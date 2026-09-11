package com.weatherquips.app

import com.weatherquips.app.ui.theme.temperatureColorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The temperature scale is anchored to actual degrees.
 *
 * Regression test for colour that meant nothing: the forecast used to be
 * coloured relative to whatever min and max happened to be on screen, so a
 * one-degree spread came out as a full blue-to-red swing, and the same
 * temperature took a different colour in each card.
 */
class TemperatureColorTest {

    @Test
    fun `the same temperature always gets the same colour`() {
        assertEquals(temperatureColorFor(17.0), temperatureColorFor(17.0))
        assertEquals(temperatureColorFor(-3.5), temperatureColorFor(-3.5))
    }

    @Test
    fun `neighbouring temperatures look alike`() {
        val sixteen = temperatureColorFor(16.0)
        val seventeen = temperatureColorFor(17.0)
        val difference = channelDistance(sixteen, seventeen)
        assertTrue("1C apart should be near-identical, was $difference", difference < 0.12f)
    }

    @Test
    fun `a real difference in temperature is visible`() {
        val difference = channelDistance(temperatureColorFor(2.0), temperatureColorFor(26.0))
        assertTrue("24C apart should be obvious, was $difference", difference > 0.5f)
    }

    @Test
    fun `colder is bluer and hotter is redder`() {
        val cold = temperatureColorFor(-5.0)
        val mild = temperatureColorFor(15.0)
        val hot = temperatureColorFor(30.0)

        assertTrue("cold should be blue-dominant", cold.blue > cold.red)
        assertTrue("hot should be red-dominant", hot.red > hot.blue)
        assertTrue("mild sits between", mild.blue < cold.blue && mild.red < hot.red)
    }

    @Test
    fun `the scale is continuous, with no jump between stops`() {
        // A badly specified stop table shows up as a visible band edge, so no
        // half-degree step may shift a channel noticeably.
        var celsius = -20.0
        var previous = temperatureColorFor(celsius)
        while (celsius <= 45.0) {
            celsius += 0.5
            val current = temperatureColorFor(celsius)
            val jump = channelDistance(previous, current)
            assertTrue("colour jumps at $celsius by $jump", jump < 0.05f)
            previous = current
        }
    }

    @Test
    fun `extremes are clamped rather than extrapolated`() {
        assertEquals(temperatureColorFor(-40.0), temperatureColorFor(-10.0))
        assertEquals(temperatureColorFor(60.0), temperatureColorFor(35.0))
    }

    private fun channelDistance(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
    ): Float = maxOf(
        kotlin.math.abs(a.red - b.red),
        kotlin.math.abs(a.green - b.green),
        kotlin.math.abs(a.blue - b.blue),
    )
}
