package com.weatherquips.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertFalse
import com.weatherquips.app.ui.home.TAG_RADAR_TAB
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.ui.home.HomePhase
import com.weatherquips.app.ui.home.HomeScreen
import com.weatherquips.app.ui.home.HomeUiState
import com.weatherquips.app.ui.home.LocationIssue
import com.weatherquips.app.ui.home.TAG_COLLAPSE
import com.weatherquips.app.ui.home.TAG_ERROR
import com.weatherquips.app.ui.home.TAG_EXPAND
import com.weatherquips.app.ui.home.TAG_LOADING
import com.weatherquips.app.ui.home.TAG_LOCATION
import com.weatherquips.app.ui.home.TAG_PERMISSION
import com.weatherquips.app.ui.home.TAG_QUOTE
import com.weatherquips.app.ui.home.TAG_STATE_MESSAGE
import com.weatherquips.app.ui.home.TAG_STATE_PRIMARY
import com.weatherquips.app.ui.home.TAG_STATE_TITLE
import com.weatherquips.app.ui.home.TAG_SUBTITLE
import com.weatherquips.app.ui.home.TAG_TEMPERATURE
import com.weatherquips.app.ui.home.UiError
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home screen rendering for each UI state. */
@RunWith(RobolectricTestRunner::class)
// A phone-sized screen so "displayed" means what it does on a real device.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        state: HomeUiState,
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenMap: (Coordinates) -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                HomeScreen(
                    uiState = state,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    onOpenSettings = onOpenSettings,
                    onOpenPrecipitationMap = onOpenMap,
                    onPermissionResult = {},
                )
            }
        }
    }

    private val successState = HomeUiState(
        phase = HomePhase.Success(TestWeather.sample(), Coordinates(52.99, 6.56)),
        settings = AppSettings(),
    )

    @Test
    fun `weather state shows the quip, subtitle, temperature and place`() {
        render(successState)

        composeRule.onNodeWithTag(TAG_QUOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SUBTITLE).assertIsDisplayed()
        composeRule.onNodeWithText("Clouds everywhere. No escape.").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TEMPERATURE).assertTextEquals("15°C")
        composeRule.onNodeWithTag(TAG_LOCATION).assertTextEquals("Assen")
    }

    @Test
    fun `the highlighted quote is announced without its markers`() {
        render(successState)

        // The screen reader hears the sentence, not the "**" syntax.
        composeRule
            .onNodeWithContentDescription("Clouds rolled in like they own the damn place")
            .assertIsDisplayed()
    }

    @Test
    fun `fahrenheit setting changes what the hero shows`() {
        render(
            successState.copy(settings = AppSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT)),
        )
        composeRule.onNodeWithTag(TAG_TEMPERATURE).assertTextEquals("59°F")
    }

    @Test
    fun `the detail panel can be pulled up from the collapsed handle`() {
        render(successState)
        composeRule.onNodeWithTag(TAG_EXPAND).assertIsDisplayed()
    }

    @Test
    fun `swiping up anywhere on the screen opens the detail panel`() {
        // Regression test: the panel used to be draggable only by its 56dp
        // handle, so a swipe on the body of the screen did nothing.
        render(successState)

        // Deliberately mid-screen: a swipe starting at the bottom edge would
        // land on the sheet's own drag handle and pass either way.
        composeRule.onRoot().performTouchInput {
            val startY = height * 0.6f
            swipeUp(startY = startY, endY = startY - height * 0.35f)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(TAG_COLLAPSE)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(TAG_COLLAPSE).assertIsDisplayed()
    }

    @Test
    fun `swiping right to left on the home screen opens the radar for this place`() {
        var opened: Coordinates? = null
        render(successState, onOpenMap = { opened = it })

        // Mid-screen, away from the edges: on a phone with gesture navigation
        // an edge swipe belongs to the system's back gesture.
        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = width * 0.8f, endX = width * 0.2f)
        }
        composeRule.waitForIdle()

        assertEquals(Coordinates(52.99, 6.56), opened)
    }

    @Test
    fun `swiping up opens the panel and does not open the radar`() {
        var opened = false
        render(successState, onOpenMap = { opened = true })

        composeRule.onRoot().performTouchInput {
            val startY = height * 0.6f
            // A real thumb drifts sideways while swiping up.
            swipe(
                start = Offset(width * 0.6f, startY),
                end = Offset(width * 0.45f, startY - height * 0.35f),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(TAG_COLLAPSE)).fetchSemanticsNodes().isNotEmpty()
        }

        assertFalse("an upward swipe opened the map", opened)
    }

    @Test
    fun `swiping left to right does nothing`() {
        var opened = false
        render(successState, onOpenMap = { opened = true })

        composeRule.onRoot().performTouchInput {
            swipeRight(startX = width * 0.2f, endX = width * 0.8f)
        }
        composeRule.waitForIdle()

        assertFalse(opened)
        composeRule.onNodeWithTag(TAG_EXPAND).assertIsDisplayed()
    }

    @Test
    fun `a short sideways brush does not open the radar`() {
        var opened = false
        render(successState, onOpenMap = { opened = true })

        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertFalse(opened)
    }

    @Test
    fun `the edge tab opens the radar for anyone who cannot swipe`() {
        var opened: Coordinates? = null
        render(successState, onOpenMap = { opened = it })

        composeRule.onNodeWithTag(TAG_RADAR_TAB).assertIsDisplayed().performClick()

        assertEquals(Coordinates(52.99, 6.56), opened)
    }

    @Test
    fun `the edge tab tells TalkBack what it does`() {
        render(successState)
        composeRule.onNodeWithContentDescription(
            "Open precipitation map. You can also swipe left.",
        ).assertExists()
    }

    @Test
    fun `a small drag does not open the panel by accident`() {
        render(successState)

        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -30f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_EXPAND).assertIsDisplayed()
    }

    @Test
    fun `loading state renders its skeleton`() {
        render(HomeUiState(phase = HomePhase.Loading))
        composeRule.onNodeWithTag(TAG_LOADING).assertIsDisplayed()
    }

    @Test
    fun `permission state asks for location and can retry`() {
        render(HomeUiState(phase = HomePhase.PermissionRequired(deniedOnce = false)))

        composeRule.onNodeWithTag(TAG_PERMISSION).assertIsDisplayed()
        composeRule.onNodeWithText("Where are you?").assertIsDisplayed()
        composeRule.onNodeWithText("Allow Location").assertIsDisplayed()
    }

    @Test
    fun `a denied permission explains how to recover`() {
        render(HomeUiState(phase = HomePhase.PermissionRequired(deniedOnce = true)))
        composeRule
            .onNodeWithText(
                "Location access was denied. Allow it in Android settings, or pick a city manually.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun `error state shows friendly copy and retries on tap`() {
        var retried = false
        render(HomeUiState(phase = HomePhase.Error(UiError.NO_INTERNET)), onRetry = { retried = true })

        composeRule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_STATE_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText("Oops…").assertIsDisplayed()
        composeRule.onNodeWithText("No internet connection.").assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_STATE_PRIMARY).performClick()
        assertTrue(retried)
    }

    @Test
    fun `errors never leak an exception class name`() {
        UiError.entries.forEach { error ->
            val message = androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(error.messageRes)
            assertTrue("technical text in: $message", !message.contains("Exception"))
            assertTrue("technical text in: $message", !message.contains("java."))
        }
    }

    @Test
    fun `location unavailable is its own message`() {
        render(HomeUiState(phase = HomePhase.LocationUnavailable(LocationIssue.DISABLED)))
        composeRule.onNodeWithTag(TAG_STATE_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("Location is turned off on this device.").assertIsDisplayed()
    }

    @Test
    fun `offline state labels the weather as saved`() {
        render(
            HomeUiState(
                phase = HomePhase.Offline(
                    weather = TestWeather.sample(),
                    coordinates = Coordinates(52.99, 6.56),
                    fetchedAtMillis = System.currentTimeMillis() - 3_600_000,
                    error = UiError.NO_INTERNET,
                ),
            ),
        )
        composeRule.onNodeWithTag(com.weatherquips.app.ui.home.TAG_STALE).assertIsDisplayed()
    }

    @Test
    fun `settings button reaches the settings screen`() {
        var opened = false
        render(successState, onOpenSettings = { opened = true })

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(opened)
    }

}
