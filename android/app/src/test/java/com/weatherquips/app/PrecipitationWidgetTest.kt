package com.weatherquips.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
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
 * A widget cannot be rendered to a bitmap here, so these assert what it puts
 * on screen rather than how it looks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrecipitationWidgetTest {

    private fun cached(
        condition: WeatherCondition = WeatherCondition.CLOUDY,
        hourly: List<Pair<String, Int>>,
    ) = CachedWeather(
        data = TestWeather.sample(condition = condition).copy(
            hourlyForecast = hourly.map { (time, chance) ->
                HourlyForecast(time, 12.0, chance)
            },
        ),
        fetchedAtEpochMillis = 1_757_000_000_000,
        coordinates = Coordinates(52.99, 6.56),
    )

    @Test
    fun `an approaching shower is spelled out with its hour`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(280.dp, 110.dp))
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 5, "15:00" to 20, "16:00" to 70)),
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Rain by 16:00")).assertExists()
        onNode(hasText("Clock is ticking.")).assertExists()
        // The place it is reporting on, so two widgets are told apart.
        onNode(hasText("assen")).assertExists()
    }

    @Test
    fun `a dry window says so without inventing a time`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(280.dp, 110.dp))
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 0, "15:00" to 5, "16:00" to 10)),
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Dry for now")).assertExists()
    }

    @Test
    fun `rain already falling leads with that`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(280.dp, 110.dp))
        val outlook = PrecipitationOutlooks.from(
            cached(condition = WeatherCondition.RAINY, hourly = listOf("14:00" to 80)),
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Raining now")).assertExists()
    }

    @Test
    fun `each hour shows its chance, with the first marked as now`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(280.dp, 110.dp))
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 15, "15:00" to 25, "16:00" to 70)),
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("now")).assertExists()
        onNode(hasText("15%")).assertExists()
        onNode(hasText("70%")).assertExists()
    }

    @Test
    fun `with nothing cached it asks to be opened rather than showing blank`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(280.dp, 110.dp))

            provideComposable { WidgetContent(outlook = null, hourOfDay = 9) }

            onNode(hasText("No weather yet")).assertExists()
        }

    @Test
    fun `the narrow size still leads with the headline`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(180.dp, 110.dp))
        val outlook = PrecipitationOutlooks.from(
            cached(hourly = listOf("14:00" to 0, "15:00" to 55)),
        )

        provideComposable { WidgetContent(outlook = outlook, hourOfDay = 9) }

        onNode(hasText("Rain by 15:00")).assertExists()
    }
}
