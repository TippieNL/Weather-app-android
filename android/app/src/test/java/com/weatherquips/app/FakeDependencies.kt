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

    /** The exact reading from the reported screenshot, for visual comparison. */
    fun assenEvening() = WeatherData(
        condition = WeatherCondition.CLOUDY,
        isDay = true,
        temperature = 17.0,
        description = "cloudy",
        location = "Assen",
        funnyQuote = "Clouds rolled in like they **own** the damn place",
        subtitle = "Clouds everywhere. No escape.",
        feelsLike = 18.0,
        temperatureMax = 17.0,
        temperatureMin = 12.0,
        humidity = 94,
        precipitationChance = 96,
        windSpeed = 7.0,
        uvIndex = 0.2,
        pressure = 1018,
        dailyForecast = listOf(
            DailyForecast("tomorrow", "2026-09-12", 20.0, 11.0),
            DailyForecast("sunday", "2026-09-13", 19.0, 15.0),
            DailyForecast("monday", "2026-09-14", 20.0, 10.0),
            DailyForecast("tuesday", "2026-09-15", 22.0, 15.0),
            DailyForecast("wednesday", "2026-09-16", 21.0, 13.0),
            DailyForecast("thursday", "2026-09-17", 18.0, 12.0),
        ),
        hourlyForecast = listOf(
            HourlyForecast("19:00", 17.0, 96),
            HourlyForecast("20:00", 17.0, 80),
            HourlyForecast("21:00", 17.0, 62),
            HourlyForecast("22:00", 17.0, 40),
            HourlyForecast("23:00", 16.0, 25),
            HourlyForecast("00:00", 16.0, 10),
            HourlyForecast("01:00", 16.0, 5),
            HourlyForecast("02:00", 15.0, 0),
            HourlyForecast("03:00", 15.0, 0),
            HourlyForecast("04:00", 14.0, 0),
            HourlyForecast("05:00", 14.0, 10),
            HourlyForecast("06:00", 14.0, 20),
        ),
    )

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
