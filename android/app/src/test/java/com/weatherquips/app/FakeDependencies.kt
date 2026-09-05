package com.weatherquips.app

import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Shared fixtures for the ViewModel and UI tests. */
object TestWeather {

    fun sample(
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        quote: String = "Clouds rolled in like they **own** the damn place",
        subtitle: String = "Clouds everywhere. No escape.",
        temperature: Double = 15.0,
        location: String = "Assen",
    ) = WeatherData(
        condition = condition,
        isDay = false,
        temperature = temperature,
        description = condition.id,
        location = location,
        funnyQuote = quote,
        subtitle = subtitle,
        feelsLike = 14.0,
        temperatureMax = 18.0,
        temperatureMin = 14.0,
        humidity = 74,
        precipitationChance = 35,
        windSpeed = 11.0,
        uvIndex = 0.0,
        pressure = 1022,
        dailyForecast = listOf(
            DailyForecast("tomorrow", "2026-09-06", 21.0, 9.0),
            DailyForecast("monday", "2026-09-07", 28.0, 16.0),
        ),
        hourlyForecast = listOf(
            HourlyForecast("18:00", 18.0, 10),
            HourlyForecast("19:00", 17.0, 10),
            HourlyForecast("20:00", 17.0, 5),
        ),
    )
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}

class FakeWeatherRepository(
    var result: Result<WeatherData> = Result.success(TestWeather.sample()),
    var cached: CachedWeather? = null,
) : WeatherRepository {

    var callCount = 0
        private set

    override suspend fun getWeather(
        coordinates: Coordinates,
        service: WeatherService,
        apiKey: String,
    ): WeatherData {
        callCount++
        return result.getOrThrow()
    }

    override suspend fun getCachedWeather(): CachedWeather? = cached

    override fun cachedWeather(): Flow<CachedWeather?> = MutableStateFlow(cached)
}
