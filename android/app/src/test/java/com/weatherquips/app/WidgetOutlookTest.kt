package com.weatherquips.app

import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.notifications.PrecipitationKind
import com.weatherquips.app.widget.Outlook
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.WidgetCopyWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the widget decides to say, which is the part worth pinning down. */
class WidgetOutlookTest {

    private fun cached(
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        hourly: List<Pair<String, Int>> = emptyList(),
        location: String = "Assen",
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition, location = location).copy(
            hourlyForecast = hourly.map { (time, chance) ->
                HourlyForecast(time = time, temperature = 12.0, precipitationChance = chance)
            },
        ),
        fetchedAtEpochMillis = 1_757_000_000_000,
        coordinates = Coordinates(52.99, 6.56),
    )

    @Test
    fun `a dry stretch says so`() {
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 0, "15:00" to 10, "16:00" to 20)),
        )
        assertEquals(Outlook.Dry, outlook.outlook)
        assertEquals("Dry for now", WidgetCopyWriter.write(outlook.outlook, hourOfDay = 9).headline)
    }

    @Test
    fun `an approaching shower names the hour it arrives`() {
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 5, "15:00" to 20, "16:00" to 70, "17:00" to 80)),
        )

        val starts = outlook.outlook
        assertTrue("expected StartsAt but was $starts", starts is Outlook.StartsAt)
        starts as Outlook.StartsAt
        assertEquals("16:00", starts.time)
        assertEquals(70, starts.chancePercent)
        assertEquals("Rain by 16:00", WidgetCopyWriter.write(starts, hourOfDay = 9).headline)
    }

    @Test
    fun `the first hour over the threshold wins, not the heaviest`() {
        // What matters on a home screen is when to leave, not the peak.
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 0, "15:00" to 45, "16:00" to 95)),
        )
        assertEquals("15:00", (outlook.outlook as Outlook.StartsAt).time)
    }

    @Test
    fun `a shower below the threshold is not worth mentioning`() {
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 0, "15:00" to 39, "16:00" to 30)),
        )
        assertEquals(Outlook.Dry, outlook.outlook)
    }

    @Test
    fun `wet conditions read as happening now, whatever the hourly says`() {
        listOf(
            WeatherCondition.RAINY to PrecipitationKind.RAIN,
            WeatherCondition.SNOWY to PrecipitationKind.SNOW,
            WeatherCondition.STORMY to PrecipitationKind.STORM,
        ).forEach { (condition, kind) ->
            val outlook = PrecipitationOutlooks.from(
                cached(condition = condition, hourly = listOf("14:00" to 0)),
            )
            assertEquals(Outlook.FallingNow(kind), outlook.outlook)
        }

        assertEquals(
            "Snowing now",
            WidgetCopyWriter.write(Outlook.FallingNow(PrecipitationKind.SNOW), hourOfDay = 3).headline,
        )
    }

    @Test
    fun `a high chance this hour counts as falling now`() {
        val outlook = PrecipitationOutlooks.from(
            cached(condition = WeatherCondition.CLOUDY, hourly = listOf("14:00" to 60)),
        )
        assertEquals(Outlook.FallingNow(PrecipitationKind.RAIN), outlook.outlook)
    }

    @Test
    fun `the chart covers the next few hours and marks the first as now`() {
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = (10..20).map { "%02d:00".format(it) to it }),
        )

        assertEquals(PrecipitationOutlooks.WINDOW_HOURS, outlook.hours.size)
        assertTrue(outlook.hours.first().isNow)
        assertTrue(outlook.hours.drop(1).none { it.isNow })
        assertEquals("10:00", outlook.hours.first().label)
        assertEquals(10, outlook.hours.first().chancePercent)
    }

    @Test
    fun `the coordinates ride along so a tap can open the radar`() {
        val outlook = PrecipitationOutlooks.from(cached())
        assertEquals(Coordinates(52.99, 6.56), outlook.coordinates)
        assertEquals("Assen", outlook.location)
    }

    @Test
    fun `an empty forecast does not crash the widget`() {
        val outlook = PrecipitationOutlooks.from(cached(hourly = emptyList()))
        assertEquals(Outlook.Dry, outlook.outlook)
        assertTrue(outlook.hours.isEmpty())
    }

    @Test
    fun `the remark rotates through the day but never mid-refresh`() {
        val outlook = Outlook.Dry
        val sameHour = (0 until 5).map { WidgetCopyWriter.write(outlook, hourOfDay = 13).aside }
        assertEquals("the remark changed without the hour changing", 1, sameHour.distinct().size)

        val acrossDay = (0 until 24).map { WidgetCopyWriter.write(outlook, hourOfDay = it).aside }
        assertTrue("the remark never changes all day", acrossDay.distinct().size > 1)
    }

    @Test
    fun `copy never leaves a blank line on the widget`() {
        val outlooks = listOf(Outlook.Dry, WidgetCopyWriter.empty()) to Unit
        PrecipitationKind.entries.forEach { kind ->
            listOf(
                Outlook.FallingNow(kind),
                Outlook.StartsAt(kind, "16:00", 70),
            ).forEach { outlook ->
                (0 until 24).forEach { hour ->
                    val copy = WidgetCopyWriter.write(outlook, hour)
                    assertTrue("blank headline for $outlook", copy.headline.isNotBlank())
                    assertTrue("blank aside for $outlook", copy.aside.isNotBlank())
                }
            }
        }
        assertTrue(WidgetCopyWriter.empty().headline.isNotBlank())
        assertTrue(outlooks.first.isNotEmpty())
    }
}
