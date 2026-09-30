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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.ui.home.HomePhase
import com.weatherquips.app.ui.home.HomeUiState
import com.weatherquips.app.ui.home.TAG_DAILY
import com.weatherquips.app.ui.home.TAG_DAY_RAIN
import com.weatherquips.app.ui.home.TAG_DAY_RAIN_CHART
import com.weatherquips.app.ui.home.TAG_DAY_ROW_PREFIX
import com.weatherquips.app.ui.home.TAG_DETAIL_PANEL
import com.weatherquips.app.ui.home.TAG_DETAIL_RANGE
import com.weatherquips.app.ui.home.TAG_DETAIL_STATS
import com.weatherquips.app.ui.home.TAG_DETAIL_TEMP
import com.weatherquips.app.ui.home.TAG_HOURLY
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

    private fun render() {
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
        composeRule.onNodeWithContentDescription("tomorrow, 12/09: low 11°, high 20°").assertExists()
        composeRule.onNodeWithContentDescription("thursday, 17/09: low 12°, high 18°").assertExists()
        // A day that might actually rain says so before it is opened.
        composeRule.onNodeWithContentDescription("sunday, 13/09: low 15°, high 19°, 80% chance of rain")
            .assertExists()
    }

    // --- tapping a day for its rain -----------------------------------------

    @Test
    fun `nothing is open until a day is tapped`() {
        render()
        composeRule.onNodeWithTag(TAG_DAY_RAIN).assertDoesNotExist()
    }

    @Test
    fun `tapping a wet day shows how much, how likely, when and how hard`() {
        render()
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-13").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_DAY_RAIN).assertExists()
        composeRule.onNodeWithText("6.4 mm expected").assertExists()
        composeRule.onNodeWithText("80% chance").assertExists()
        composeRule.onNodeWithText("Mostly between 14:00 and 19:00").assertExists()
        composeRule.onNodeWithText("Heaviest around 16:00: heavy, 2.6 mm/h").assertExists()
        composeRule.onNodeWithText("Wet for about 5 hours").assertExists()
        composeRule.onNodeWithTag(TAG_DAY_RAIN_CHART).assertExists()
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-13").assertIsSelected()
    }

    @Test
    fun `a dry day says so plainly`() {
        render()
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-12").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Looks dry all day").assertExists()
        composeRule.onNodeWithTag(TAG_DAY_RAIN_CHART).assertDoesNotExist()
    }

    @Test
    fun `a day the provider said nothing about is not called dry`() {
        render()
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-17").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No rain forecast for this day").assertExists()
        composeRule.onNodeWithText("Looks dry all day").assertDoesNotExist()
    }

    @Test
    fun `choosing another day moves the selection, and tapping again closes it`() {
        render()
        val sunday = "${TAG_DAY_ROW_PREFIX}2026-09-13"
        val tomorrow = "${TAG_DAY_ROW_PREFIX}2026-09-12"

        composeRule.onNodeWithTag(sunday).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tomorrow).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(tomorrow).assertIsSelected()
        composeRule.onNodeWithTag(sunday).assertIsNotSelected()
        composeRule.onNodeWithText("6.4 mm expected").assertDoesNotExist()
        composeRule.onNodeWithText("Looks dry all day").assertExists()

        composeRule.onNodeWithTag(tomorrow).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_DAY_RAIN).assertDoesNotExist()
        composeRule.onNodeWithTag(tomorrow).assertIsNotSelected()
    }

    @Test
    fun `the open day survives the activity being recreated`() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                WeatherDetailPanel(
                    uiState = HomeUiState(
                        phase = HomePhase.Success(weather, Coordinates(52.99, 6.56)),
                        settings = AppSettings(),
                    ),
                    weather = weather,
                    staleSinceMillis = null,
                    onRefresh = {},
                )
            }
        }
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-13").performScrollTo().performClick()
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("6.4 mm expected").assertExists()
    }

    @Test
    fun `a refresh that drops the open day closes it instead of opening another`() {
        var current by androidx.compose.runtime.mutableStateOf(weather)
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                WeatherDetailPanel(
                    uiState = HomeUiState(
                        phase = HomePhase.Success(current, Coordinates(52.99, 6.56)),
                        settings = AppSettings(),
                    ),
                    weather = current,
                    staleSinceMillis = null,
                    onRefresh = {},
                )
            }
        }
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-13").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The week rolls over: Sunday is now today and leaves the list.
        current = weather.copy(dailyForecast = weather.dailyForecast.drop(2))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_DAY_RAIN).assertDoesNotExist()
    }

    @Test
    fun `the week is named in the app's language`() {
        val dutch = android.content.res.Configuration(composeRule.activity.resources.configuration)
            .apply { setLocale(java.util.Locale.forLanguageTag("nl")) }
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides dutch,
                androidx.compose.ui.platform.LocalContext provides
                    composeRule.activity.createConfigurationContext(dutch),
            ) {
                WeatherQuipsTheme(darkTheme = false) {
                    WeatherDetailPanel(
                        uiState = HomeUiState(
                            phase = HomePhase.Success(weather, Coordinates(52.99, 6.56)),
                            settings = AppSettings(),
                        ),
                        weather = weather,
                        staleSinceMillis = null,
                        onRefresh = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("morgen, 12/09: laag 11°, hoog 20°").assertExists()
        composeRule.onNodeWithContentDescription("zondag, 13/09: laag 15°, hoog 19°, 80% kans op regen")
            .assertExists()
        composeRule.onNodeWithText("sunday", useUnmergedTree = true).assertDoesNotExist()

        // And the opened day speaks Dutch too, decimal comma included.
        composeRule.onNodeWithTag("${TAG_DAY_ROW_PREFIX}2026-09-13").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("6,4 mm verwacht").assertExists()
        composeRule.onNodeWithText("Vooral tussen 14:00 en 19:00").assertExists()
        composeRule.onNodeWithText("Het hevigst rond 16:00: zwaar, 2,6 mm/u").assertExists()
    }

    @Test
    fun `the panel still shows the headline temperature`() {
        render()

        composeRule.onNodeWithTag(TAG_DETAIL_PANEL).assertExists()
        composeRule.onNodeWithTag(TAG_DETAIL_TEMP).assertIsDisplayed()
        composeRule.onNodeWithText("17°C").assertIsDisplayed()
    }

    @Test
    fun `the map is reached from the home screen, not from the bottom of the panel`() {
        render()
        composeRule.onNodeWithText("precipitation map").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextValue(
        value: String,
    ): List<androidx.compose.ui.semantics.SemanticsNode> =
        onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes()
}
