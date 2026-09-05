package com.weatherquips.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.ui.precipitation.PrecipitationMapScreen
import com.weatherquips.app.ui.precipitation.RadarUiState
import com.weatherquips.app.ui.precipitation.TAG_BACK
import com.weatherquips.app.ui.precipitation.TAG_CURRENT_TIME
import com.weatherquips.app.ui.precipitation.TAG_MAP
import com.weatherquips.app.ui.precipitation.TAG_PLAY_PAUSE
import com.weatherquips.app.ui.precipitation.TAG_RADAR_STATUS
import com.weatherquips.app.ui.precipitation.TAG_TIMELINE
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The radar screen needs a real MapView, so it runs as an instrumentation test.
 * The radar data itself is supplied directly — no network required.
 */
@RunWith(AndroidJUnit4::class)
class PrecipitationScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val frames = listOf(
        RadarFrame("/v2/radar/1757000000", 1_757_000_000),
        RadarFrame("/v2/radar/1757000600", 1_757_000_600),
        RadarFrame("/v2/radar/nowcast_1", 1_757_001_200),
    )

    @Test
    fun mapTimelineAndControlsRender() {
        var paused = false
        var backPressed = false

        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                PrecipitationMapScreen(
                    coordinates = Coordinates(52.99, 6.56),
                    uiState = RadarUiState(
                        frames = frames,
                        pastCount = 2,
                        currentIndex = 0,
                        isPlaying = true,
                        isLoading = false,
                    ),
                    onTogglePlay = { paused = true },
                    onSelectFrame = {},
                    onPauseForLifecycle = {},
                    onResumeForLifecycle = {},
                    tileUrlFor = { "https://tilecache.rainviewer.com${it.path}/256/{z}/{x}/{y}/2/1_1.png" },
                    onBack = { backPressed = true },
                )
            }
        }

        composeRule.onNodeWithTag(TAG_MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TIMELINE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CURRENT_TIME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_PLAY_PAUSE).performClick()
        assertTrue(paused)

        composeRule.onNodeWithTag(TAG_BACK).performClick()
        assertTrue(backPressed)
    }

    @Test
    fun radarErrorStateIsShownWithoutCrashing() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                PrecipitationMapScreen(
                    coordinates = Coordinates(0.0, 0.0),
                    uiState = RadarUiState(isLoading = false, hasError = true),
                    onTogglePlay = {},
                    onSelectFrame = {},
                    onPauseForLifecycle = {},
                    onResumeForLifecycle = {},
                    tileUrlFor = { "" },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MAP).assertIsDisplayed()
    }
}
