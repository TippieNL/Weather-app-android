package com.weatherquips.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weatherquips.app.AppContainer
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.domain.repository.WeatherRepository
import com.weatherquips.app.location.DeviceLocationSource
import com.weatherquips.app.location.LocationResult
import com.weatherquips.app.notifications.AlertThrottle
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationAlerts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the home screen's state, so it survives rotation and — because the phase
 * lives here rather than in the Activity — a configuration change never drops
 * the loaded weather.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class HomeViewModel(
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: DeviceLocationSource,
    private val notificationHelper: NotificationHelper,
    private val alertThrottle: AlertThrottle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var permissionDeniedOnce = false

    /** Inputs that should trigger a reload; unrelated settings edits must not. */
    private data class WeatherInputs(
        val locationMode: LocationMode,
        val manualCoords: Coordinates?,
        val service: com.weatherquips.app.domain.model.WeatherService,
        val apiKey: String,
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { WeatherInputs(it.locationMode, it.manualCoords, it.weatherService, it.weatherApiKey) }
                .distinctUntilChanged()
                // An API key is typed one character at a time; without this the
                // app would fire (and fail) a request per keystroke.
                .debounce(INPUT_DEBOUNCE_MILLIS)
                .collect { load(showLoading = _uiState.value.weather == null) }
        }
    }

    /** Pull-to-refresh / the refresh button. Always fetches a new quote too. */
    fun refresh() = load(showLoading = false, isRefresh = true)

    /** Retry from an error state, or after the user granted permission. */
    fun retry() = load(showLoading = true)

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            permissionDeniedOnce = false
            load(showLoading = true)
        } else {
            permissionDeniedOnce = true
            _uiState.update {
                it.copy(
                    phase = HomePhase.PermissionRequired(deniedOnce = true),
                    isRefreshing = false,
                )
            }
        }
    }

    /** Called when the screen becomes visible again (permission may have changed). */
    fun onResumed() {
        val phase = _uiState.value.phase
        val needsLocation = phase is HomePhase.PermissionRequired || phase is HomePhase.LocationUnavailable
        if (needsLocation && locationProvider.hasPermission() && locationProvider.isLocationEnabled()) {
            load(showLoading = true)
        }
    }

    private fun load(showLoading: Boolean, isRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val settings = settingsRepository.current()
            _uiState.update {
                it.copy(
                    settings = settings,
                    isRefreshing = isRefresh,
                    phase = if (showLoading) HomePhase.Loading else it.phase,
                )
            }

            val coordinates = when (val resolved = resolveCoordinates(settings)) {
                is ResolvedLocation.Failure -> {
                    _uiState.update { it.copy(phase = resolved.phase, isRefreshing = false) }
                    return@launch
                }
                is ResolvedLocation.Success -> resolved.coordinates
            }

            try {
                val weather = weatherRepository.getWeather(
                    coordinates = coordinates,
                    service = settings.weatherService,
                    apiKey = settings.weatherApiKey,
                )
                _uiState.update { current ->
                    current
                        .copy(
                            phase = HomePhase.Success(weather, coordinates),
                            isRefreshing = false,
                        )
                        .withPokemonQuote(weather.condition, settings)
                }
                maybeNotifyPrecipitation(settings, weather)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val uiError = UiError.from(error)
                // Offline-first: fall back to the last successful result rather
                // than showing an empty error screen.
                val cached = weatherRepository.getCachedWeather()
                _uiState.update { current ->
                    val phase = if (cached != null) {
                        HomePhase.Offline(
                            weather = cached.data,
                            coordinates = cached.coordinates,
                            fetchedAtMillis = cached.fetchedAtEpochMillis,
                            error = uiError,
                        )
                    } else {
                        HomePhase.Error(uiError)
                    }
                    current
                        .copy(phase = phase, isRefreshing = false)
                        .withPokemonQuote(cached?.data?.condition, settings)
                }
            }
        }
    }

    private sealed interface ResolvedLocation {
        data class Success(val coordinates: Coordinates) : ResolvedLocation
        data class Failure(val phase: HomePhase) : ResolvedLocation
    }

    private suspend fun resolveCoordinates(settings: AppSettings): ResolvedLocation {
        if (settings.locationMode == LocationMode.MANUAL) {
            val coords = settings.manualCoords
                ?: return ResolvedLocation.Failure(HomePhase.Error(UiError.NO_MANUAL_LOCATION))
            return ResolvedLocation.Success(coords)
        }

        return when (val result = locationProvider.currentLocation()) {
            is LocationResult.Success -> ResolvedLocation.Success(result.coordinates)
            LocationResult.PermissionDenied ->
                ResolvedLocation.Failure(HomePhase.PermissionRequired(permissionDeniedOnce))
            LocationResult.LocationDisabled ->
                ResolvedLocation.Failure(HomePhase.LocationUnavailable(LocationIssue.DISABLED))
            LocationResult.Unavailable ->
                ResolvedLocation.Failure(HomePhase.LocationUnavailable(LocationIssue.UNAVAILABLE))
        }
    }

    /**
     * Mirrors the web app's notification effect: fire when the forecast turns
     * wet, at most once every 30 minutes, and never without permission.
     */
    private suspend fun maybeNotifyPrecipitation(
        settings: AppSettings,
        weather: com.weatherquips.app.domain.model.WeatherData,
    ) {
        if (!settings.notificationsEnabled) return
        if (!notificationHelper.hasPermission()) return
        if (!PrecipitationAlerts.shouldNotify(weather)) return
        if (!alertThrottle.shouldSend()) return

        if (notificationHelper.notifyPrecipitation(PrecipitationAlerts.build(weather))) {
            alertThrottle.markSent()
        }
    }

    /** Themed quote + silhouette stay stable until the condition changes. */
    private fun HomeUiState.withPokemonQuote(
        condition: WeatherCondition?,
        settings: AppSettings,
    ): HomeUiState {
        if (condition == null || !settings.isPokemonModeActive) return this
        if (pokemonQuote.isNotEmpty() && pokemonQuoteCondition == condition) return this
        val quip = PokemonQuips.quote(condition)
        return copy(
            pokemonQuote = quip.quote,
            pokemonSubtitle = quip.subtitle,
            pokemonSilhouette = PokemonQuips.randomSilhouette(),
            pokemonQuoteCondition = condition,
        )
    }

    companion object {
        private const val INPUT_DEBOUNCE_MILLIS = 350L

        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                HomeViewModel(
                    weatherRepository = resolved.weatherRepository,
                    settingsRepository = resolved.settingsRepository,
                    locationProvider = resolved.locationProvider,
                    notificationHelper = resolved.notificationHelper,
                    alertThrottle = resolved.alertThrottle,
                )
            }
        }
    }
}

internal fun CreationExtras.requireContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WeatherQuipsApplication).container
