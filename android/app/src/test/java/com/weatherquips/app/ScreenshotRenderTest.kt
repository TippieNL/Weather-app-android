package com.weatherquips.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.ui.home.HomePhase
import com.weatherquips.app.ui.home.HomeScreen
import com.weatherquips.app.ui.home.HomeUiState
import com.weatherquips.app.ui.settings.SettingsScreen
import com.weatherquips.app.ui.settings.SettingsUiState
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Renders the main screens to PNGs so the visual result can be reviewed against
 * the original Weather-Quips design. Output lands in build/screenshots/.
 *
 * Not an assertion suite — it fails only if a screen cannot be drawn at all,
 * which by itself catches layout crashes in light and dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The hero icon animates forever, so the test clock is driven manually —
     * otherwise the capture would wait for an idle state that never arrives.
     */
    private fun settleAndCapture(name: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500)
        save(name)
    }

    private fun save(name: String) {
        // Drawing the decor view directly works under Robolectric's native
        // graphics; captureToImage() waits for a real frame callback that a
        // host JVM never delivers.
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun renderHome(state: HomeUiState, dark: Boolean) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        uiState = state,
                        onRefresh = {},
                        onRetry = {},
                        onOpenSettings = {},
                        onOpenPrecipitationMap = {},
                        onPermissionResult = {},
                    )
                }
            }
        }
    }

    private val weatherState = HomeUiState(
        phase = HomePhase.Success(TestWeather.sample(), Coordinates(52.99, 6.56)),
        settings = AppSettings(),
    )

    @Test
    fun homeLight() {
        renderHome(weatherState, dark = false)
        settleAndCapture("home-light")
    }

    @Test
    fun homeDark() {
        renderHome(weatherState, dark = true)
        settleAndCapture("home-dark")
    }

    @Test
    fun permissionState() {
        renderHome(HomeUiState(phase = HomePhase.PermissionRequired(false)), dark = false)
        settleAndCapture("home-permission")
    }

    @Test
    fun errorState() {
        renderHome(
            HomeUiState(phase = HomePhase.Error(com.weatherquips.app.ui.home.UiError.NO_INTERNET)),
            dark = false,
        )
        settleAndCapture("home-error")
    }

    @Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
    @Test
    fun detailPanelLight() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    com.weatherquips.app.ui.home.WeatherDetailPanel(
                        uiState = weatherState,
                        weather = TestWeather.assenEvening(),
                        staleSinceMillis = null,
                        onRefresh = {},
                        onOpenPrecipitationMap = {},
                    )
                }
            }
        }
        settleAndCapture("detail-panel-light")
    }

    @Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
    @Test
    fun detailPanelDark() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    com.weatherquips.app.ui.home.WeatherDetailPanel(
                        uiState = weatherState,
                        weather = TestWeather.assenEvening(),
                        staleSinceMillis = null,
                        onRefresh = {},
                        onOpenPrecipitationMap = {},
                    )
                }
            }
        }
        settleAndCapture("detail-panel-dark")
    }

    /** Every condition, day row over night row, to check the pairs differ sensibly. */
    @Test
    fun weatherIconMatrix() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        listOf(true, false).forEach { isDay ->
                            androidx.compose.material3.Text(if (isDay) "day" else "night")
                            androidx.compose.foundation.layout.Row {
                                com.weatherquips.app.domain.model.WeatherCondition.entries
                                    .forEach { condition ->
                                        com.weatherquips.app.ui.components.WeatherGlyph(
                                            condition = condition,
                                            isDay = isDay,
                                            size = 40.dp,
                                        )
                                    }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
        settleAndCapture("icon-matrix")
    }

    @Test
    fun pokemonMode() {
        renderHome(
            HomeUiState(
                phase = HomePhase.Success(
                    TestWeather.sample(condition = com.weatherquips.app.domain.model.WeatherCondition.STORMY),
                    Coordinates(34.68, 138.95),
                ),
                settings = AppSettings(
                    locationMode = com.weatherquips.app.domain.model.LocationMode.MANUAL,
                    pokemonMode = true,
                ),
                pokemonQuote = "Pikachu seems unusually excited about today's forecast.",
                pokemonSubtitle = "The Power Plant is buzzing with energy.",
                pokemonSilhouette = com.weatherquips.app.domain.quotes.PokemonQuips.Silhouette.SPARK,
            ),
            dark = false,
        )
        settleAndCapture("pokemon-mode")
    }

    /** All four creatures side by side, to check they read as a set. */
    @Test
    fun pokemonSilhouettes() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        com.weatherquips.app.ui.components.PokeballIcon(size = 180.dp)
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement =
                                androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            com.weatherquips.app.domain.quotes.PokemonQuips.Silhouette.entries
                                .forEach { silhouette ->
                                    com.weatherquips.app.ui.components.PokemonSilhouette(
                                        silhouette = silhouette,
                                        size = 96.dp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    )
                                }
                        }
                    }
                }
            }
        }
        settleAndCapture("pokemon-parts")
    }

    @Test
    fun onboardingWelcome() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                com.weatherquips.app.ui.onboarding.OnboardingScreen(
                    uiState = com.weatherquips.app.ui.onboarding.OnboardingUiState(),
                    onLocationResult = {},
                    onFinish = {},
                )
            }
        }
        settleAndCapture("onboarding-welcome")
    }

    @Test
    fun onboardingLocation() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                com.weatherquips.app.ui.onboarding.OnboardingScreen(
                    uiState = com.weatherquips.app.ui.onboarding.OnboardingUiState(),
                    onLocationResult = {},
                    onFinish = {},
                )
            }
        }
        composeRule.onNodeWithTag(com.weatherquips.app.ui.onboarding.TAG_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(com.weatherquips.app.ui.onboarding.TAG_NEXT).performClick()
        composeRule.waitForIdle()
        settleAndCapture("onboarding-location")
    }

    @Test
    fun settingsLight() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    actions = NoOpSettingsActions,
                    onBack = {},
                )
            }
        }
        settleAndCapture("settings-light")
    }
}

private object NoOpSettingsActions : com.weatherquips.app.ui.settings.SettingsActions {
    override fun setTemperatureUnit(unit: com.weatherquips.app.domain.model.TemperatureUnit) = Unit
    override fun setTimeFormat(format: com.weatherquips.app.domain.model.TimeFormat) = Unit
    override fun setDateFormat(format: com.weatherquips.app.domain.model.DateFormat) = Unit
    override fun setLocationMode(mode: com.weatherquips.app.domain.model.LocationMode) = Unit
    override fun setManualLocation(value: String) = Unit
    override fun setWeatherService(service: com.weatherquips.app.domain.model.WeatherService) = Unit
    override fun setApiKey(value: String) = Unit
    override fun exitPokemonMode() = Unit
    override fun findLocation() = Unit
    override fun selectResult(result: com.weatherquips.app.domain.model.GeocodeResult) = Unit
    override fun setNotificationsEnabled(enabled: Boolean, granted: Boolean) = Unit
    override fun toggleNotifications(enabled: Boolean) = Unit
    override fun sendTestNotification(): Boolean = true
}
