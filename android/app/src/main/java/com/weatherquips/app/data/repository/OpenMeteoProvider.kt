package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.OpenMeteoApi
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCodeMapper
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.model.isDayFromHour
import java.time.LocalTime
import kotlin.math.roundToInt

/** Default provider: free, no API key, and the reference for the data model. */
class OpenMeteoProvider(private val api: OpenMeteoApi) : WeatherProvider {

    override val service = WeatherService.OPEN_METEO

    override suspend fun fetch(
        coordinates: Coordinates,
        apiKey: String,
        locationName: String,
    ): WeatherData {
        val response = api.forecast(coordinates.latitude, coordinates.longitude)
        val current = response.current

        // Open-Meteo computes is_day from the sun's position with timezone=auto,
        // so it is authoritative. Fall back to the local hour when absent.
        val isDay = when (current.isDay) {
            1 -> true
            0 -> false
            else -> {
                val hour = current.time?.takeIf { it.length >= 13 }
                    ?.substring(11, 13)?.toIntOrNull()
                hour?.let { isDayFromHour(it) } ?: true
            }
        }

        val windSpeed = current.windSpeed
        val condition = WeatherCodeMapper.applyWindOverride(
            WeatherCodeMapper.map(current.weatherCode, current.temperature),
            windSpeed,
        )

        val hourly = response.hourly
        val startIndex = ProviderSupport.firstUpcomingHourIndex(
            times = hourly.time,
            nowLocal = current.time,
            deviceHour = LocalTime.now().hour,
        )
        val hourlyForecast = hourly.time.indices
            .drop(startIndex)
            .take(24)
            .mapNotNull { i ->
                val temperature = hourly.temperature.getOrNull(i) ?: return@mapNotNull null
                HourlyForecast(
                    time = ProviderSupport.hourLabel(hourly.time[i]),
                    temperature = temperature,
                    precipitationChance = hourly.precipitationProbability.getOrNull(i) ?: 0,
                )
            }

        val daily = response.daily
        val dailyForecast = daily.time.indices
            .drop(1)
            .mapNotNull { i ->
                val max = daily.temperatureMax.getOrNull(i) ?: return@mapNotNull null
                val min = daily.temperatureMin.getOrNull(i) ?: return@mapNotNull null
                DailyForecast(
                    day = ProviderSupport.dayLabel(i - 1, daily.time[i]),
                    date = daily.time[i],
                    temperatureMax = max,
                    temperatureMin = min,
                )
            }

        return WeatherData(
            condition = condition,
            isDay = isDay,
            temperature = current.temperature,
            description = condition.id,
            location = locationName,
            funnyQuote = "",
            subtitle = "",
            feelsLike = current.apparentTemperature,
            temperatureMax = daily.temperatureMax.firstOrNull() ?: current.temperature,
            temperatureMin = daily.temperatureMin.firstOrNull() ?: current.temperature,
            humidity = current.relativeHumidity,
            precipitationChance = daily.precipitationProbabilityMax.firstOrNull() ?: 0,
            windSpeed = windSpeed,
            uvIndex = ((current.uvIndex ?: 0.0) * 10).roundToInt() / 10.0,
            pressure = (current.surfacePressure ?: 0.0).roundToInt(),
            dailyForecast = dailyForecast,
            hourlyForecast = hourlyForecast,
        )
    }
}
