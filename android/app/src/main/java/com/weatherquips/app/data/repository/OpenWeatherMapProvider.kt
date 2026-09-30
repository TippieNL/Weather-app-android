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

        // OWM stamps everything in UTC; the location's offset turns that into
        // its own clock, so "14:00" and "tomorrow" mean what they do there.
        val offset = ZoneOffset.ofTotalSeconds(current.timezone ?: 0)
        val hourlyForecast = forecast.list.take(8).map { it.toHourly(offset) }

        // Group the 3-hourly entries per local day, then drop today, as on the web.
        val byDate = forecast.list.groupBy { entry ->
            Instant.ofEpochSecond(entry.dt).atOffset(offset).toLocalDate().toString()
        }
        val dailyForecast = byDate.entries.drop(1).take(6).mapIndexed { index, (date, entries) ->
            DailyForecast(
                day = ProviderSupport.dayLabel(index, date),
                date = date,
                temperatureMax = entries.maxOf { it.main.temp },
                temperatureMin = entries.minOf { it.main.temp },
                precipitationMm = entries.sumOf { it.precipitationMm() },
                precipitationChance = entries.maxOf { it.chancePercent() },
                // OWM has no hours-of-rain figure and its snow is water
                // equivalent, not depth; neither is invented here.
                hours = entries.map { it.toHourly(offset) },
                hourStep = OWM_SLOT_HOURS.toInt(),
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
            utcOffsetSeconds = current.timezone,
        )
    }

    private fun OwmForecastEntry.precipitationMm(): Double =
        (rain?.threeHours ?: 0.0) + (snow?.threeHours ?: 0.0)

    private fun OwmForecastEntry.chancePercent(): Int = pop?.let { (it * 100).roundToInt() } ?: 0

    private fun OwmForecastEntry.toHourly(offset: ZoneOffset) = HourlyForecast(
        time = "%02d:00".format(Instant.ofEpochSecond(dt).atOffset(offset).hour),
        temperature = main.temp,
        precipitationChance = chancePercent(),
        // OWM reports a volume per three-hour slot; the app wants per hour.
        precipitationMm = precipitationMm() / OWM_SLOT_HOURS,
    )

    private companion object {
        const val OWM_SLOT_HOURS = 3.0
    }
}
