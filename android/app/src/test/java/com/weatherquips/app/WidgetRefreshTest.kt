package com.weatherquips.app

import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.widget.ChartResolution
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.WidgetCopyWriter
import com.weatherquips.app.widget.WidgetLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What keeps the widget alive between refreshes.
 *
 * Both halves of the same bug: the background refresh could not find a
 * location and therefore never fetched, and the drawing code turned an old
 * cache into an empty rectangle instead of a worse-but-honest graph.
 */
class WidgetRefreshTest {

    private val here = Coordinates(51.22, 4.48)
    private val lastKnown = Coordinates(53.87, 10.69)

    // --- where the refresh fetches for ----------------------------------

    @Test
    fun `a background refresh with no location fix falls back on the last place shown`() = runTest {
        // Android starves a background app of location without the background
        // permission, so this is the normal case, not the edge case.
        val resolved = WidgetLocation.resolve(
            settings = AppSettings(locationMode = LocationMode.DEVICE),
            deviceFix = { null },
            lastKnownPlace = { lastKnown },
        )
        assertEquals(lastKnown, resolved)
    }

    @Test
    fun `a device fix is still preferred when one happens to be available`() = runTest {
        val resolved = WidgetLocation.resolve(
            settings = AppSettings(locationMode = LocationMode.DEVICE),
            deviceFix = { here },
            lastKnownPlace = { lastKnown },
        )
        assertEquals(here, resolved)
    }

    @Test
    fun `a manual location never asks the device where it is`() = runTest {
        var asked = false
        val resolved = WidgetLocation.resolve(
            settings = AppSettings(locationMode = LocationMode.MANUAL, manualCoords = here),
            deviceFix = { asked = true; lastKnown },
            lastKnownPlace = { lastKnown },
        )
        assertEquals(here, resolved)
        assertFalse("the manual location was ignored", asked)
    }

    @Test
    fun `with nothing to go on it gives up rather than inventing a place`() = runTest {
        assertNull(
            WidgetLocation.resolve(
                settings = AppSettings(locationMode = LocationMode.DEVICE),
                deviceFix = { null },
                lastKnownPlace = { null },
            ),
        )
    }

    // --- what an old cache draws ----------------------------------------

    private val fetchedAt = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()

    /** A normal Open-Meteo payload: radar-style nowcast plus hourly totals. */
    private fun cached() = CachedWeather(
        data = TestWeather.sample(location = "Wijnegem").copy(
            utcOffsetSeconds = 0,
            nowcast = (-12..23).map {
                NowcastPoint(time = "12:00", minutesFromNow = it * 5, millimetresPerHour = 0.0)
            },
            // Likely rain at 13:00 — which is in the past by the time the
            // stale cases read this — and again at 16:00, which is not.
            hourlyForecast = (12..23).map {
                HourlyForecast(
                    time = "%02d:00".format(it),
                    temperature = 14.0,
                    precipitationChance = if (it == 13 || it == 16) 70 else 10,
                    precipitationMm = if (it == 16) 1.2 else 0.0,
                )
            },
        ),
        fetchedAtEpochMillis = fetchedAt,
        coordinates = here,
    )

    private fun at(hoursLater: Long) =
        PrecipitationOutlooks.from(cached(), nowMillis = fetchedAt + hoursLater * 3_600_000L)

    @Test
    fun `a cache too old for the nowcast still draws, from the hourly forecast`() {
        // The whole failure: three hours without a successful refresh slid the
        // five-minute series off the left of the graph, and the widget drew a
        // blank rectangle under the headline.
        val stale = at(hoursLater = 3)
        assertFalse("the graph went blank on a three-hour-old cache", stale.chart.isEmpty)
        assertEquals(ChartResolution.HOURLY, stale.chart.resolution)
    }

    @Test
    fun `the hourly fallback is placed by its clock, not by its position`() {
        // Fetched at 12:00 and read at 15:00, "16:00" is an hour away — not
        // four hours away, which is where indexing from the fetch would put it.
        val stale = at(hoursLater = 3)
        val labelled = stale.chart.points.zip(stale.chart.ticks)
        assertTrue(labelled.isNotEmpty())
        assertEquals(
            "16:00 should be an hour out",
            60,
            stale.chart.ticks.first { it.label == "16:00" }.minutesFromNow,
        )
    }

    @Test
    fun `rain in the hourly forecast survives the nowcast expiring`() {
        val stale = at(hoursLater = 3)
        assertEquals(1.2, stale.chart.peakMillimetresPerHour, 0.0001)
    }

    @Test
    fun `a fresh cache still uses the minute-level series`() {
        val fresh = at(hoursLater = 0)
        assertEquals(ChartResolution.SUB_HOURLY, fresh.chart.resolution)
        assertFalse(fresh.chart.isEmpty)
    }

    @Test
    fun `the age rides along so the widget can admit how old it is`() {
        assertEquals(0, at(hoursLater = 0).ageMinutes)
        assertEquals(180, at(hoursLater = 3).ageMinutes)
    }

    @Test
    fun `a stale widget never announces an hour that has already passed`() {
        // Fetched at noon, read at three: the first hour worth naming is the
        // one still ahead, not the one the forecast opened with.
        val stale = at(hoursLater = 3)
        val headline = WidgetCopyWriter.write(stale.outlook, hourOfDay = 15).headline
        listOf("12:00", "13:00", "14:00").forEach {
            assertFalse("announced $it, which is in the past: $headline", headline.contains(it))
        }
    }

    @Test
    fun `a cache older than the forecast it holds finally gives up`() {
        // Past the end of the hourly data there is genuinely nothing to draw,
        // and the widget says so rather than drawing an empty box.
        val ancient = at(hoursLater = 20)
        assertTrue(ancient.chart.isEmpty)
    }
}
