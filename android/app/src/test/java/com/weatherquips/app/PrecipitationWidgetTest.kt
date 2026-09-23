package com.weatherquips.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.WidgetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget's composition, checked without a launcher.
 *
 * Glance renders through RemoteViews and cannot be rasterised here, so these
 * assert what the widget puts on screen rather than how it looks — the graph
 * itself is covered by WidgetGraphTest and drawn to PNG by WidgetRenderTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrecipitationWidgetTest {

    private val wide = DpSize(280.dp, 140.dp)
    private val narrow = DpSize(180.dp, 140.dp)

    /** A fixed clock: the widget ages its series against the cache time. */
    private val NOW = 1_757_000_000_000L

    private fun hourly(
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        hours: List<Pair<String, Int>>,
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition).copy(
            hourlyForecast = hours.map { (time, chance) -> HourlyForecast(time, 12.0, chance) },
            nowcast = emptyList(),
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    private fun nowcast(
        vararg rates: Double,
        condition: WeatherCondition = WeatherCondition.CLOUDY,
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition).copy(
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

    @Test
    fun `an approaching shower is counted down in minutes`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(wide)
        val outlook = PrecipitationOutlooks.from(
            nowcast(0.0, 0.0, 0.0, 0.0, 0.0, 1.4, 2.2),
            nowMillis = NOW,
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Rain in 45 min")).assertExists()
        onNode(hasText("Clock is ticking.")).assertExists()
        // The place it is reporting on, so two widgets are told apart.
        onNode(hasText("assen")).assertExists()
    }

    @Test
    fun `rain already falling leads with the rate`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(wide)
        val outlook = PrecipitationOutlooks.from(nowcast(0.8, 1.4, 1.8, 1.2, 0.4), nowMillis = NOW)

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Raining now")).assertExists()
        onNode(hasText("assen · 1.8 mm/h")).assertExists()
    }

    @Test
    fun `a dry window says so without inventing a time`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(wide)
        val outlook = PrecipitationOutlooks.from(nowcast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), nowMillis = NOW)

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Dry for now")).assertExists()
        // No rate to report, so the place stands alone.
        onNode(hasText("assen")).assertExists()
    }

    @Test
    fun `a provider without a nowcast still names the hour`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(wide)
        val outlook = PrecipitationOutlooks.from(
            hourly(hours = listOf("14:00" to 5, "15:00" to 20, "16:00" to 70)),
            nowMillis = NOW,
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Rain by 16:00")).assertExists()
    }

    @Test
    fun `with nothing cached it asks to be opened rather than showing blank`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(wide)

            provideComposable { WidgetContent(outlook = null, hourOfDay = 9) }

            onNode(hasText("No weather yet")).assertExists()
        }

    @Test
    fun `an empty graph says so instead of leaving a white rectangle`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(wide)
            // Hours old, with nothing left in range to plot.
            val outlook = PrecipitationOutlooks.from(
                nowcast(0.0, 0.0, 0.0, 0.0),
                nowMillis = NOW + 20 * 60 * 60_000L,
            )

            provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

            onNode(hasText("No forecast to draw. Tap to refresh.")).assertExists()
        }

    @Test
    fun `a widget with nothing cached points at the app rather than sitting blank`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(wide)

            provideComposable { WidgetContent(outlook = null, hourOfDay = 9) }

            onNode(hasText("Open the app once to get started")).assertExists()
        }

    @Test
    fun `stale data admits its age instead of passing as current`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(wide)
            val outlook = PrecipitationOutlooks.from(
                nowcast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                nowMillis = NOW + 3 * 60 * 60_000L,
            )

            provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

            onNode(hasText("assen · 3h ago")).assertExists()
        }

    @Test
    fun `a narrow widget drops the place before it drops the age`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(narrow)
            val outlook = PrecipitationOutlooks.from(
                nowcast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                nowMillis = NOW + 95 * 60_000L,
            )

            provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

            onNode(hasText("1h ago")).assertExists()
        }

    @Test
    fun `fresh data does not nag about its age`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(wide)
        val outlook = PrecipitationOutlooks.from(nowcast(0.0, 0.0, 0.0, 0.0), nowMillis = NOW)

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("assen")).assertExists()
    }

    @Test
    fun `the narrow size still leads with the headline`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(narrow)
        val outlook = PrecipitationOutlooks.from(nowcast(0.0, 0.0, 0.0, 0.9, 1.6), nowMillis = NOW)

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Rain in 15 min")).assertExists()
    }
}
