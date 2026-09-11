package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.OpenWeatherMapApi
import com.weatherquips.app.data.model.OwmForecastEntry
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCodeMapper
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.model.isDayFromHour
import com.weatherquips.app.domain.repository.WeatherError
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * OpenWeatherMap, ported from `fetchOpenWeatherMap()`. The free plan has no UV
 * in the current-weather endpoint, so UV stays 0 exactly like the web app.
 */
class OpenWeatherMapProvider(private val api: OpenWeatherMapApi) : WeatherProvider {

    override val service = WeatherService.OPEN_WEATHER_MAP

    override suspend fun fetch(
        coordinates: Coordinates,
        apiKey: String,
        locationName: String,
    ): WeatherData {
        if (apiKey.isBlank()) throw WeatherError.MissingApiKey

        val current = api.current(coordinates.latitude, coordinates.longitude, apiKey)
        val forecast = api.forecast(coordinates.latitude, coordinates.longitude, apiKey)

        val hourlyForecast = forecast.list.take(8).map { entry ->
            HourlyForecast(
                time = utcHourLabel(entry.dt),
                temperature = entry.main.temp,
                precipitationChance = entry.pop?.let { (it * 100).roundToInt() } ?: 0,
                // OWM reports a volume per three-hour slot; the app wants per hour.
                precipitationMm = entry.precipitationMm() / OWM_SLOT_HOURS,
            )
        }

        // Group the 3-hourly entries per UTC day, then drop today, as on the web.
        val byDate = LinkedHashMap<String, MutableList<Double>>()
        forecast.list.forEach { entry ->
            val date = Instant.ofEpochSecond(entry.dt).atOffset(ZoneOffset.UTC).toLocalDate().toString()
            byDate.getOrPut(date) { mutableListOf() }.add(entry.main.temp)
        }
        val dailyForecast = byDate.entries.drop(1).take(6).mapIndexed { index, (date, temps) ->
            DailyForecast(
                day = ProviderSupport.dayLabel(index, date),
                date = date,
                temperatureMax = temps.max(),
                temperatureMin = temps.min(),
            )
        }

        val owmId = current.weather.firstOrNull()?.id ?: 800
        val weatherCode = WeatherCodeMapper.fromOpenWeatherMapId(owmId)
        val windSpeedKmh = current.wind.speed * 3.6
        val condition = WeatherCodeMapper.applyWindOverride(
            WeatherCodeMapper.map(weatherCode, current.main.temp),
            windSpeedKmh,
        )

        // Day/night from the icon suffix ('d'/'n'), with sunrise/sunset fallback.
        val icon = current.weather.firstOrNull()?.icon.orEmpty()
        val isDay = when {
            icon.endsWith("d") -> true
            icon.endsWith("n") -> false
            current.sys?.sunrise != null && current.sys.sunset != null && current.dt != null ->
                current.dt >= current.sys.sunrise && current.dt < current.sys.sunset
            else -> {
                val seconds = (current.dt ?: (System.currentTimeMillis() / 1000)) + (current.timezone ?: 0)
                isDayFromHour(((seconds % 86_400) / 3600).toInt())
            }
        }

        return WeatherData(
            condition = condition,
            isDay = isDay,
            temperature = current.main.temp,
            description = condition.id,
            location = locationName,
            funnyQuote = "",
            subtitle = "",
            feelsLike = current.main.feelsLike,
            temperatureMax = current.main.tempMax,
            temperatureMin = current.main.tempMin,
            humidity = current.main.humidity,
            precipitationChance = forecast.list.firstOrNull()?.pop
                ?.let { (it * 100).roundToInt() } ?: 0,
            windSpeed = windSpeedKmh,
            uvIndex = 0.0,
            pressure = current.main.pressure,
            dailyForecast = dailyForecast,
            hourlyForecast = hourlyForecast,
            // No sub-hourly feed on the free plan, so the widget's graph gets
            // a step per slot rather than a curve.
            nowcast = ProviderSupport.nowcastFromHourly(hourlyForecast),
        )
    }

    private fun OwmForecastEntry.precipitationMm(): Double =
        (rain?.threeHours ?: 0.0) + (snow?.threeHours ?: 0.0)

    private companion object {
        const val OWM_SLOT_HOURS = 3.0
    }

    private fun utcHourLabel(epochSeconds: Long): String {
        val hour = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).hour
        return "%02d:00".format(hour)
    }
}
