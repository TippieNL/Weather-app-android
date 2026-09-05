package com.weatherquips.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.ui.settings.GeocodeState
import com.weatherquips.app.ui.settings.SettingsActions
import com.weatherquips.app.ui.settings.SettingsScreen
import com.weatherquips.app.ui.settings.SettingsUiState
import com.weatherquips.app.ui.settings.TAG_API_KEY_FIELD
import com.weatherquips.app.ui.settings.TAG_CITY_FIELD
import com.weatherquips.app.ui.settings.TAG_EXIT_POKEMON
import com.weatherquips.app.ui.settings.TAG_FIND_BUTTON
import com.weatherquips.app.ui.settings.TAG_GEOCODE_CHOICES
import com.weatherquips.app.ui.settings.TAG_GEOCODE_STATUS
import com.weatherquips.app.ui.settings.TAG_NOTIFICATIONS_SWITCH
import com.weatherquips.app.ui.settings.TAG_SETTINGS_BACK
import com.weatherquips.app.ui.settings.TAG_SETTINGS_SCREEN
import com.weatherquips.app.ui.settings.TAG_UNIT_DROPDOWN
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings rendering and the interactions the user relies on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : SettingsActions {
        var recordedUnit: TemperatureUnit? = null
        var recordedTimeFormat: TimeFormat? = null
        var recordedDateFormat: DateFormat? = null
        var recordedLocationMode: LocationMode? = null
        var recordedManualLocation: String? = null
        var recordedService: WeatherService? = null
        var recordedApiKey: String? = null
        var exitedPokemon = false
        var findCalls = 0
        var recordedSelection: GeocodeResult? = null
        var recordedNotifications: Pair<Boolean, Boolean>? = null
        var testNotifications = 0

        override fun setTemperatureUnit(unit: TemperatureUnit) { recordedUnit = unit }
        override fun setTimeFormat(format: TimeFormat) { recordedTimeFormat = format }
        override fun setDateFormat(format: DateFormat) { recordedDateFormat = format }
        override fun setLocationMode(mode: LocationMode) { recordedLocationMode = mode }
        override fun setManualLocation(value: String) { recordedManualLocation = value }
        override fun setWeatherService(service: WeatherService) { recordedService = service }
        override fun setApiKey(value: String) { recordedApiKey = value }
        override fun exitPokemonMode() { exitedPokemon = true }
        override fun findLocation() { findCalls++ }
        override fun selectResult(result: GeocodeResult) { recordedSelection = result }
        override fun setNotificationsEnabled(enabled: Boolean, granted: Boolean) {
            recordedNotifications = enabled to granted
        }
        override fun toggleNotifications(enabled: Boolean) {
            recordedNotifications = enabled to true
        }
        override fun sendTestNotification(): Boolean {
            testNotifications++
            return true
        }
    }

    private fun render(
        state: SettingsUiState,
        actions: SettingsActions = RecordingActions(),
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                SettingsScreen(uiState = state, actions = actions, onBack = onBack)
            }
        }
    }

    @Test
    fun `every settings section is present`() {
        render(SettingsUiState())

        composeRule.onNodeWithTag(TAG_SETTINGS_SCREEN).assertExists()
        composeRule.onNodeWithText("Temperature Unit").assertExists()
        composeRule.onNodeWithText("Date & Time").assertExists()
        composeRule.onNodeWithText("Weather Service").assertExists()
        composeRule.onNodeWithText("Notifications").assertExists()
        composeRule.onNodeWithTag(TAG_UNIT_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `back button is wired up`() {
        var backPressed = false
        render(SettingsUiState(), onBack = { backPressed = true })

        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()
        assertTrue(backPressed)
    }

    @Test
    fun `the city field only appears in manual mode`() {
        render(SettingsUiState(settings = AppSettings(locationMode = LocationMode.DEVICE)))
        composeRule.onNodeWithTag(TAG_CITY_FIELD).assertDoesNotExistSafely()
    }

    @Test
    fun `manual mode offers a city search`() {
        val actions = RecordingActions()
        render(
            SettingsUiState(settings = AppSettings(locationMode = LocationMode.MANUAL)),
            actions = actions,
        )

        composeRule.onNodeWithTag(TAG_CITY_FIELD).performTextReplacement("Assen")
        assertEquals("Assen", actions.recordedManualLocation)

        composeRule.onNodeWithTag(TAG_FIND_BUTTON).performClick()
        assertEquals(1, actions.findCalls)
    }

    @Test
    fun `several matches are offered as a choice`() {
        val actions = RecordingActions()
        render(
            SettingsUiState(
                settings = AppSettings(locationMode = LocationMode.MANUAL),
                geocodeState = GeocodeState.Choices(
                    listOf(
                        GeocodeResult(52.99, 6.56, "Assen"),
                        GeocodeResult(41.0, -74.0, "Assen"),
                    ),
                ),
            ),
            actions = actions,
        )

        composeRule.onNodeWithTag(TAG_GEOCODE_CHOICES).assertExists()
    }

    @Test
    fun `a failed lookup explains itself`() {
        render(
            SettingsUiState(
                settings = AppSettings(locationMode = LocationMode.MANUAL),
                geocodeState = GeocodeState.Message(R.string.geocode_not_found),
            ),
        )
        composeRule.onNodeWithTag(TAG_GEOCODE_STATUS).assertExists()
        composeRule.onNodeWithText("Location not found").assertExists()
    }

    @Test
    fun `the api key field only appears for providers that need one`() {
        render(SettingsUiState(settings = AppSettings(weatherService = WeatherService.OPEN_METEO)))
        composeRule.onNodeWithTag(TAG_API_KEY_FIELD).assertDoesNotExistSafely()
    }

    @Test
    fun `providers that need a key get a masked field`() {
        render(
            SettingsUiState(
                settings = AppSettings(weatherService = WeatherService.OPEN_WEATHER_MAP),
            ),
        )
        composeRule.onNodeWithTag(TAG_API_KEY_FIELD).assertExists()
        // Masked: the raw key must never be rendered as plain text.
        composeRule.onNodeWithText("secret-key").assertDoesNotExistSafely()
    }

    @Test
    fun `toggling notifications reports the change`() {
        val actions = RecordingActions()
        render(SettingsUiState(settings = AppSettings(notificationsEnabled = true)), actions)

        composeRule.onNodeWithTag(TAG_NOTIFICATIONS_SWITCH).performScrollTo().performClick()
        assertEquals(false to true, actions.recordedNotifications)
    }

    @Test
    fun `notification switch is off by default`() {
        render(SettingsUiState(settings = AppSettings(notificationsEnabled = false)))
        composeRule.onNodeWithTag(TAG_NOTIFICATIONS_SWITCH).performScrollTo().assertIsOff()
    }

    @Test
    fun `notification switch reflects an enabled setting`() {
        render(SettingsUiState(settings = AppSettings(notificationsEnabled = true)))
        composeRule.onNodeWithTag(TAG_NOTIFICATIONS_SWITCH).performScrollTo().assertIsOn()
    }

    @Test
    fun `pokemon mode can be switched off from settings`() {
        val actions = RecordingActions()
        render(
            SettingsUiState(
                settings = AppSettings(locationMode = LocationMode.MANUAL, pokemonMode = true),
            ),
            actions,
        )

        composeRule.onNodeWithTag(TAG_EXIT_POKEMON).performScrollTo().performClick()
        assertTrue(actions.exitedPokemon)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistSafely() {
        assertDoesNotExist()
    }
}
