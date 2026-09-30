package com.weatherquips.app

import com.weatherquips.app.data.api.OpenMeteoApi
import com.weatherquips.app.data.api.OpenWeatherMapApi
import com.weatherquips.app.data.api.WeatherApiApi
import com.weatherquips.app.data.model.OpenMeteoCurrent
import com.weatherquips.app.data.model.OpenMeteoDaily
import com.weatherquips.app.data.model.OpenMeteoHourly
import com.weatherquips.app.data.model.OpenMeteoResponse
import com.weatherquips.app.data.model.OwmCurrentResponse
import com.weatherquips.app.data.model.OwmForecastEntry
import com.weatherquips.app.data.model.OwmForecastResponse
import com.weatherquips.app.data.model.OwmMain
import com.weatherquips.app.data.model.OwmVolume
import com.weatherquips.app.data.model.OwmWeather
import com.weatherquips.app.data.model.WeatherApiCondition
import com.weatherquips.app.data.model.WeatherApiCurrent
import com.weatherquips.app.data.model.WeatherApiDay
import com.weatherquips.app.data.model.WeatherApiForecast
import com.weatherquips.app.data.model.WeatherApiForecastDay
import com.weatherquips.app.data.model.WeatherApiHour
import com.weatherquips.app.data.model.WeatherApiLocation
import com.weatherquips.app.data.model.WeatherApiResponse
import com.weatherquips.app.data.repository.OpenMeteoProvider
import com.weatherquips.app.data.repository.OpenWeatherMapProvider
import com.weatherquips.app.data.repository.WeatherApiProvider
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.ui.home.DayRain
import com.weatherquips.app.ui.home.DayRains
import com.weatherquips.app.widget.IntensityBand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** What the app says about a chosen day's rain, and where the figures come from. */
class DayRainTest {

    private fun day(
        mm: Double? = null,
        chance: Int? = null,
        hours: Double? = null,
        snow: Double? = null,
        hourly: List<com.weatherquips.app.domain.model.HourlyForecast> = emptyList(),
        step: Int = 1,
    ) = DailyForecast(
        day = "sunday",
        date = "2026-09-13",
        temperatureMax = 19.0,
        temperatureMin = 15.0,
        precipitationMm = mm,
        precipitationChance = chance,
        precipitationHours = hours,
        snowfallCm = snow,
        hours = hourly,
        hourStep = step,
    )

    // --- the summary ------------------------------------------------------

    @Test
    fun `an afternoon of showers has an amount, a window, a peak and a length`() {
        val rain = DayRains.of(
            day(
                mm = 6.4, chance = 80, hours = 5.0,
                hourly = TestWeather.dayOfHours(
                    14 to (0.4 to 60), 15 to (1.2 to 75), 16 to (2.6 to 80), 17 to (1.6 to 70), 18 to (0.6 to 50),
                ),
            ),
        )
        assertEquals(DayRain.Outlook.WET, rain.outlook)
        assertEquals(6.4, rain.totalMm!!, 0.001)
        assertEquals(80, rain.chance)
        assertEquals(DayRain.Window(from = "14:00", until = "19:00", allDay = false), rain.window)
        assertEquals("16:00", rain.peak?.time)
        assertEquals(IntensityBand.HEAVY, rain.peak?.band)
        assertEquals(5.0, rain.wetHours!!, 0.001)
        assertFalse("the provider gave the hours", rain.wetHoursEstimated)
        assertEquals(24, rain.bars.size)
    }

    @Test
    fun `a dry day has no window, no peak and no length`() {
        val rain = DayRains.of(day(mm = 0.0, chance = 5, hours = 0.0, hourly = TestWeather.dayOfHours()))
        assertEquals(DayRain.Outlook.DRY, rain.outlook)
        assertNull(rain.window)
        assertNull(rain.peak)
        assertNull(rain.wetHours)
    }

    @Test
    fun `saying nothing is not the same as saying dry`() {
        val rain = DayRains.of(day())
        assertEquals(DayRain.Outlook.UNKNOWN, rain.outlook)
        assertNull(rain.totalMm)
        assertTrue(rain.bars.isEmpty())
    }

