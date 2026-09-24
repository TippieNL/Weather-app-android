package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.ui.components.Scene
import com.weatherquips.app.ui.components.panelStage
import com.weatherquips.app.ui.components.sceneFor
import com.weatherquips.app.ui.home.introStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic behind every staggered entrance and the weather scenes. */
class MotionTimingTest {

    @Test
    fun `every staged element starts hidden and finishes fully shown`() {
        (0..3).forEach { index ->
            assertEquals("element $index was visible before the entrance", 0f, introStage(0f, index), 0.0001f)
            assertEquals("element $index never landed", 1f, introStage(1f, index), 0.0001f)
        }
    }

    @Test
    fun `later elements are never ahead of earlier ones`() {
        // A stagger that let the subtitle overtake the quote would read as a glitch.
        var t = 0f
        while (t <= 1f) {
            (1..3).forEach { index ->
                assertTrue(
                    "element $index overtook ${index - 1} at $t",
                    introStage(t, index) <= introStage(t, index - 1) + 0.0001f,
                )
            }
            t += 0.01f
        }
    }

    @Test
    fun `a stage only ever moves forward`() {
        var previous = -1f
        var t = 0f
        while (t <= 1f) {
            val stage = panelStage(t, delayMillis = 200)
            assertTrue("went backwards at $t", stage >= previous - 0.0001f)
            previous = stage
            t += 0.005f
        }
    }

    @Test
    fun `the whole panel has landed by the end of its reveal`() {
        // The latest thing to start is the last row of the week.
        val lastRowDelay = 2 * 90 + 6 * 45
        assertEquals(1f, panelStage(1f, delayMillis = lastRowDelay), 0.0001f)
    }

    @Test
    fun `every condition has a scene, and night is not drawn with a sun`() {
        WeatherCondition.entries.forEach { condition ->
            listOf(true, false).forEach { isDay ->
                assertTrue(sceneFor(condition, isDay) != Scene.NONE)
            }
        }
        assertEquals(Scene.SUN, sceneFor(WeatherCondition.CLEAR, isDay = true))
        assertEquals(Scene.STARS, sceneFor(WeatherCondition.CLEAR, isDay = false))
        assertEquals(Scene.CLOUDY_NIGHT, sceneFor(WeatherCondition.CLOUDY, isDay = false))
    }
}
