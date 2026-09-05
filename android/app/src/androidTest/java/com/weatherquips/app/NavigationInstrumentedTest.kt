package com.weatherquips.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weatherquips.app.ui.home.TAG_STATE_SECONDARY
import com.weatherquips.app.ui.settings.TAG_SETTINGS_BACK
import com.weatherquips.app.ui.settings.TAG_SETTINGS_SCREEN
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device navigation and back behaviour.
 *
 * Without a granted location permission the app opens on the permission state,
 * which gives a deterministic entry point into Settings.
 */
@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsOpensAndSystemBackReturnsHome() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTagCount(TAG_STATE_SECONDARY) > 0
        }

        composeRule.onNodeWithTag(TAG_STATE_SECONDARY).performClick()
        composeRule.onNodeWithTag(TAG_SETTINGS_SCREEN).assertIsDisplayed()

        // System back (works the same for gesture and three-button navigation).
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_STATE_SECONDARY).assertIsDisplayed()
        assertTrue(!composeRule.activity.isFinishing)
    }

    @Test
    fun settingsBackButtonReturnsHome() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTagCount(TAG_STATE_SECONDARY) > 0
        }

        composeRule.onNodeWithTag(TAG_STATE_SECONDARY).performClick()
        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_STATE_SECONDARY).assertIsDisplayed()
    }

    @Test
    fun backOnHomeLeavesTheApp() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTagCount(TAG_STATE_SECONDARY) > 0
        }

        Espresso.pressBackUnconditionally()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing || composeRule.activity.isDestroyed)
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(
    tag: String,
): Int = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
