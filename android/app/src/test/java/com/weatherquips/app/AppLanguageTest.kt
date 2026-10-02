package com.weatherquips.app

import android.app.LocaleManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.locale.AppLanguage
import com.weatherquips.app.locale.AppLocale
import com.weatherquips.app.locale.LanguageController
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationScheduler
import com.weatherquips.app.ui.settings.SettingsScreen
import com.weatherquips.app.ui.settings.SettingsViewModel
import com.weatherquips.app.ui.settings.TAG_LANGUAGE_DROPDOWN
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Picking a language has to reach the platform.
 *
 * Regression test: the settings screen called `setLanguage`, but the view
 * model never implemented it, so the interface's do-nothing default answered
 * and the language never changed. Each layer is covered here — the screen,
 * the view model, and the platform call on both sides of Android 13.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLanguageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val originalDefault = Locale.getDefault()

    @After
    fun restore() {
        AppLocale.set(context, AppLanguage.SYSTEM)
        Locale.setDefault(originalDefault)
    }

    /** Records what reached the platform, and answers as a pre-13 phone would. */
    private class RecordingController(
        var language: AppLanguage = AppLanguage.SYSTEM,
        private val mustRecreate: Boolean = true,
    ) : LanguageController {
        val applied = mutableListOf<AppLanguage>()
        override fun current() = language
        override fun set(language: AppLanguage): Boolean {
            applied += language
            this.language = language
            return mustRecreate
        }
    }

    private class StubGeocoding : GeocodingRepository {
        override suspend fun search(query: String) = emptyList<GeocodeResult>()
        override suspend fun reverseGeocode(coordinates: com.weatherquips.app.domain.model.Coordinates) = ""
    }

    private fun viewModel(controller: LanguageController) = SettingsViewModel(
        settingsRepository = FakeSettingsRepository(),
        geocodingRepository = StubGeocoding(),
        notificationHelper = NotificationHelper(context),
        precipitationScheduler = PrecipitationScheduler(context),
        languageController = controller,
    )

    // --- the screen, through the real view model ---------------------------

    @Test
    fun `choosing a language in settings reaches the platform`() {
        val controller = RecordingController()
        val viewModel = viewModel(controller)
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                val state = viewModel.uiState.collectAsState().value
                SettingsScreen(uiState = state, actions = viewModel, onBack = {})
            }
        }

        composeRule.onNodeWithTag(TAG_LANGUAGE_DROPDOWN).performClick()
        composeRule.onNodeWithText("Nederlands").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(AppLanguage.DUTCH), controller.applied)
        assertEquals(AppLanguage.DUTCH, viewModel.uiState.value.language)
    }

    // --- the view model ----------------------------------------------------

    @Test
    fun `the view model applies the choice and says whether to rebuild`() {
        val old = RecordingController(mustRecreate = true)
        assertTrue(viewModel(old).setLanguage(AppLanguage.DUTCH))
        assertEquals(listOf(AppLanguage.DUTCH), old.applied)

        // Android 13+: the platform rebuilds the screen itself.
        val modern = RecordingController(mustRecreate = false)
        assertFalse(viewModel(modern).setLanguage(AppLanguage.ENGLISH))
        assertEquals(listOf(AppLanguage.ENGLISH), modern.applied)
    }

    @Test
    fun `choosing the language already in use does nothing`() {
        val controller = RecordingController(language = AppLanguage.DUTCH)
        assertFalse(viewModel(controller).setLanguage(AppLanguage.DUTCH))
        assertTrue(controller.applied.isEmpty())
    }

    @Test
    fun `a change made in system settings shows up on refresh`() {
        val controller = RecordingController()
        val viewModel = viewModel(controller)
        controller.language = AppLanguage.DUTCH
        viewModel.refreshLanguage()
        assertEquals(AppLanguage.DUTCH, viewModel.uiState.value.language)
    }

    // --- the platform ------------------------------------------------------

    @Test
    fun `on Android 13 and later the platform owns the choice`() {
        assertFalse("13+ rebuilds the activity itself", AppLocale.set(context, AppLanguage.DUTCH))
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        assertEquals("nl", locales.toLanguageTags())
        assertEquals(AppLanguage.DUTCH, AppLocale.current(context))

        AppLocale.set(context, AppLanguage.SYSTEM)
        assertTrue(context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
        assertEquals(AppLanguage.SYSTEM, AppLocale.current(context))
    }

    @Config(sdk = [28])
    @Test
    fun `below Android 13 the activity is rebuilt in the chosen language`() {
        assertTrue("the caller must recreate", AppLocale.set(context, AppLanguage.DUTCH))
        assertEquals(AppLanguage.DUTCH, AppLocale.current(context))

        val wrapped = AppLocale.wrap(context)
        assertEquals("Instellingen", wrapped.getString(R.string.settings))
        assertEquals("nl", Locale.getDefault().language)
    }

    @Config(sdk = [28], qualifiers = "en")
    @Test
    fun `going back to the system language undoes the old default`() {
        AppLocale.set(context, AppLanguage.DUTCH)
        AppLocale.wrap(context)
        assertEquals("nl", Locale.getDefault().language)

        AppLocale.set(context, AppLanguage.SYSTEM)
        val wrapped = AppLocale.wrap(context)
        assertEquals("Settings", wrapped.getString(R.string.settings))
        assertEquals("en", Locale.getDefault().language)
    }
}