    @Test
    fun `a real chance with next to nothing falling is a trace, not a dry day`() {
        val rain = DayRains.of(day(mm = 0.1, chance = 35, hourly = TestWeather.dayOfHours(9 to (0.1 to 35))))
        assertEquals(DayRain.Outlook.TRACE, rain.outlook)
        assertEquals(DayRain.Window("09:00", "10:00", allDay = false), rain.window)
    }

    @Test
    fun `models that round amounts away still get a window from the chance`() {
        val rain = DayRains.of(
            day(mm = 0.0, chance = 55, hourly = TestWeather.dayOfHours(20 to (0.0 to 55), 21 to (0.0 to 45))),
        )
        assertEquals(DayRain.Outlook.TRACE, rain.outlook)
        assertEquals(DayRain.Window("20:00", "22:00", allDay = false), rain.window)
        assertNull("no amount means no peak to name", rain.peak)
    }

    @Test
    fun `rain from morning to night is on and off all day`() {
        val wet = (5..23).map { it to (0.3 to 70) }.toTypedArray()
        val rain = DayRains.of(day(mm = 5.7, chance = 70, hourly = TestWeather.dayOfHours(*wet)))
        assertTrue(rain.window!!.allDay)
        // The last wet hour ends at midnight, which the clock calls 00:00.
        assertEquals("00:00", rain.window!!.until)
    }

    @Test
    fun `without the provider's figure the hours are counted, and marked as counted`() {
        val rain = DayRains.of(
            day(mm = 2.0, chance = 60, hourly = TestWeather.dayOfHours(10 to (1.0 to 60), 11 to (1.0 to 60))),
        )
        assertEquals(2.0, rain.wetHours!!, 0.001)
        assertTrue(rain.wetHoursEstimated)
    }

    @Test
    fun `three-hour slots count three hours each and end three hours later`() {
        val slots = listOf(
            com.weatherquips.app.domain.model.HourlyForecast("09:00", 12.0, 20, 0.0),
            com.weatherquips.app.domain.model.HourlyForecast("12:00", 12.0, 70, 0.8),
            com.weatherquips.app.domain.model.HourlyForecast("15:00", 12.0, 60, 0.4),
            com.weatherquips.app.domain.model.HourlyForecast("18:00", 12.0, 10, 0.0),
        )
        val rain = DayRains.of(day(mm = 3.6, chance = 70, hourly = slots, step = 3))
        assertEquals(DayRain.Window("12:00", "18:00", allDay = false), rain.window)
        assertEquals(6.0, rain.wetHours!!, 0.001)
        assertEquals(3, rain.barHours)
    }

    @Test
    fun `snow is mentioned only when some is expected`() {
        assertEquals(3.5, DayRains.of(day(mm = 4.0, chance = 90, snow = 3.5)).snowfallCm!!, 0.001)
        assertNull(DayRains.of(day(mm = 4.0, chance = 90, snow = 0.0)).snowfallCm)
    }

    @Test
    fun `with only hours to go on, the total and chance come from them`() {
        val rain = DayRains.of(day(hourly = TestWeather.dayOfHours(8 to (0.5 to 50), 9 to (0.3 to 65))))
        assertEquals(0.8, rain.totalMm!!, 0.001)
        assertEquals(65, rain.chance)
        assertEquals(DayRain.Outlook.WET, rain.outlook)
    }

    // --- Open-Meteo -------------------------------------------------------

    private class FakeOpenMeteo(private val response: OpenMeteoResponse) : OpenMeteoApi {
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

