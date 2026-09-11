package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.WeatherApiApi
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCodeMapper
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.model.isDayFromHour
import com.weatherquips.app.domain.repository.WeatherError
import java.time.LocalTime
import kotlin.math.roundToInt

/** WeatherAPI.com, ported from `fetchWeatherAPI()`. */
class WeatherApiProvider(private val api: WeatherApiApi) : WeatherProvider {

    override val service = WeatherService.WEATHER_API

    override suspend fun fetch(
        coordinates: Coordinates,
        apiKey: String,
        locationName: String,
    ): WeatherData {
        if (apiKey.isBlank()) throw WeatherError.MissingApiKey

        val response = api.forecast(apiKey, "${coordinates.latitude},${coordinates.longitude}")
        val current = response.current
        val today = response.forecast.forecastday.firstOrNull()
            ?: throw WeatherError.MalformedResponse

        val weatherCode = WeatherCodeMapper.fromWeatherApiCode(current.condition.code)
        val condition = WeatherCodeMapper.applyWindOverride(
            WeatherCodeMapper.map(weatherCode, current.tempC),
            current.windKph,
        )

        // WeatherAPI provides an authoritative is_day flag (1 = day, 0 = night).
        val isDay = when (current.isDay) {
            1 -> true
            0 -> false
            else -> {
                val hour = response.location?.localtime?.takeIf { it.length >= 13 }
                    ?.substring(11, 13)?.toIntOrNull()
                hour?.let { isDayFromHour(it) } ?: true
            }
        }

        val startIndex = ProviderSupport.firstUpcomingHourIndex(
            times = today.hour.map { it.time },
            nowLocal = response.location?.localtime?.replace(' ', 'T'),
            deviceHour = LocalTime.now().hour,
        )
        val hourlyForecast = today.hour
            .drop(startIndex)
            .take(24)
            .map { hour ->
                HourlyForecast(
                    time = ProviderSupport.hourLabel(hour.time.replace(' ', 'T')),
                    temperature = hour.tempC,
                    precipitationChance = hour.chanceOfRain ?: 0,
                    precipitationMm = hour.precipMm,
                )
            }

        val dailyForecast = response.forecast.forecastday.drop(1).mapIndexed { index, day ->
            DailyForecast(
                day = ProviderSupport.dayLabel(index, day.date),
                date = day.date,
                temperatureMax = day.day.maxTempC,
                temperatureMin = day.day.minTempC,
            )
        }

        return WeatherData(
            condition = condition,
            isDay = isDay,
            temperature = current.tempC,
            description = condition.id,
            location = locationName,
            funnyQuote = "",
            subtitle = "",
            feelsLike = current.feelsLikeC,
            temperatureMax = today.day.maxTempC,
            temperatureMin = today.day.minTempC,
            humidity = current.humidity,
            precipitationChance = today.day.dailyChanceOfRain ?: 0,
            windSpeed = current.windKph,
            uvIndex = (current.uv * 10).roundToInt() / 10.0,
            pressure = current.pressureMb.roundToInt(),
            dailyForecast = dailyForecast,
            hourlyForecast = hourlyForecast,
            // Hourly totals are the finest resolution this API offers.
            nowcast = ProviderSupport.nowcastFromHourly(hourlyForecast),
        )
    }
}
