package com.weatherquips.app

import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.notifications.PrecipitationAlerts
import com.weatherquips.app.notifications.PrecipitationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Alert wording: funny on top, useful underneath. */
class PrecipitationAlertsTest {

    private fun weather(
        condition: WeatherCondition = WeatherCondition.RAINY,
        precipitationChance: Int = 75,
        hourly: List<HourlyForecast> = emptyList(),
        location: String = "Assen",
    ) = WeatherData(
        condition = condition,
        isDay = true,
        temperature = 12.0,
        description = condition.id,
        location = location,
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

    private val wetAfternoon = listOf(
        HourlyForecast("13:00", 12.0, 10),
        HourlyForecast("14:00", 12.0, 40),
        HourlyForecast("16:00", 12.0, 90),
        HourlyForecast("18:00", 12.0, 35),
    )

    @Test
    fun `wet conditions always trigger, dry ones need a high chance`() {
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.RAINY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.SNOWY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.STORMY, 0)))
        assertTrue(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.CLEAR, 51)))
        assertFalse(PrecipitationAlerts.shouldNotify(weather(WeatherCondition.CLEAR, 50)))
    }

    @Test
    fun `the headline is a joke from the matching set`() {
        val alert = PrecipitationAlerts.build(weather(hourly = wetAfternoon), Random(3))
        assertEquals(PrecipitationKind.RAIN, alert.kind)
        assertTrue(
            "headline was not one of the rain lines: ${alert.headline}",
            alert.headline in PrecipitationAlerts.headlinesFor(PrecipitationKind.RAIN),
        )
    }

    @Test
    fun `the collapsed line carries the figures, not the joke`() {
        val alert = PrecipitationAlerts.build(weather(hourly = wetAfternoon), Random(3))
        // Someone who never expands the notification still learns something.
        assertEquals("75% chance of rain, heaviest around 16:00", alert.summary)
    }

    @Test
    fun `the expanded text gives the window, the peak and a parting shot`() {
        val alert = PrecipitationAlerts.build(weather(hourly = wetAfternoon), Random(3))

        assertTrue(alert.detail.startsWith("75% chance of rain today."))
        assertTrue(alert.detail.contains("Expect it between 14:00 and 18:00."))
        assertTrue(alert.detail.contains("It peaks at 90% around 16:00."))
        assertTrue(
            "no aside in: ${alert.detail}",
            PrecipitationAlerts.asidesFor(PrecipitationKind.RAIN).any { alert.detail.endsWith(it) },
        )
    }

    @Test
    fun `snow and storms get their own voice and kind`() {
        val snow = PrecipitationAlerts.build(weather(condition = WeatherCondition.SNOWY), Random(1))
        assertEquals(PrecipitationKind.SNOW, snow.kind)
        assertTrue(snow.headline in PrecipitationAlerts.headlinesFor(PrecipitationKind.SNOW))
        assertTrue(snow.summary.contains("snow"))

        val storm = PrecipitationAlerts.build(weather(condition = WeatherCondition.STORMY), Random(1))
        assertEquals(PrecipitationKind.STORM, storm.kind)
        assertTrue(storm.headline in PrecipitationAlerts.headlinesFor(PrecipitationKind.STORM))
        assertTrue(storm.summary.contains("storms"))
    }

    @Test
    fun `a single wet hour reads as around that time`() {
        val alert = PrecipitationAlerts.build(
            weather(hourly = listOf(HourlyForecast("15:00", 12.0, 60))),
            Random(2),
        )
        assertTrue(alert.detail.contains("Most likely around 15:00."))
        assertTrue(alert.detail.contains("It peaks at 60% around 15:00."))
    }

    @Test
    fun `no hourly data still produces a usable alert`() {
        val alert = PrecipitationAlerts.build(weather(hourly = emptyList()), Random(4))
        assertEquals("75% chance of rain", alert.summary)
        assertTrue(alert.detail.startsWith("75% chance of rain today."))
    }

    @Test
    fun `the location rides along for the notification header`() {
        assertEquals("Assen", PrecipitationAlerts.build(weather(), Random(1)).location)
        assertEquals(null, PrecipitationAlerts.build(weather(location = ""), Random(1)).location)
    }

    @Test
    fun `no highlight markers leak into plain notification text`() {
        // The in-app quips use **word**; a notification would print the stars.
        PrecipitationKind.entries.forEach { kind ->
            (PrecipitationAlerts.headlinesFor(kind) + PrecipitationAlerts.asidesFor(kind))
                .forEach { line ->
                    assertFalse("markers in: $line", line.contains("**"))
                    assertTrue("empty line for $kind", line.isNotBlank())
                }
        }
    }

    @Test
    fun `every kind has several lines to choose from`() {
        PrecipitationKind.entries.forEach { kind ->
            assertTrue(PrecipitationAlerts.headlinesFor(kind).size >= 4)
            assertTrue(PrecipitationAlerts.asidesFor(kind).size >= 4)
            // Repeating the same alert twice in a row would read as a bug.
            assertEquals(
                PrecipitationAlerts.headlinesFor(kind).size,
                PrecipitationAlerts.headlinesFor(kind).distinct().size,
            )
        }
    }

    @Test
    fun `selection is reproducible for a given seed`() {
        val first = PrecipitationAlerts.build(weather(hourly = wetAfternoon), Random(9))
        val second = PrecipitationAlerts.build(weather(hourly = wetAfternoon), Random(9))
        assertEquals(first, second)
    }

    @Test
    fun `the test notification is a real, complete alert`() {
        val sample = PrecipitationAlerts.sample(Random(5))
        assertTrue(sample.headline in PrecipitationAlerts.headlinesFor(PrecipitationKind.RAIN))
        assertTrue(sample.summary.contains("75%"))
        assertTrue(sample.detail.contains("16:00"))
    }
}