    @Test
    fun `open-meteo files the week's hours under their own day`() = runTest {
        val times = listOf("2026-09-05", "2026-09-06", "2026-09-07").flatMap { date ->
            (0..23).map { "%sT%02d:00".format(date, it) }
        }
        val response = OpenMeteoResponse(
            current = OpenMeteoCurrent(
                time = "2026-09-05T18:00", temperature = 15.0, weatherCode = 3, windSpeed = 5.0,
                apparentTemperature = 14.0, relativeHumidity = 70, surfacePressure = 1015.0,
                uvIndex = 0.0, isDay = 0,
            ),
            hourly = OpenMeteoHourly(
                time = times,
                temperature = times.map { 12.0 },
                precipitationProbability = times.map { if (it == "2026-09-06T15:00") 75 else 0 },
                precipitation = times.map { if (it == "2026-09-06T15:00") 1.8 else 0.0 },
            ),
            daily = OpenMeteoDaily(
                time = listOf("2026-09-05", "2026-09-06", "2026-09-07"),
                temperatureMax = listOf(18.0, 21.0, 22.0),
                temperatureMin = listOf(10.0, 11.0, 12.0),
                precipitationProbabilityMax = listOf(0, 75, 5),
                precipitationSum = listOf(0.0, 1.8, 0.0),
                precipitationHours = listOf(0.0, 1.0, 0.0),
                snowfallSum = listOf(0.0, 0.0, 0.0),
            ),
            minutely15 = null,
        )

        val weather = OpenMeteoProvider(FakeOpenMeteo(response)).fetch(Coordinates(52.0, 6.0), "", "Assen")
        val tomorrow = weather.dailyForecast.first()

        assertEquals("2026-09-06", tomorrow.date)
        assertEquals(1.8, tomorrow.precipitationMm!!, 0.001)
        assertEquals(75, tomorrow.precipitationChance)
        assertEquals(1.0, tomorrow.precipitationHours!!, 0.001)
        assertEquals(24, tomorrow.hours.size)
        assertEquals("00:00", tomorrow.hours.first().time)
        assertEquals(1.8, tomorrow.hours.single { it.time == "15:00" }.precipitationMm, 0.001)
        // The next day's hours are its own, not tomorrow's.
        assertEquals(0.0, weather.dailyForecast[1].hours.sumOf { it.precipitationMm }, 0.001)
        // The today strip is still the next hours from now, untouched.
        assertEquals("18:00", weather.hourlyForecast.first().time)
    }

    @Test
    fun `open-meteo without the new daily fields leaves them unknown, not zero`() = runTest {
        val response = OpenMeteoResponse(
            current = OpenMeteoCurrent(
                time = "2026-09-05T18:00", temperature = 15.0, weatherCode = 3, windSpeed = 5.0,
                apparentTemperature = 14.0, relativeHumidity = 70, surfacePressure = 1015.0,
                uvIndex = 0.0, isDay = 0,
            ),
            hourly = OpenMeteoHourly(),
            daily = OpenMeteoDaily(
                time = listOf("2026-09-05", "2026-09-06"),
                temperatureMax = listOf(18.0, 21.0),
                temperatureMin = listOf(10.0, 11.0),
            ),
            minutely15 = null,
        )
        val tomorrow = OpenMeteoProvider(FakeOpenMeteo(response))
            .fetch(Coordinates(52.0, 6.0), "", "Assen").dailyForecast.single()
        assertNull(tomorrow.precipitationMm)
        assertEquals(DayRain.Outlook.UNKNOWN, DayRains.of(tomorrow).outlook)
    }

    // --- OpenWeatherMap ---------------------------------------------------

    private class FakeOwm(
        private val current: OwmCurrentResponse,
        private val forecast: OwmForecastResponse,
    ) : OpenWeatherMapApi {
        override suspend fun current(latitude: Double, longitude: Double, apiKey: String, units: String) = current
        override suspend fun forecast(latitude: Double, longitude: Double, apiKey: String, units: String) = forecast
    }

    private fun owmMain(temp: Double) = OwmMain(temp, temp, temp, temp, 1015, 70)

