package com.weatherquips.app.domain.model

/** Persisted preferences. Mirrors `Settings` from `client/src/contexts/settings.tsx`. */
data class AppSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val dateFormat: DateFormat = DateFormat.DD_MM,
    val locationMode: LocationMode = LocationMode.DEVICE,
    val manualLocation: String = "",
    val manualCoords: Coordinates? = null,
    val weatherService: WeatherService = WeatherService.OPEN_METEO,
    val weatherApiKey: String = "",
    val notificationsEnabled: Boolean = false,
    val pokemonMode: Boolean = false,
    /** False until the first-run introduction has been seen. */
    val onboardingCompleted: Boolean = false,
) {
    /** The Easter egg only runs while a manual location is selected, as on the web. */
    val isPokemonModeActive: Boolean
        get() = pokemonMode && locationMode == LocationMode.MANUAL
}

enum class TemperatureUnit(val id: String) {
    CELSIUS("celsius"),
    FAHRENHEIT("fahrenheit");

    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: CELSIUS
    }
}

enum class TimeFormat(val id: String) {
    H12("12h"),
    H24("24h");

    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: H24
    }
}

enum class DateFormat(val id: String) {
    DD_MM("DD/MM"),
    MM_DD("MM/DD"),
    ISO("YYYY-MM-DD");

    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: DD_MM
    }
}

enum class LocationMode(val id: String) {
    DEVICE("device"),
    MANUAL("manual");

    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: DEVICE
    }
}

enum class WeatherService(val id: String, val requiresApiKey: Boolean) {
    OPEN_METEO("openmeteo", requiresApiKey = false),
    OPEN_WEATHER_MAP("openweathermap", requiresApiKey = true),
    WEATHER_API("weatherapi", requiresApiKey = true);

    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: OPEN_METEO
    }
}
