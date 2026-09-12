package com.weatherquips.app

import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.notifications.PrecipitationKind
import com.weatherquips.app.widget.ChartResolution
import com.weatherquips.app.widget.Outlook
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.WidgetCopyWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the widget decides to say, which is the part worth pinning down. */
class WidgetOutlookTest {

    /** A forecast with only hourly probabilities, as the paid providers give. */
    /** A fixed clock: the widget ages its series against the cache time. */
    private val NOW = 1_757_000_000_000L

    private fun hourlyOnly(
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        hourly: List<Pair<String, Int>> = emptyList(),
        location: String = "Assen",
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition, location = location).copy(
            hourlyForecast = hourly.map { (time, chance) ->
                HourlyForecast(time = time, temperature = 12.0, precipitationChance = chance)
            },
            nowcast = emptyList(),
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    /**
     * Quarter-hourly intensities starting half an hour ago, which is what
     * Open-Meteo's minutely feed looks like.
     */
    private fun withNowcast(
        vararg rates: Double,
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        hourly: List<Pair<String, Int>> = emptyList(),
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition).copy(
            hourlyForecast = hourly.map { (time, chance) ->
                HourlyForecast(time = time, temperature = 12.0, precipitationChance = chance)
            },
            nowcast = rates.mapIndexed { i, mm ->
                val minutes = 17 * 60 + 30 + i * 15
                NowcastPoint(
                    time = "%02d:%02d".format((minutes / 60) % 24, minutes % 60),
                    minutesFromNow = i * 15 - 30,
                    millimetresPerHour = mm,
                )
            },
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    // --- the nowcast path ------------------------------------------------

    @Test
    fun `rain already falling is read off the nowcast, not the condition`() {
        val outlook = PrecipitationOutlooks.from(withNowcast(0.8, 1.2, 1.8, 1.4, 0.6), nowMillis = NOW)
        assertEquals(Outlook.FallingNow(PrecipitationKind.RAIN, 1.8), outlook.outlook)
        assertEquals(1.8, outlook.nowMillimetresPerHour, 0.0001)
        assertEquals("Raining now", WidgetCopyWriter.write(outlook.outlook, 9).headline)
    }

    @Test
    fun `an approaching shower is counted in minutes`() {
        // Dry now, first wet quarter at +45 minutes.
        val outlook = PrecipitationOutlooks.from(
            withNowcast(0.0, 0.0, 0.0, 0.0, 0.0, 1.2, 2.4),
            nowMillis = NOW,
        )
        val starts = outlook.outlook
        assertTrue("expected StartsIn but was $starts", starts is Outlook.StartsIn)
        starts as Outlook.StartsIn
        assertEquals(45, starts.minutesAway)
        assertEquals("18:45", starts.time)
        assertEquals("Rain in 45 min", WidgetCopyWriter.write(starts, 9).headline)
    }

    @Test
    fun `arrival is rounded to something a person would act on`() {
        val outlook = Outlook.StartsIn(PrecipitationKind.RAIN, "18:23", minutesAway = 23)
        assertEquals("Rain in 25 min", WidgetCopyWriter.write(outlook, 9).headline)

        // Never "in 0 min" — that is what "Raining now" is for.
        val imminent = Outlook.StartsIn(PrecipitationKind.RAIN, "18:02", minutesAway = 2)
        assertEquals("Rain in 5 min", WidgetCopyWriter.write(imminent, 9).headline)
    }

    @Test
    fun `far-off rain gets a clock time instead of a minute count`() {
        val outlook = Outlook.StartsIn(PrecipitationKind.SNOW, "20:15", minutesAway = 105)
        assertEquals("Snow by 20:15", WidgetCopyWriter.write(outlook, 9).headline)
    }

    @Test
    fun `a trace of drizzle is not announced as rain`() {
        val outlook = PrecipitationOutlooks.from(
            withNowcast(0.0, 0.0, 0.04, 0.02, 0.0, 0.0),
            nowMillis = NOW,
        )
        assertEquals(Outlook.Dry, outlook.outlook)
    }

    @Test
    fun `a dry nowcast still warns about rain beyond its window`() {
        // The nowcast reaches +90 minutes; the shower is four hours out.
        val outlook = PrecipitationOutlooks.from(
            withNowcast(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                hourly = listOf(
                    "18:00" to 0, "19:00" to 5, "20:00" to 10,
                    "21:00" to 20, "22:00" to 75,
                ),
            ),
            nowMillis = NOW,
        )
        assertEquals(
            Outlook.StartsAt(PrecipitationKind.RAIN, "22:00", 75),
            outlook.outlook,
        )
    }

    @Test
    fun `a dry nowcast is trusted over the hours it actually covers`() {
        // Hour two says 80%, but the minute-by-minute forecast says no.
        val outlook = PrecipitationOutlooks.from(
            withNowcast(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                hourly = listOf("18:00" to 0, "19:00" to 80, "20:00" to 10),
            ),
            nowMillis = NOW,
        )
        assertEquals(Outlook.Dry, outlook.outlook)
    }

    // --- the reasons the graph used to come out flat ---------------------

    @Test
    fun `a quantised minute forecast does not override hourly millimetres`() {
        // Open-Meteo rounds minutely_15 to a tenth of a millimetre per
        // quarter-hour, so light rain lands on exactly zero while the hourly
        // field still reports it. Drawing the zeros is how the widget ends up
        // claiming a dry afternoon in the rain.
        val cached = CachedWeather(
            data = TestWeather.sample().copy(
                nowcast = (0..8).map {
                    NowcastPoint("18:%02d".format(it * 15 % 60), it * 15 - 30, 0.0)
                },
                hourlyForecast = listOf(
                    HourlyForecast("18:00", 14.0, 80, 0.3),
                    HourlyForecast("19:00", 14.0, 80, 0.2),
                ),
            ),
            fetchedAtEpochMillis = NOW,
            coordinates = Coordinates(52.99, 6.56),
        )

        val outlook = PrecipitationOutlooks.from(cached, nowMillis = NOW)
        assertEquals(ChartResolution.HOURLY, outlook.chart.resolution)
        assertEquals(0.3, outlook.nowMillimetresPerHour, 0.0001)
        assertEquals(Outlook.FallingNow(PrecipitationKind.RAIN, 0.3), outlook.outlook)
    }

    @Test
    fun `a genuinely dry minute forecast is still trusted over a dry hourly one`() {
        val outlook = PrecipitationOutlooks.from(
            withNowcast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            nowMillis = NOW,
        )
        assertEquals(ChartResolution.SUB_HOURLY, outlook.chart.resolution)
        assertEquals(Outlook.Dry, outlook.outlook)
    }

    @Test
    fun `the series slides back as the cache ages, so now stays now`() {
        // Rain arriving 45 minutes after the fetch is 25 minutes away when the
        // widget redraws 20 minutes later.
        val cached = withNowcast(0.0, 0.0, 0.0, 0.0, 0.0, 1.4, 2.0)
        val later = PrecipitationOutlooks.from(cached, nowMillis = NOW + 20 * 60_000L)

        val starts = later.outlook
        assertTrue("expected StartsIn but was $starts", starts is Outlook.StartsIn)
        assertEquals(25, (starts as Outlook.StartsIn).minutesAway)
        assertEquals("18:45", starts.time)
    }

    @Test
    fun `rain that started since the last fetch reads as falling now`() {
        val cached = withNowcast(0.0, 0.0, 0.0, 1.2, 1.8, 1.4)
        // The wet quarter at +15 is underfoot twenty minutes later.
        val later = PrecipitationOutlooks.from(cached, nowMillis = NOW + 20 * 60_000L)
        assertTrue(later.outlook is Outlook.FallingNow)
        assertTrue(later.nowMillimetresPerHour > 0.0)
    }

    @Test
    fun `history that has scrolled off the graph is dropped`() {
        val cached = withNowcast(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        val later = PrecipitationOutlooks.from(cached, nowMillis = NOW + 40 * 60_000L)

        assertTrue(
            "kept a point older than the history limit",
            later.chart.points.all { it.minutesFromNow >= -PrecipitationOutlooks.MAX_HISTORY_MINUTES },
        )
        assertTrue("dropped the whole series", later.chart.points.isNotEmpty())
    }

    // --- the graph -------------------------------------------------------

    @Test
    fun `the graph keeps every sample and knows where now is`() {
        val outlook = PrecipitationOutlooks.from(withNowcast(0.1, 0.2, 0.3, 0.4, 0.5), nowMillis = NOW)
        assertEquals(5, outlook.chart.points.size)
        assertEquals(-30, outlook.chart.startMinutes)
        assertEquals(30, outlook.chart.endMinutes)
        assertEquals(ChartResolution.SUB_HOURLY, outlook.chart.resolution)
        assertEquals(0.5, outlook.chart.peakMillimetresPerHour, 0.0001)
    }

    @Test
    fun `only the whole and half hours are labelled`() {
        val outlook = PrecipitationOutlooks.from(withNowcast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), nowMillis = NOW)
        // 17:30, 17:45, 18:00, 18:15, 18:30, 18:45, 19:00
        assertEquals(listOf("17:30", "18:00", "18:30", "19:00"), outlook.chart.ticks.map { it.label })
    }

    @Test
    fun `without a nowcast the graph falls back to hourly millimetres`() {
        val cached = CachedWeather(
            data = TestWeather.sample().copy(
                nowcast = emptyList(),
                hourlyForecast = listOf(
                    HourlyForecast("18:00", 14.0, 20, 0.0),
                    HourlyForecast("19:00", 13.0, 55, 1.1),
                    HourlyForecast("20:00", 12.0, 70, 2.6),
                ),
            ),
            fetchedAtEpochMillis = NOW,
            coordinates = Coordinates(52.99, 6.56),
        )
        val outlook = PrecipitationOutlooks.from(cached, nowMillis = NOW)
        assertEquals(ChartResolution.HOURLY, outlook.chart.resolution)
        assertEquals(listOf(0.0, 1.1, 2.6), outlook.chart.points.map { it.millimetresPerHour })
        assertEquals(listOf("18:00", "19:00", "20:00"), outlook.chart.ticks.map { it.label })
    }

    // --- the hourly-only fallback ---------------------------------------

    @Test
    fun `a dry stretch says so`() {
        val outlook = PrecipitationOutlooks.from(
            hourlyOnly(hourly = listOf("14:00" to 0, "15:00" to 10, "16:00" to 20)),
            nowMillis = NOW,
        )
        assertEquals(Outlook.Dry, outlook.outlook)
        assertEquals("Dry for now", WidgetCopyWriter.write(outlook.outlook, hourOfDay = 9).headline)
    }

    @Test
    fun `an approaching shower names the hour it arrives`() {
        val outlook = PrecipitationOutlooks.from(
            hourlyOnly(hourly = listOf("14:00" to 5, "15:00" to 20, "16:00" to 70, "17:00" to 80)),
            nowMillis = NOW,
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
            hourlyOnly(hourly = listOf("14:00" to 0, "15:00" to 45, "16:00" to 95)),
            nowMillis = NOW,
        )
        assertEquals("15:00", (outlook.outlook as Outlook.StartsAt).time)
    }

    @Test
    fun `a shower below the threshold is not worth mentioning`() {
        val outlook = PrecipitationOutlooks.from(
            hourlyOnly(hourly = listOf("14:00" to 0, "15:00" to 39, "16:00" to 30)),
            nowMillis = NOW,
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
                hourlyOnly(condition = condition, hourly = listOf("14:00" to 0)),
                nowMillis = NOW,
            )
            assertEquals(Outlook.FallingNow(kind, 0.0), outlook.outlook)
        }

        assertEquals(
            "Snowing now",
            WidgetCopyWriter.write(Outlook.FallingNow(PrecipitationKind.SNOW), hourOfDay = 3).headline,
        )
    }

    @Test
    fun `a high chance this hour counts as falling now`() {
        val outlook = PrecipitationOutlooks.from(
            hourlyOnly(condition = WeatherCondition.CLOUDY, hourly = listOf("14:00" to 60)),
            nowMillis = NOW,
        )
        assertEquals(Outlook.FallingNow(PrecipitationKind.RAIN, 0.0), outlook.outlook)
    }

    // --- housekeeping ----------------------------------------------------

    @Test
    fun `the coordinates ride along so a tap can open the radar`() {
        val outlook = PrecipitationOutlooks.from(hourlyOnly(), nowMillis = NOW)
        assertEquals(Coordinates(52.99, 6.56), outlook.coordinates)
        assertEquals("Assen", outlook.location)
    }

    @Test
    fun `an empty forecast does not crash the widget`() {
        val outlook = PrecipitationOutlooks.from(hourlyOnly(hourly = emptyList()))
        assertEquals(Outlook.Dry, outlook.outlook)
        assertTrue(outlook.chart.isEmpty)
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
        PrecipitationKind.entries.forEach { kind ->
            listOf(
                Outlook.FallingNow(kind, 2.4),
                Outlook.StartsIn(kind, "16:00", 40),
                Outlook.StartsIn(kind, "19:00", 140),
                Outlook.StartsAt(kind, "16:00", 70),
                Outlook.Dry,
            ).forEach { outlook ->
                (0 until 24).forEach { hour ->
                    val copy = WidgetCopyWriter.write(outlook, hour)
                    assertTrue("blank headline for $outlook", copy.headline.isNotBlank())
                    assertTrue("blank aside for $outlook", copy.aside.isNotBlank())
                }
            }
        }
        assertTrue(WidgetCopyWriter.empty().headline.isNotBlank())
        assertTrue(WidgetCopyWriter.empty().aside.isNotBlank())
    }
}
