package com.weatherquips.app

import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.notifications.PrecipitationAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alert wording and trigger rules, ported from the web app's notification effect. */
class PrecipitationAlertsTest {

    private fun weather(
        condition: WeatherCondition = WeatherCondition.RAINY,
        precipitationChance: Int = 75,
        hourly: List<HourlyForecast> = emptyList(),
    ) = WeatherData(
        condition = condition,
        isDay = true,
        temperature = 12.0,
        description = condition.id,
        location = "Assen",
        funnyQuote = "",
        subtitle = "",
        feelsLike = 11.0,
        temperatureMax = 15.0,
        temperatureMin = 8.0,
        humidity = 80,
        precipitationChance = precipitationChance,
        windSpeed = 10.0,
        uvIndex = 1.0,
        pressure = 1010,
        dailyForecast = emptyList(),
        hourlyForecast = hourly,
    )

    @Test
    fun `wet conditions always trigger, dry ones need a high chance`() {
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.RAINY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.SNOWY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.STORMY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.CLEAR, 51)))
        assertFalse(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.CLEAR, 50)))
        assertFalse(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.CLOUDY, 20)))
    }

    @Test
    fun `body names the window and the peak`() {
        val alert = PrecipitationAlerts.build(
            weather(
                hourly = listOf(
                    HourlyForecast("13:00", 12.0, 10),
                    HourlyForecast("14:00", 12.0, 40),
                    HourlyForecast("16:00", 12.0, 90),
                    HourlyForecast("18:00", 12.0, 35),
                ),
            ),
        )

        assertEquals("Precipitation Alert", alert.title)
        assertEquals(
            "Rain expected — 75% chance today. Expected between 14:00–18:00. " +
                "Peak: 90% at 16:00. You might want an umbrella.",
            alert.body,
        )
    }

    @Test
    fun `a single wet hour reads as around that time`() {
        val alert = PrecipitationAlerts.build(
            weather(hourly = listOf(HourlyForecast("15:00", 12.0, 60))),
        )
        assertTrue(alert.body.contains("Most likely around 15:00."))
        assertTrue(alert.body.contains("Peak: 60% at 15:00."))
    }

    @Test
    fun `snow and storms get their own advice`() {
        val snow = PrecipitationAlerts.build(weather(condition = WeatherCondition.SNOWY))
        assertTrue(snow.body.startsWith("Snow expected"))
        assertTrue(snow.body.endsWith("Bundle up and watch for slippery conditions."))

        val storm = PrecipitationAlerts.build(weather(condition = WeatherCondition.STORMY))
        assertTrue(storm.body.startsWith("Storms expected"))
        assertTrue(storm.body.endsWith("Stay indoors if you can."))
    }

    @Test
    fun `no hourly data still produces a usable alert`() {
        val alert = PrecipitationAlerts.build(weather(hourly = emptyList()))
        assertEquals("Rain expected — 75% chance today. You might want an umbrella.", alert.body)
    }
}
