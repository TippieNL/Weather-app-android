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
    /** How much is expected to fall during the hour, in millimetres. */
    val precipitationMm: Double = 0.0,
)

/**
 * One sample of the short-term precipitation nowcast.
 *
 * The hourly forecast answers "will it rain this afternoon"; this answers
 * "should I leave now", which is what the widget's graph is for. Intensity
 * rather than probability, because a 90% chance of drizzle and a 90% chance
 * of a downpour are not the same errand.
 */
@Serializable
data class NowcastPoint(
    /** "HH:mm" in the location's local time. */
    val time: String,
    /** Offset from the moment the forecast was made; negative is the recent past. */
    val minutesFromNow: Int,
    /** Rate in millimetres per hour, not millimetres per bucket. */
    val millimetresPerHour: Double,
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
    /**
     * The location's offset from UTC. Sources that stamp their data in UTC —
     * the radar nowcast — need it to label a point with the clock time at the
     * place being forecast, which is not necessarily the phone's.
     */
    val utcOffsetSeconds: Int? = null,
    /**
     * Fine-grained precipitation for the next couple of hours. Empty when the
     * chosen provider has nothing better than hourly totals to offer.
     */
    val nowcast: List<NowcastPoint> = emptyList(),
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
