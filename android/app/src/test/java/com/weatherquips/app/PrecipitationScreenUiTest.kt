package com.weatherquips.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.ui.precipitation.PrecipitationMapScreen
import com.weatherquips.app.ui.precipitation.RadarUiState
import com.weatherquips.app.ui.precipitation.TAG_BACK
import com.weatherquips.app.ui.precipitation.TAG_CURRENT_TIME
import com.weatherquips.app.ui.precipitation.TAG_PLAY_PAUSE
import com.weatherquips.app.ui.precipitation.TAG_RADAR_STATUS
import com.weatherquips.app.ui.precipitation.TAG_RECENTER
import com.weatherquips.app.ui.precipitation.TAG_TIMELINE
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The radar screen builds a real osmdroid MapView, so this also proves the map
 * can be created, laid out and torn down on the host without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PrecipitationScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val frames = listOf(
        RadarFrame("/v2/radar/1757000000", 1_757_000_000),
        RadarFrame("/v2/radar/1757000600", 1_757_000_600),
        RadarFrame("/v2/radar/nowcast_1", 1_757_001_200),
    )

    private fun render(state: RadarUiState, onTogglePlay: () -> Unit = {}, onBack: () -> Unit = {}) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                PrecipitationMapScreen(
                    coordinates = Coordinates(52.99, 6.56),
                    uiState = state,
                    onTogglePlay = onTogglePlay,
                    onSelectFrame = {},
                    onPauseForLifecycle = {},
                    onResumeForLifecycle = {},
                    tileUrlFor = {
                        "https://tilecache.rainviewer.com${it.path}/256/{z}/{x}/{y}/2/1_1.png"
                    },
                    onBack = onBack,
                )
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `map, timeline and controls all render`() {
        var toggled = false
        var backPressed = false
        render(
            RadarUiState(frames = frames, pastCount = 2, currentIndex = 0, isPlaying = true, isLoading = false),
            onTogglePlay = { toggled = true },
            onBack = { backPressed = true },
        )

        composeRule.onNodeWithTag(TAG_TIMELINE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CURRENT_TIME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RECENTER).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_PLAY_PAUSE).performClick()
        assertTrue(toggled)

        composeRule.onNodeWithTag(TAG_BACK).performClick()
        assertTrue(backPressed)

        capture("precipitation-map")
    }

    @Test
    fun `radar failure still leaves a usable screen`() {
        render(RadarUiState(isLoading = false, hasError = true))

        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BACK).assertIsDisplayed()
    }

    private fun capture(name: String) {
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
