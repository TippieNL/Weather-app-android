package com.weatherquips.app

import android.graphics.Color
import com.weatherquips.app.widget.ChartPoint
import com.weatherquips.app.widget.ChartResolution
import com.weatherquips.app.widget.ChartTick
import com.weatherquips.app.widget.IntensityBand
import com.weatherquips.app.widget.IntensityScale
import com.weatherquips.app.widget.PrecipitationChart
import com.weatherquips.app.widget.PrecipitationGraph
import com.weatherquips.app.widget.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The intensity axis and the bitmap the widget actually ships. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetGraphTest {

    // --- the scale -------------------------------------------------------

    @Test
    fun `dry sits on the floor and a cloudburst is capped at the ceiling`() {
        assertEquals(0f, IntensityScale.fraction(0.0), 0.0001f)
        assertEquals(1f, IntensityScale.fraction(40.0), 0.0001f)
    }

    @Test
    fun `each band gets an equal quarter of the height`() {
        assertEquals(0.25f, IntensityScale.fraction(IntensityBand.LIGHT.millimetresPerHour), 0.0001f)
        assertEquals(0.5f, IntensityScale.fraction(IntensityBand.MODERATE.millimetresPerHour), 0.0001f)
        assertEquals(0.75f, IntensityScale.fraction(IntensityBand.HEAVY.millimetresPerHour), 0.0001f)
    }

    @Test
    fun `the scale never goes backwards`() {
        var previous = -1f
        var rate = 0.0
        while (rate <= 20.0) {
            val fraction = IntensityScale.fraction(rate)
            assertTrue("fraction dropped at $rate", fraction >= previous)
            previous = fraction
            rate += 0.05
        }
    }

    @Test
    fun `anything the radar can measure is tall enough to see`() {
        // The whole point of the non-linear axis: on a linear 0..15 scale the
        // drizzle that actually passed over Lubeck would be under one percent
        // of the height, which is what "the widget shows nothing" looks like.
        assertTrue(
            "0.12 mm/h drew ${IntensityScale.fraction(0.12)} of the plot",
            IntensityScale.fraction(0.12) >= 0.1f,
        )
        assertTrue(IntensityScale.fraction(IntensityScale.WET_MM_PER_HOUR) >= 0.1f)
        assertTrue(IntensityScale.fraction(0.3) > IntensityScale.fraction(0.12))
    }

    @Test
    fun `a trace of rain is not called rain`() {
        assertNull(IntensityScale.bandOf(0.0))
        assertNull(IntensityScale.bandOf(0.04))
        assertEquals(IntensityBand.LIGHT, IntensityScale.bandOf(0.3))
        assertEquals(IntensityBand.MODERATE, IntensityScale.bandOf(1.8))
        assertEquals(IntensityBand.VIOLENT, IntensityScale.bandOf(99.0))
    }

    @Test
    fun `rates are printed the way a forecast reads them`() {
        assertEquals("1.8 mm/h", IntensityScale.format(1.79))
        assertEquals("0.3 mm/h", IntensityScale.format(0.34))
        assertEquals("23 mm/h", IntensityScale.format(22.7))
    }

    // --- the bitmap ------------------------------------------------------

    private fun chart(vararg rates: Double, startMinutes: Int = -30, step: Int = 15) =
        PrecipitationChart(
            points = rates.mapIndexed { i, mm -> ChartPoint(startMinutes + i * step, mm) },
            ticks = listOf(ChartTick(0, "18:00"), ChartTick(60, "19:00")),
            resolution = ChartResolution.SUB_HOURLY,
        )

    @Test
    fun `the bitmap matches the space it is given`() {
        val bitmap = PrecipitationGraph.render(chart(0.0, 1.0, 2.0), 252f, 60f, WidgetColors.graph)
        assertNotNull(bitmap)
        assertEquals((252f * PrecipitationGraph.RENDER_SCALE).toInt(), bitmap!!.width)
        assertEquals((60f * PrecipitationGraph.RENDER_SCALE).toInt(), bitmap.height)
    }

    @Test
    fun `a stretched widget is scaled down rather than blowing the payload`() {
        // A widget dragged across a whole home screen would otherwise ask for
        // a bitmap several megabytes wide.
        val bitmap = PrecipitationGraph.render(chart(0.0, 3.0, 1.0), 600f, 400f, WidgetColors.graph)!!
        assertTrue(
            "expected the bitmap to be capped, got ${bitmap.width}x${bitmap.height}",
            bitmap.width * bitmap.height <= 400_000,
        )
        assertEquals(
            "the aspect ratio has to survive the clamp",
            600f / 400f,
            bitmap.width.toFloat() / bitmap.height,
            0.01f,
        )
    }

    @Test
    fun `a chart with nothing to plot draws nothing rather than an empty box`() {
        val single = PrecipitationChart(
            points = listOf(ChartPoint(0, 1.0)),
            ticks = emptyList(),
            resolution = ChartResolution.HOURLY,
        )
        assertNull(PrecipitationGraph.render(single, 252f, 60f, WidgetColors.graph))
        assertNull(
            PrecipitationGraph.render(
                PrecipitationChart(emptyList(), emptyList(), ChartResolution.HOURLY),
                252f, 60f, WidgetColors.graph,
            ),
        )
    }

    @Test
    fun `rain is painted and a dry hour is not`() {
        val width = 252f
        val height = 60f
        val wet = PrecipitationGraph.render(
            chart(6.0, 6.0, 6.0, 6.0, 6.0), width, height, WidgetColors.graph,
        )!!
        val dry = PrecipitationGraph.render(
            chart(0.0, 0.0, 0.0, 0.0, 0.0), width, height, WidgetColors.graph,
        )!!

        // Three quarters up the plot, and clear of the "now" line in the middle.
        val x = wet.width * 3 / 4
        val y = (PrecipitationGraph.RENDER_SCALE * 20f).toInt()

        val wetPixel = wet.getPixel(x, y)
        assertTrue("expected the fill under heavy rain", Color.alpha(wetPixel) > 0)
        assertTrue(
            "expected a blue fill but got #${Integer.toHexString(wetPixel)}",
            Color.blue(wetPixel) > Color.red(wetPixel),
        )

        assertEquals(
            "a dry forecast should leave the plot empty",
            0,
            Color.alpha(dry.getPixel(x, y)),
        )
    }

    @Test
    fun `the now line is drawn across the plot when there is history behind it`() {
        val graph = PrecipitationGraph.render(chart(0.0, 0.0, 0.0), 252f, 60f, WidgetColors.graph)!!
        assertTrue("the now line is missing", graph.redPixelsInLowerPlot() > 0)
    }

    @Test
    fun `a graph that begins at now is labelled but not ruled`() {
        // A radar nowcast starts at the current minute. The label still has to
        // say so, but a dashed line on the border would only clip against it.
        val fromNow = PrecipitationGraph.render(
            chart(0.0, 1.0, 2.0, startMinutes = 0, step = 5), 252f, 60f, WidgetColors.graph,
        )!!
        assertEquals(
            "a line on the left border is clipped, not informative",
            0,
            fromNow.redPixelsInLowerPlot(),
        )
        assertTrue("the now label is missing", fromNow.countsRedPixels() > 0)
    }

    @Test
    fun `a window entirely in the future does not claim to contain now`() {
        val later = PrecipitationGraph.render(
            chart(0.0, 0.0, 0.0, startMinutes = 60), 252f, 60f, WidgetColors.graph,
        )!!
        assertEquals(0, later.countsRedPixels())
    }

    /**
     * Red below the label strip: only the ruled line reaches down here, so
     * this tells the line apart from the word above it.
     */
    private fun android.graphics.Bitmap.redPixelsInLowerPlot(): Int =
        countsRedPixels(fromY = (height * 0.6f).toInt())

    /** The "now" line and its label are the only red in the palette. */
    private fun android.graphics.Bitmap.countsRedPixels(fromY: Int = 0): Int {
        var count = 0
        for (x in 0 until width) {
            for (y in fromY until height) {
                val pixel = getPixel(x, y)
                if (Color.alpha(pixel) > 64 &&
                    Color.red(pixel) > 150 &&
                    Color.red(pixel) > Color.blue(pixel) + 60
                ) {
                    count++
                }
            }
        }
        return count
    }
}
