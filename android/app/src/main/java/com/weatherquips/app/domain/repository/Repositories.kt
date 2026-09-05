package com.weatherquips.app.domain.repository

import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import kotlinx.coroutines.flow.Flow

/** Typed failures so the UI never has to look at an exception. */
sealed class WeatherError : Exception() {
    data object NoInternet : WeatherError()
    data object Timeout : WeatherError()
    data object RateLimited : WeatherError()
    data object InvalidApiKey : WeatherError()
    data object MissingApiKey : WeatherError()
    data class Server(val code: Int) : WeatherError()
    data object MalformedResponse : WeatherError()
    data object Unknown : WeatherError()
}

interface WeatherRepository {
    /**
     * Fetches current + hourly + daily weather for [coordinates] from [service].
     * A fresh funny quote is attached on every call, matching the web app.
     */
    suspend fun getWeather(
        coordinates: Coordinates,
        service: WeatherService,
        apiKey: String,
    ): WeatherData

    /** Last successful result, or null when nothing has been cached yet. */
    suspend fun getCachedWeather(): CachedWeather?

    fun cachedWeather(): Flow<CachedWeather?>
}

interface GeocodingRepository {
    /** Forward geocoding for the manual-location search. */
    suspend fun search(query: String): List<GeocodeResult>

    /** Reverse geocoding for the displayed place name; never throws. */
    suspend fun reverseGeocode(coordinates: Coordinates): String
}

/** One RainViewer radar frame: a tile path template and its timestamp. */
data class RadarFrame(val path: String, val timeEpochSeconds: Long)

data class RadarTimeline(val frames: List<RadarFrame>, val pastCount: Int)

interface RadarRepository {
    suspend fun loadTimeline(): RadarTimeline

    /** Full tile URL template for a frame, with {z}/{x}/{y} placeholders. */
    fun tileUrlTemplate(frame: RadarFrame): String
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
