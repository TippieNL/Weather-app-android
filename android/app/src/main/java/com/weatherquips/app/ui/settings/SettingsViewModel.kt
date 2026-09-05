package com.weatherquips.app.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weatherquips.app.AppContainer
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationAlerts
import com.weatherquips.app.notifications.PrecipitationScheduler
import com.weatherquips.app.ui.home.requireContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Result of the manual-location search, kept out of the settings model itself. */
sealed interface GeocodeState {
    data object Idle : GeocodeState
    data object Loading : GeocodeState
    data class Message(@StringRes val messageRes: Int, val argument: String? = null) : GeocodeState
    /** More than one match — the user picks which city they meant. */
    data class Choices(val results: List<GeocodeResult>) : GeocodeState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val geocodeState: GeocodeState = GeocodeState.Idle,
    val notificationPermissionBlocked: Boolean = false,
)

/**
 * What the settings screen can do. The screen depends on this rather than on the
 * ViewModel itself, which keeps it a plain stateless composable.
 */
interface SettingsActions {
    fun setTemperatureUnit(unit: TemperatureUnit)
    fun setTimeFormat(format: TimeFormat)
    fun setDateFormat(format: DateFormat)
    fun setLocationMode(mode: LocationMode)
    fun setManualLocation(value: String)
    fun setWeatherService(service: WeatherService)
    fun setApiKey(value: String)
    fun exitPokemonMode()
    fun findLocation()
    fun selectResult(result: GeocodeResult)
    fun setNotificationsEnabled(enabled: Boolean, granted: Boolean)
    fun sendTestNotification(): Boolean
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geocodingRepository: GeocodingRepository,
    private val notificationHelper: NotificationHelper,
    private val precipitationScheduler: PrecipitationScheduler,
) : ViewModel(), SettingsActions {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var geocodeJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    override fun setTemperatureUnit(unit: TemperatureUnit) = edit { it.copy(temperatureUnit = unit) }

    override fun setTimeFormat(format: TimeFormat) = edit { it.copy(timeFormat = format) }

    override fun setDateFormat(format: DateFormat) = edit { it.copy(dateFormat = format) }

    override fun setLocationMode(mode: LocationMode) = edit { it.copy(locationMode = mode) }

    override fun setManualLocation(value: String) {
        _uiState.update { it.copy(geocodeState = GeocodeState.Idle) }
        edit { it.copy(manualLocation = value) }
    }

    override fun setWeatherService(service: WeatherService) = edit { it.copy(weatherService = service) }

    override fun setApiKey(value: String) = edit { it.copy(weatherApiKey = value.trim()) }

    /** Turns the Easter egg off but keeps whatever city the weather is showing. */
    override fun exitPokemonMode() = edit { it.copy(pokemonMode = false, manualLocation = "") }

    override fun findLocation() {
        val query = _uiState.value.settings.manualLocation.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(geocodeState = GeocodeState.Message(R.string.geocode_empty)) }
            return
        }

        // Secret Pallet Town Easter egg: fictional, so it bypasses geocoding and
        // keeps the currently selected city's weather when there is one.
        if (PokemonQuips.isPalletTown(query)) {
            viewModelScope.launch {
                settingsRepository.update { current ->
                    current.copy(
                        manualCoords = current.manualCoords ?: PokemonQuips.PALLET_TOWN_COORDS,
                        pokemonMode = true,
                    )
                }
                _uiState.update {
                    it.copy(
                        geocodeState = GeocodeState.Message(
                            R.string.geocode_found,
                            PokemonQuips.PALLET_TOWN_LABEL,
                        ),
                    )
                }
            }
            return
        }

        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            _uiState.update { it.copy(geocodeState = GeocodeState.Loading) }
            try {
                val results = geocodingRepository.search(query)
                when {
                    results.isEmpty() -> _uiState.update {
                        it.copy(geocodeState = GeocodeState.Message(R.string.geocode_not_found))
                    }
                    results.size == 1 -> selectResult(results.first())
                    else -> _uiState.update { it.copy(geocodeState = GeocodeState.Choices(results)) }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = if (error is IOException) {
                    R.string.geocode_offline
                } else {
                    R.string.geocode_failed
                }
                _uiState.update { it.copy(geocodeState = GeocodeState.Message(message)) }
            }
        }
    }

    override fun selectResult(result: GeocodeResult) {
        viewModelScope.launch {
            settingsRepository.update { current ->
                current.copy(
                    manualLocation = result.name,
                    manualCoords = Coordinates(result.latitude, result.longitude),
                    pokemonMode = false,
                )
            }
            _uiState.update {
                it.copy(geocodeState = GeocodeState.Message(R.string.geocode_found, result.name))
            }
        }
    }

    /**
     * @param granted whether POST_NOTIFICATIONS is (now) granted. The caller
     *                requests the runtime permission before enabling.
     */
    override fun setNotificationsEnabled(enabled: Boolean, granted: Boolean) {
        if (enabled && !granted) {
            _uiState.update { it.copy(notificationPermissionBlocked = true) }
            edit { it.copy(notificationsEnabled = false) }
            precipitationScheduler.setEnabled(false)
            return
        }
        _uiState.update { it.copy(notificationPermissionBlocked = false) }
        edit { it.copy(notificationsEnabled = enabled) }
        precipitationScheduler.setEnabled(enabled)
    }

    /** Posts the sample alert from the settings screen. */
    override fun sendTestNotification(): Boolean =
        notificationHelper.notifyPrecipitation(PrecipitationAlerts.sample())

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    companion object {
        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                SettingsViewModel(
                    settingsRepository = resolved.settingsRepository,
                    geocodingRepository = resolved.geocodingRepository,
                    notificationHelper = resolved.notificationHelper,
                    precipitationScheduler = resolved.precipitationScheduler,
                )
            }
        }
    }
}