    @Test
    fun `openweathermap sums its slots per local day, in the location's own clock`() = runTest {
        // Lübeck in summer: two hours ahead of UTC.
        val offset = ZoneOffset.ofHours(2)
        fun at(local: String) = LocalDateTime.parse(local).toEpochSecond(offset)
        val slots = listOf(
            "2026-09-05T20:00" to null,
            "2026-09-05T23:00" to null,
            // 23:00 UTC on the 5th: still the 5th in UTC, already the 6th in Lübeck.
            "2026-09-06T01:00" to 0.6,
            "2026-09-06T11:00" to 2.4,
            "2026-09-06T14:00" to 1.2,
            "2026-09-07T11:00" to null,
        ).map { (local, mm) ->
            OwmForecastEntry(
                dt = at(local),
                main = owmMain(14.0),
                pop = if (mm != null) 0.8 else 0.1,
                rain = mm?.let { OwmVolume(it) },
            )
        }
        val current = OwmCurrentResponse(
            weather = listOf(OwmWeather(500, icon = "10n")),
            main = owmMain(14.0),
            dt = at("2026-09-05T19:30"),
            timezone = 7200,
        )

        val weather = OpenWeatherMapProvider(FakeOwm(current, OwmForecastResponse(slots)))
            .fetch(Coordinates(53.87, 10.69), "key", "Lübeck")
        val tomorrow = weather.dailyForecast.first()

        assertEquals("2026-09-06", tomorrow.date)
        assertEquals(4.2, tomorrow.precipitationMm!!, 0.001)
        assertEquals(80, tomorrow.precipitationChance)
        assertEquals(3, tomorrow.hourStep)
        assertEquals(listOf("01:00", "11:00", "14:00"), tomorrow.hours.map { it.time })
        // Per hour, like every other provider: 2.4 mm over three hours.
        assertEquals(0.8, tomorrow.hours[1].precipitationMm, 0.001)
        // No hours-of-rain figure is invented for a provider that has none.
        assertNull(tomorrow.precipitationHours)
        // The today strip reads the location's clock too, not UTC.
        assertEquals("20:00", weather.hourlyForecast.first().time)
    }

    // --- WeatherAPI -------------------------------------------------------

    private class FakeWeatherApi(private val response: WeatherApiResponse) : WeatherApiApi {
        override suspend fun forecast(apiKey: String, query: String, days: Int, aqi: String) = response
    }

    @Test
    fun `weatherapi reports its daily totals and the day's hours`() = runTest {
        fun hours(date: String, wetHour: Int?, mm: Double) = (0..23).map { hour ->
            WeatherApiHour(
                time = "%s %02d:00".format(date, hour),
                tempC = 12.0,
                chanceOfRain = if (hour == wetHour) 70 else 0,
                chanceOfSnow = if (hour == wetHour) 20 else 0,
                precipMm = if (hour == wetHour) mm else 0.0,
            )
        }
        val response = WeatherApiResponse(
            location = WeatherApiLocation("Assen", "2026-09-05 18:12"),
            current = WeatherApiCurrent(
                tempC = 15.0, isDay = 0, condition = WeatherApiCondition(1003), windKph = 9.0,
                feelsLikeC = 14.0, humidity = 70, pressureMb = 1015.0,
            ),
            forecast = WeatherApiForecast(
                listOf(
                    WeatherApiForecastDay("2026-09-05", WeatherApiDay(18.0, 10.0, 10), hours("2026-09-05", null, 0.0)),
                    WeatherApiForecastDay(
                        "2026-09-06",
                        WeatherApiDay(
                            maxTempC = 20.0, minTempC = 11.0,
                            dailyChanceOfRain = 70, dailyChanceOfSnow = 85,
                            totalPrecipMm = 3.1, totalSnowCm = 1.5,
                        ),
                        hours("2026-09-06", 9, 3.1),
                    ),
                ),
            ),
        )

        val tomorrow = WeatherApiProvider(FakeWeatherApi(response))
            .fetch(Coordinates(52.99, 6.56), "key", "Assen").dailyForecast.single()

        assertEquals(3.1, tomorrow.precipitationMm!!, 0.001)
        // Rain or snow, whichever is likelier.
        assertEquals(85, tomorrow.precipitationChance)
        assertEquals(1.5, tomorrow.snowfallCm!!, 0.001)
        assertEquals(24, tomorrow.hours.size)
        assertEquals(70, tomorrow.hours.single { it.time == "09:00" }.precipitationChance)
        assertEquals(DayRain.Window("09:00", "10:00", allDay = false), DayRains.of(tomorrow).window)
    }
}
