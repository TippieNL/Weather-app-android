package com.weatherquips.app.ui.home

import androidx.annotation.StringRes
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.repository.WeatherError

/** User-facing error copy. Raw exceptions never reach the UI. */
enum class UiError(@StringRes val messageRes: Int) {
    NO_INTERNET(R.string.error_no_internet),
    TIMEOUT(R.string.error_timeout),
    SERVER(R.string.error_server),
    RATE_LIMITED(R.string.error_rate_limited),
    INVALID_API_KEY(R.string.error_api_key_invalid),
    MISSING_API_KEY(R.string.error_api_key_missing),
    NO_MANUAL_LOCATION(R.string.error_no_manual_location),
    UNKNOWN(R.string.error_unknown);

    companion object {
        fun from(error: Throwable): UiError = when (error) {
            is WeatherError.NoInternet -> NO_INTERNET
            is WeatherError.Timeout -> TIMEOUT
            is WeatherError.RateLimited -> RATE_LIMITED
            is WeatherError.InvalidApiKey -> INVALID_API_KEY
            is WeatherError.MissingApiKey -> MISSING_API_KEY
            is WeatherError.Server, is WeatherError.MalformedResponse -> SERVER
            else -> UNKNOWN
        }
    }
}

/** Why device location could not be used. */
enum class LocationIssue(@StringRes val messageRes: Int) {
    DISABLED(R.string.error_location_disabled),
    UNAVAILABLE(R.string.error_location_unavailable),
}

/**
 * Everything the home screen needs. Splitting the phases into a sealed type
 * makes the impossible combinations (weather + "permission required", say)
 * unrepresentable.
 */
sealed interface HomePhase {
    data object Loading : HomePhase

    /** Location permission has not been granted yet. */
    data class PermissionRequired(val deniedOnce: Boolean) : HomePhase

    data class LocationUnavailable(val issue: LocationIssue) : HomePhase

    data class Error(val error: UiError) : HomePhase

    /** Fresh data from the network. */
    data class Success(val weather: WeatherData, val coordinates: Coordinates) : HomePhase

    /**
     * Cached data shown because the network is unreachable. The UI labels it so
     * nobody mistakes yesterday's forecast for today's.
     */
    data class Offline(
        val weather: WeatherData,
        val coordinates: Coordinates,
        val fetchedAtMillis: Long,
        val error: UiError,
    ) : HomePhase
}

data class HomeUiState(
    val phase: HomePhase = HomePhase.Loading,
    val isRefreshing: Boolean = false,
    val settings: AppSettings = AppSettings(),
    /** Pokémon-mode quote, recomputed only when the condition changes. */
    val pokemonQuote: String = "",
    val pokemonSubtitle: String = "",
    val pokemonSilhouette: PokemonQuips.Silhouette = PokemonQuips.Silhouette.SPARK,
) {
    val isPokemonMode: Boolean get() = settings.isPokemonModeActive

    val weather: WeatherData?
        get() = when (phase) {
            is HomePhase.Success -> phase.weather
            is HomePhase.Offline -> phase.weather
            else -> null
        }

    val coordinates: Coordinates?
        get() = when (phase) {
            is HomePhase.Success -> phase.coordinates
            is HomePhase.Offline -> phase.coordinates
            else -> null
        }

    /** The quote actually shown, honouring the Easter egg. */
    val displayQuote: String
        get() = if (isPokemonMode) pokemonQuote else weather?.funnyQuote.orEmpty()

    val displaySubtitle: String
        get() = if (isPokemonMode) pokemonSubtitle else weather?.subtitle.orEmpty()
}
