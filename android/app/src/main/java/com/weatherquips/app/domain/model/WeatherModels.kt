package com.weatherquips.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Normalized weather conditions. Ported 1:1 from `shared/schema.ts` in the web
 * app so the quote map, icon map and API mappings all keep the same vocabulary.
 */
@Serializable
enum class WeatherCondition {
    @SerialName("clear") CLEAR,
    @SerialName("cloudy") CLOUDY,
    @SerialName("rainy") RAINY,
    @SerialName("stormy") STORMY,
    @SerialName("snowy") SNOWY,
    @SerialName("foggy") FOGGY,
    @SerialName("windy") WINDY,
    @SerialName("hot") HOT,
    @SerialName("cold") COLD;

    /** Lowercase wire/display name, matching the web app's condition strings. */
    val id: String get() = name.lowercase()

    companion object {
        fun fromId(value: String): WeatherCondition? =
            entries.firstOrNull { it.id == value.lowercase() }
    }
}

@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class HourlyForecast(
    /** "HH:00" in the location's local time, exactly like the web API. */
    val time: String,
    val temperature: Double,
    val precipitationChance: Int = 0,
)

@Serializable
data class DailyForecast(
    /** "tomorrow" for the first entry, otherwise the lowercase weekday name. */
    val day: String,
    /** ISO date, "yyyy-MM-dd". */
    val date: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
)

/**
 * The app's single weather model. Mirrors `weatherDataSchema` from the web app;
 * temperatures are always Celsius here and converted for display only.
 */
@Serializable
data class WeatherData(
    val condition: WeatherCondition,
    val isDay: Boolean,
    val temperature: Double,
    val description: String,
    val location: String,
    val funnyQuote: String,
    val subtitle: String,
    val feelsLike: Double,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val humidity: Int,
    val precipitationChance: Int,
    val windSpeed: Double,
    val uvIndex: Double,
    val pressure: Int,
    val dailyForecast: List<DailyForecast>,
    val hourlyForecast: List<HourlyForecast>,
)

/** A weather payload plus the moment it was fetched, used for offline display. */
@Serializable
data class CachedWeather(
    val data: WeatherData,
    val fetchedAtEpochMillis: Long,
    val coordinates: Coordinates,
)

@Serializable
data class GeocodeResult(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)
