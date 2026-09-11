package com.weatherquips.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.ui.home.HomePhase
import com.weatherquips.app.ui.home.HomeUiState
import com.weatherquips.app.ui.home.TAG_DAILY
import com.weatherquips.app.ui.home.TAG_DETAIL_PANEL
import com.weatherquips.app.ui.home.TAG_DETAIL_RANGE
import com.weatherquips.app.ui.home.TAG_DETAIL_STATS
import com.weatherquips.app.ui.home.TAG_DETAIL_TEMP
import com.weatherquips.app.ui.home.TAG_HOURLY
import com.weatherquips.app.ui.home.TAG_MAP_CARD
import com.weatherquips.app.ui.home.WeatherDetailPanel
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reworked detail panel: what it shows and that nothing is duplicated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
class DetailPanelUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val weather = TestWeather.assenEvening()

    private fun render(onOpenMap: () -> Unit = {}) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WeatherDetailPanel(
                        uiState = HomeUiState(
                            phase = HomePhase.Success(weather, Coordinates(52.99, 6.56)),
                            settings = AppSettings(),
                        ),
                        weather = weather,
                        staleSinceMillis = null,
                        onRefresh = {},
                        onOpenPrecipitationMap = onOpenMap,
                    )
                }
            }
        }
    }

    @Test
    fun `all six stats are shown with their units`() {
        render()

        composeRule.onNodeWithTag(TAG_DETAIL_STATS).assertExists()
        composeRule.onNodeWithContentDescription("feels like: 18°C").assertExists()
        composeRule.onNodeWithContentDescription("rain chance: 96%").assertExists()
        composeRule.onNodeWithContentDescription("humidity: 94%").assertExists()
        composeRule.onNodeWithContentDescription("wind: 7 km/h").assertExists()
        composeRule.onNodeWithContentDescription("uv index: 0.2").assertExists()
        // Pressure used to be a bare number with no unit at all.
        composeRule.onNodeWithContentDescription("pressure: 1018 hPa").assertExists()
    }

    @Test
    fun `the range bar reports the low, the high and where now sits`() {
        render()
        composeRule.onNodeWithTag(TAG_DETAIL_RANGE).assertExists()
        composeRule
            .onNodeWithContentDescription("Low 12°C, high 17°C, now 17°C")
            .assertExists()
    }

    @Test
    fun `the high and low are not repeated below the range bar`() {
        render()

        // The old layout printed the same two figures twice: once beside the
        // bar and again as arrow chips underneath.
        assertTrue(
            "12° appears more than once",
            composeRule.onAllNodesWithTextValue("12°").size <= 1,
        )
        assertTrue(
            "17° appears more than once",
            composeRule.onAllNodesWithTextValue("17°").size <= 1,
        )
    }

    @Test
    fun `the hourly strip shows rain chance per hour`() {
        render()
        composeRule.onNodeWithTag(TAG_HOURLY).assertExists()
        composeRule.onNodeWithContentDescription("now: 17°, 96% chance of rain").assertExists()
        composeRule.onNodeWithContentDescription("23: 16°, 25% chance of rain").assertExists()
    }

    @Test
    fun `every forecast day is listed with its range`() {
        render()
        composeRule.onNodeWithTag(TAG_DAILY).assertExists()
        composeRule.onNodeWithContentDescription("tomorrow: 11° to 20°").assertExists()
        composeRule.onNodeWithContentDescription("thursday: 12° to 18°").assertExists()
    }

    @Test
    fun `the panel still shows the headline temperature and the map link`() {
        var mapOpened = false
        render(onOpenMap = { mapOpened = true })

        composeRule.onNodeWithTag(TAG_DETAIL_PANEL).assertExists()
        composeRule.onNodeWithTag(TAG_DETAIL_TEMP).assertIsDisplayed()
        composeRule.onNodeWithText("17°C").assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_MAP_CARD).performClick()
        assertTrue(mapOpened)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextValue(
        value: String,
    ): List<androidx.compose.ui.semantics.SemanticsNode> =
        onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes()
}
