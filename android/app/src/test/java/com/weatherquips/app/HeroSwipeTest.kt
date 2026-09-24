package com.weatherquips.app

import com.weatherquips.app.ui.home.HeroSwipe
import com.weatherquips.app.ui.home.rubberBand
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The geometry of a swipe on the home screen, without synthesising touches. */
class HeroSwipeTest {

    private val expand = 150f
    private val map = 200f

    private fun classify(dx: Float, dy: Float) = HeroSwipe.classify(dx, dy, expand, map)

    @Test
    fun `a clean right-to-left swipe opens the map`() {
        assertEquals(HeroSwipe.OPEN_MAP, classify(dx = -400f, dy = 0f))
    }

    @Test
    fun `a clean upward swipe opens the panel`() {
        assertEquals(HeroSwipe.EXPAND, classify(dx = 0f, dy = -400f))
    }

    @Test
    fun `an upward swipe that drifts sideways is still an upward swipe`() {
        // The thumb arcs; that must not send someone to the radar.
        assertEquals(HeroSwipe.EXPAND, classify(dx = -120f, dy = -400f))
    }

    @Test
    fun `a sideways swipe with some vertical wobble still opens the map`() {
        assertEquals(HeroSwipe.OPEN_MAP, classify(dx = -400f, dy = -80f))
    }

    @Test
    fun `a diagonal swipe is ambiguous and does nothing`() {
        // Neither axis dominates enough to be sure.
        assertNull(classify(dx = -300f, dy = -250f))
    }

    @Test
    fun `left to right is not a map swipe`() {
        assertNull(classify(dx = 400f, dy = 0f))
    }

    @Test
    fun `short swipes in either direction do nothing`() {
        assertNull(classify(dx = -150f, dy = 0f))
        assertNull(classify(dx = 0f, dy = -100f))
    }

    @Test
    fun `swiping down does nothing`() {
        assertNull(classify(dx = 0f, dy = 400f))
    }

    @Test
    fun `the rubber band follows closely at first and never passes its limit`() {
        val limit = 100f
        assertEquals(0f, rubberBand(0f, limit), 0.0001f)
        // Near 1:1 for a small drag...
        assertTrue(rubberBand(10f, limit) > 4f)
        // ...and asymptotic, however far the finger goes.
        listOf(50f, 200f, 1_000f, 100_000f).forEach {
            assertTrue("passed the limit at $it", rubberBand(it, limit) < limit)
        }
        var previous = 0f
        for (d in 1..2_000 step 7) {
            val r = rubberBand(d.toFloat(), limit)
            assertTrue("went backwards at $d", r >= previous)
            previous = r
        }
    }
}
