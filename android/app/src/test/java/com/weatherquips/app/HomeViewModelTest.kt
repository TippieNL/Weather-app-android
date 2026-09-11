package com.weatherquips.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.repository.WeatherError
import com.weatherquips.app.location.LocationProvider
import com.weatherquips.app.notifications.AlertThrottle
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.ui.home.HomePhase
import com.weatherquips.app.ui.home.HomeViewModel
import com.weatherquips.app.ui.home.UiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ViewModel behaviour: the state machine the whole home screen depends on.
 * Robolectric supplies a Context for the location provider and notification
 * helper; the repositories are fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var throttleFile: File
    private lateinit var throttleStore: DataStore<Preferences>
    private lateinit var throttleScope: kotlinx.coroutines.CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        throttleFile = File.createTempFile("throttle", ".preferences_pb").apply { delete() }
        throttleScope = kotlinx.coroutines.CoroutineScope(
            Dispatchers.IO + kotlinx.coroutines.SupervisorJob(),
        )
        throttleStore = PreferenceDataStoreFactory.create(scope = throttleScope) { throttleFile }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        throttleScope.cancel()
        throttleFile.delete()
    }

    private fun viewModel(
        weatherRepository: FakeWeatherRepository = FakeWeatherRepository(),
        settings: AppSettings = AppSettings(
            locationMode = LocationMode.MANUAL,
            manualCoords = Coordinates(52.99, 6.56),
        ),
    ) = HomeViewModel(
        weatherRepository = weatherRepository,
        settingsRepository = FakeSettingsRepository(settings),
        locationProvider = LocationProvider(context),
        notificationHelper = NotificationHelper(context),
        alertThrottle = AlertThrottle(throttleStore),
    )

    @Test
    fun `manual location loads weather and lands on Success`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue("expected Success but was $phase", phase is HomePhase.Success)
        assertEquals("Assen", (phase as HomePhase.Success).weather.location)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `manual mode with no city selected asks the user to pick one`() = runTest(dispatcher) {
        val viewModel = viewModel(
            settings = AppSettings(locationMode = LocationMode.MANUAL, manualCoords = null),
        )
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is HomePhase.Error)
        assertEquals(UiError.NO_MANUAL_LOCATION, (phase as HomePhase.Error).error)
    }

    @Test
    fun `device mode without permission asks for it instead of erroring`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = AppSettings(locationMode = LocationMode.DEVICE))
        advanceUntilIdle()

        // Robolectric grants no runtime permissions by default.
        assertTrue(viewModel.uiState.value.phase is HomePhase.PermissionRequired)
    }

    @Test
    fun `a denied permission is remembered so the copy can explain itself`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = AppSettings(locationMode = LocationMode.DEVICE))
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is HomePhase.PermissionRequired)
        assertTrue((phase as HomePhase.PermissionRequired).deniedOnce)
    }

    @Test
    fun `a network failure with a cache shows the cached weather as offline`() = runTest(dispatcher) {
        val cached = CachedWeather(
            data = TestWeather.sample(location = "Groningen"),
            fetchedAtEpochMillis = 1_700_000_000_000,
            coordinates = Coordinates(53.2, 6.5),
        )
        val repository = FakeWeatherRepository(
            result = Result.failure(WeatherError.NoInternet),
            cached = cached,
        )

        val viewModel = viewModel(weatherRepository = repository)
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue("expected Offline but was $phase", phase is HomePhase.Offline)
        phase as HomePhase.Offline
        assertEquals("Groningen", phase.weather.location)
        assertEquals(UiError.NO_INTERNET, phase.error)
        assertEquals(1_700_000_000_000, phase.fetchedAtMillis)
    }

    @Test
    fun `a network failure with no cache shows a readable error`() = runTest(dispatcher) {
        val repository = FakeWeatherRepository(result = Result.failure(WeatherError.Timeout))

        val viewModel = viewModel(weatherRepository = repository)
        advanceUntilIdle()

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is HomePhase.Error)
        assertEquals(UiError.TIMEOUT, (phase as HomePhase.Error).error)
    }

    @Test
    fun `refresh fetches again`() = runTest(dispatcher) {
        val repository = FakeWeatherRepository()
        val viewModel = viewModel(weatherRepository = repository)
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, repository.callCount)
        assertTrue(viewModel.uiState.value.phase is HomePhase.Success)
    }

    @Test
    fun `switching the easter egg on rewrites the quote straight away`() = runTest(dispatcher) {
        // Regression test: turning the mode on changes no input that triggers a
        // reload, so the themed quip was never generated. The banner and the
        // Poke Ball appeared while the quote stayed blank.
        val settings = FakeSettingsRepository(
            AppSettings(
                locationMode = LocationMode.MANUAL,
                manualCoords = Coordinates(52.99, 6.56),
            ),
        )
        val viewModel = HomeViewModel(
            weatherRepository = FakeWeatherRepository(
                result = Result.success(TestWeather.sample(condition = WeatherCondition.STORMY)),
            ),
            settingsRepository = settings,
            locationProvider = LocationProvider(context),
            notificationHelper = NotificationHelper(context),
            alertThrottle = AlertThrottle(throttleStore),
        )
        advanceUntilIdle()
        val weatherQuote = viewModel.uiState.value.displayQuote
        assertTrue(weatherQuote.isNotBlank())

        settings.update { it.copy(pokemonMode = true) }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("the easter egg never engaged", state.isPokemonMode)
        assertTrue("the quote stayed empty", state.displayQuote.isNotBlank())
        assertTrue(
            "not a themed quote: ${state.displayQuote}",
            state.displayQuote in PokemonQuips.quotesFor(WeatherCondition.STORMY),
        )
        assertTrue(state.displaySubtitle.isNotBlank())
    }

    @Test
    fun `switching it off restores the ordinary quip`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(
            AppSettings(
                locationMode = LocationMode.MANUAL,
                manualCoords = Coordinates(52.99, 6.56),
                pokemonMode = true,
            ),
        )
        val weather = TestWeather.sample(condition = WeatherCondition.STORMY)
        val viewModel = HomeViewModel(
            weatherRepository = FakeWeatherRepository(result = Result.success(weather)),
            settingsRepository = settings,
            locationProvider = LocationProvider(context),
            notificationHelper = NotificationHelper(context),
            alertThrottle = AlertThrottle(throttleStore),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.displayQuote in PokemonQuips.quotesFor(WeatherCondition.STORMY))

        settings.update { it.copy(pokemonMode = false) }
        advanceUntilIdle()

        assertEquals(weather.funnyQuote, viewModel.uiState.value.displayQuote)
        assertEquals(weather.subtitle, viewModel.uiState.value.displaySubtitle)
    }

    @Test
    fun `pokemon mode supplies its own quote for the condition`() = runTest(dispatcher) {
        val viewModel = viewModel(
            weatherRepository = FakeWeatherRepository(
                result = Result.success(TestWeather.sample(condition = WeatherCondition.STORMY)),
            ),
            settings = AppSettings(
                locationMode = LocationMode.MANUAL,
                manualCoords = Coordinates(34.68, 138.95),
                pokemonMode = true,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPokemonMode)
        assertTrue(state.displayQuote.isNotBlank())
        assertTrue(
            state.displayQuote in
                com.weatherquips.app.domain.quotes.PokemonQuips.quotesFor(WeatherCondition.STORMY),
        )
    }
}
