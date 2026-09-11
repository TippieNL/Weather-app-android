package com.weatherquips.app

import com.weatherquips.app.data.api.OpenMeteoApi
import com.weatherquips.app.data.model.OpenMeteoCurrent
import com.weatherquips.app.data.model.OpenMeteoDaily
import com.weatherquips.app.data.model.OpenMeteoHourly
import com.weatherquips.app.data.model.OpenMeteoMinutely15
import com.weatherquips.app.data.model.OpenMeteoResponse
import com.weatherquips.app.data.repository.OpenMeteoProvider
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherCondition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Response → app model mapping for the default provider. */
class OpenMeteoProviderTest {

    private fun response(
        weatherCode: Int = 3,
        temperature: Double = 15.2,
        windSpeed: Double = 11.0,
        isDay: Int? = 0,
        currentTime: String = "2026-09-05T18:00",
        minutely15: OpenMeteoMinutely15? = OpenMeteoMinutely15(
            // Half an hour behind the current time, then two hours ahead.
            time = (0..9).map { "2026-09-05T%02d:%02d".format(17 + (30 + it * 15) / 60, (30 + it * 15) % 60) },
            precipitation = listOf(0.0, 0.0, 0.0, 0.1, 0.25, 0.5, 0.3, 0.0, 0.0, 0.0),
        ),
    ) = OpenMeteoResponse(
        current = OpenMeteoCurrent(
            time = currentTime,
            temperature = temperature,
            weatherCode = weatherCode,
            windSpeed = windSpeed,
            apparentTemperature = 14.1,
            relativeHumidity = 74,
            surfacePressure = 1021.6,
            uvIndex = 0.04,
            isDay = isDay,
        ),
        hourly = OpenMeteoHourly(
            time = (0..23).map { "2026-09-05T%02d:00".format(it) },
            temperature = (0..23).map { 10.0 + it },
            precipitationProbability = (0..23).map { it },
            precipitation = (0..23).map { it * 0.1 },
        ),
        daily = OpenMeteoDaily(
            time = listOf(
                "2026-09-05", "2026-09-06", "2026-09-07", "2026-09-08",
                "2026-09-09", "2026-09-10", "2026-09-11",
            ),
            temperatureMax = listOf(18.0, 21.0, 28.0, 24.0, 22.0, 20.0, 19.0),
            temperatureMin = listOf(14.0, 9.0, 16.0, 15.0, 13.0, 12.0, 11.0),
            precipitationProbabilityMax = listOf(35, 10, 0, 5, 20, 40, 60),
            uvIndexMax = listOf(4.0, 5.0, 6.0, 5.0, 4.0, 3.0, 2.0),
            weatherCode = listOf(3, 1, 0, 2, 61, 80, 95),
        ),
        minutely15 = minutely15,
    )

    private class FakeApi(private val response: OpenMeteoResponse) : OpenMeteoApi {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            current: String,
            daily: String,
            hourly: String,
            minutely15: String,
            timezone: String,
            forecastDays: Int,
            forecastMinutely15: Int,
            pastMinutely15: Int,
        ): OpenMeteoResponse = response
    }

    private val coordinates = Coordinates(52.99, 6.56)

    @Test
    fun `current conditions map into the app model`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response())).fetch(coordinates, "", "Assen")

        assertEquals(WeatherCondition.CLOUDY, weather.condition)
        assertEquals("cloudy", weather.description)
        assertEquals("Assen", weather.location)
        assertEquals(15.2, weather.temperature, 0.001)
        assertEquals(14.1, weather.feelsLike, 0.001)
        assertEquals(74, weather.humidity)
        assertEquals(1022, weather.pressure)
        assertEquals(11.0, weather.windSpeed, 0.001)
        assertEquals(18.0, weather.temperatureMax, 0.001)
        assertEquals(14.0, weather.temperatureMin, 0.001)
        assertEquals(35, weather.precipitationChance)
        assertEquals(false, weather.isDay)
    }

    @Test
    fun `uv index is rounded to one decimal like the web api`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response())).fetch(coordinates, "", "Assen")
        assertEquals(0.0, weather.uvIndex, 0.001)
    }

    @Test
    fun `hourly starts at the current local hour of the location`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response(currentTime = "2026-09-05T18:00")))
            .fetch(coordinates, "", "Assen")

        assertEquals("18:00", weather.hourlyForecast.first().time)
        // 18:00 through 23:00 is six entries — the array ends there.
        assertEquals(6, weather.hourlyForecast.size)
        assertEquals(28.0, weather.hourlyForecast.first().temperature, 0.001)
        assertEquals(18, weather.hourlyForecast.first().precipitationChance)
        assertEquals(1.8, weather.hourlyForecast.first().precipitationMm, 0.001)
    }

    @Test
    fun `quarter-hourly totals become a rate, anchored on the current time`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response())).fetch(coordinates, "", "Assen")

        assertEquals(10, weather.nowcast.size)
        // 17:30 is half an hour before the 18:00 reading.
        assertEquals("17:30", weather.nowcast.first().time)
        assertEquals(-30, weather.nowcast.first().minutesFromNow)
        assertEquals(105, weather.nowcast.last().minutesFromNow)
        // 0.5 mm in a quarter of an hour is 2 mm/h, which is the figure a
        // rain radar would print.
        assertEquals(2.0, weather.nowcast[5].millimetresPerHour, 0.001)
    }

    @Test
    fun `without a minutely feed the nowcast falls back to hourly millimetres`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response(minutely15 = null)))
            .fetch(coordinates, "", "Assen")

        assertEquals(4, weather.nowcast.size)
        assertEquals(0, weather.nowcast.first().minutesFromNow)
        assertEquals(60, weather.nowcast[1].minutesFromNow)
        assertEquals(1.8, weather.nowcast.first().millimetresPerHour, 0.001)
    }

    @Test
    fun `daily forecast skips today and labels tomorrow`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response())).fetch(coordinates, "", "Assen")

        assertEquals(6, weather.dailyForecast.size)
        assertEquals("tomorrow", weather.dailyForecast[0].day)
        assertEquals("2026-09-06", weather.dailyForecast[0].date)
        assertEquals(21.0, weather.dailyForecast[0].temperatureMax, 0.001)
        // 2026-09-07 is a Monday.
        assertEquals("monday", weather.dailyForecast[1].day)
    }

    @Test
    fun `strong wind turns any calm condition windy`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response(weatherCode = 0, windSpeed = 55.0)))
            .fetch(coordinates, "", "Assen")
        assertEquals(WeatherCondition.WINDY, weather.condition)
    }

    @Test
    fun `is_day falls back to the local hour when the field is missing`() = runTest {
        val night = OpenMeteoProvider(FakeApi(response(isDay = null, currentTime = "2026-09-05T22:00")))
            .fetch(coordinates, "", "Assen")
        assertTrue(!night.isDay)

        val day = OpenMeteoProvider(FakeApi(response(isDay = null, currentTime = "2026-09-05T09:00")))
            .fetch(coordinates, "", "Assen")
        assertTrue(day.isDay)
    }

    @Test
    fun `quotes are attached by the repository, not the provider`() = runTest {
        val weather = OpenMeteoProvider(FakeApi(response())).fetch(coordinates, "", "Assen")
        assertEquals("", weather.funnyQuote)
        assertEquals("", weather.subtitle)
    }
}
