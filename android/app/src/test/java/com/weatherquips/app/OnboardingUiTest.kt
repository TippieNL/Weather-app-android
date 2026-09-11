package com.weatherquips.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.weatherquips.app.ui.onboarding.LocationChoice
import com.weatherquips.app.ui.onboarding.OnboardingScreen
import com.weatherquips.app.ui.onboarding.OnboardingUiState
import com.weatherquips.app.ui.onboarding.TAG_ALLOW_LOCATION
import com.weatherquips.app.ui.onboarding.TAG_LOCATION_RESULT
import com.weatherquips.app.ui.onboarding.TAG_MANUAL_LOCATION
import com.weatherquips.app.ui.onboarding.TAG_NEXT
import com.weatherquips.app.ui.onboarding.TAG_ONBOARDING
import com.weatherquips.app.ui.onboarding.TAG_PAGE_LOCATION
import com.weatherquips.app.ui.onboarding.TAG_PAGE_WELCOME
import com.weatherquips.app.ui.onboarding.TAG_SKIP
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var finishedManual: Boolean? = null

    private fun render(state: OnboardingUiState = OnboardingUiState()) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                OnboardingScreen(
                    uiState = state,
                    onLocationResult = {},
                    onFinish = { manual -> finishedManual = manual },
                )
            }
        }
    }

    @Test
    fun `it opens on the welcome page, in the app's voice`() {
        render()

        composeRule.onNodeWithTag(TAG_ONBOARDING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PAGE_WELCOME).assertIsDisplayed()
        // The markers are for colouring a word, never for the user to read.
        composeRule
            .onNodeWithContentDescription("Every other weather app is polite about it")
            .assertExists()
    }

    @Test
    fun `the introduction can be skipped outright`() {
        render()
        composeRule.onNodeWithTag(TAG_SKIP).performClick()
        assertEquals(false, finishedManual)
    }

    @Test
    fun `paging through reaches the location page`() {
        render()

        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_PAGE_LOCATION).assertExists()
        // The last page offers the permission, and skipping is gone by then.
        composeRule.onNodeWithTag(TAG_ALLOW_LOCATION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SKIP).assertDoesNotExist()
    }

    @Test
    fun `the location page explains itself before the system asks`() {
        render()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription("We need to know where you are suffering")
            .assertExists()
        composeRule.onNodeWithText(
            "Location access is how the app knows which sky to insult. " +
                "Precise pins your neighbourhood, approximate settles for your region — " +
                "honestly, both are fine for weather. " +
                "Your coordinates go to the weather service and nowhere else.",
        ).assertExists()
    }

    @Test
    fun `declining location leads to the city search instead of a dead end`() {
        render()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_MANUAL_LOCATION).performClick()
        assertEquals(true, finishedManual)
    }

    @Test
    fun `a refused permission gets an answer, not silence`() {
        render(OnboardingUiState(locationChoice = LocationChoice.DECLINED))
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_NEXT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LOCATION_RESULT).assertExists()
        composeRule
            .onNodeWithText("Fine. Keep your secrets — pick a city in settings instead.")
            .assertExists()
    }

    @Test
    fun `granting location ends the introduction without another tap`() {
        render(OnboardingUiState(locationChoice = LocationChoice.GRANTED))
        composeRule.waitForIdle()
        assertTrue("should have finished on its own", finishedManual == false)
    }
}
