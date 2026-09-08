package com.weatherquips.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationScheduler
import com.weatherquips.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Text input must never wait on storage.
 *
 * Regression test for characters vanishing while typing: the fields used to be
 * driven straight off DataStore, so every keystroke needed an async write and a
 * flow emission before it appeared, and typing faster than that round-trip lost
 * characters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsInputTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    /** A settings store whose writes are slow, like a real DataStore commit. */
    private class SlowSettingsRepository(
        private val writeDelayMillis: Long = 50,
    ) : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())
        var writes = 0
            private set

        override val settings: Flow<AppSettings> = state
        override suspend fun current(): AppSettings = state.first()
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            delay(writeDelayMillis)
            writes++
            state.update(transform)
        }
    }

    private class StubGeocoding : GeocodingRepository {
        override suspend fun search(query: String): List<GeocodeResult> =
            listOf(GeocodeResult(52.99, 6.56, "Assen"))
        override suspend fun reverseGeocode(
            coordinates: com.weatherquips.app.domain.model.Coordinates,
        ): String = "Assen"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: SettingsRepository) = SettingsViewModel(
        settingsRepository = repository,
        geocodingRepository = StubGeocoding(),
        notificationHelper = NotificationHelper(context),
        precipitationScheduler = PrecipitationScheduler(context),
    )

    @Test
    fun `every typed character shows up immediately`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        // Type without letting any storage write finish in between.
        "Amsterdam".forEachIndexed { index, _ ->
            viewModel.setManualLocation("Amsterdam".take(index + 1))
        }

        assertEquals("Amsterdam", viewModel.uiState.value.manualLocationInput)
        advanceUntilIdle()
        assertEquals("Amsterdam", viewModel.uiState.value.manualLocationInput)
    }

    @Test
    fun `a slow write cannot overwrite what is still being typed`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository(writeDelayMillis = 500)
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.setManualLocation("Ams")
        advanceTimeBy(SettingsViewModel.PERSIST_DEBOUNCE_MILLIS + 1)
        // The "Ams" write is in flight; the user keeps typing.
        viewModel.setManualLocation("Amsterdam")
        advanceUntilIdle()

        assertEquals("Amsterdam", viewModel.uiState.value.manualLocationInput)
        assertEquals("Amsterdam", repository.current().manualLocation)
    }

    @Test
    fun `typing is debounced into a single write`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        "Assen".forEachIndexed { index, _ -> viewModel.setManualLocation("Assen".take(index + 1)) }
        advanceUntilIdle()

        assertEquals(1, repository.writes)
        assertEquals("Assen", repository.current().manualLocation)
    }

    @Test
    fun `the api key field is not trimmed while typing`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        // A space mid-entry must survive; trimming here would move the cursor.
        viewModel.setApiKey("abc ")
        assertEquals("abc ", viewModel.uiState.value.apiKeyInput)

        advanceUntilIdle()
        assertEquals("abc ", viewModel.uiState.value.apiKeyInput)
        // Storage still gets the trimmed value.
        assertEquals("abc", repository.current().weatherApiKey)
    }

    @Test
    fun `searching uses what is on screen, not what has been stored`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository(writeDelayMillis = 1_000)
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.setManualLocation("Assen")
        // No time passes: nothing has been persisted yet.
        viewModel.findLocation()
        advanceUntilIdle()

        assertEquals("Assen", repository.current().manualLocation)
        assertEquals(52.99, repository.current().manualCoords?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun `picking a result fills the field with the chosen name`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.setManualLocation("Asse")
        viewModel.selectResult(GeocodeResult(52.99, 6.56, "Assen"))

        assertEquals("Assen", viewModel.uiState.value.manualLocationInput)
        advanceUntilIdle()
        assertEquals("Assen", viewModel.uiState.value.manualLocationInput)
        assertEquals("Assen", repository.current().manualLocation)
    }

    @Test
    fun `stored settings still populate the fields on first load`() = runTest(dispatcher) {
        val repository = SlowSettingsRepository()
        repository.update { it.copy(manualLocation = "Groningen", weatherApiKey = "stored-key") }
        advanceUntilIdle()

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals("Groningen", viewModel.uiState.value.manualLocationInput)
        assertEquals("stored-key", viewModel.uiState.value.apiKeyInput)
    }
}
