package com.weatherquips.app

import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.ui.navigation.AppStartViewModel
import com.weatherquips.app.ui.navigation.Routes
import com.weatherquips.app.ui.onboarding.LocationChoice
import com.weatherquips.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** First run happens once, and declining location is not a dead end. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a fresh install opens on the introduction`() = runTest(dispatcher) {
        val viewModel = AppStartViewModel(FakeSettingsRepository())
        advanceUntilIdle()
        assertEquals(Routes.ONBOARDING, viewModel.startDestination.value)
    }

    @Test
    fun `once seen, the app opens on the weather`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(AppSettings(onboardingCompleted = true))
        val viewModel = AppStartViewModel(repository)
        advanceUntilIdle()
        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `the destination stays unknown until the flag has been read`() = runTest(dispatcher) {
        val viewModel = AppStartViewModel(FakeSettingsRepository())
        // Nothing has run yet: the UI must wait rather than guess at Home.
        assertNull(viewModel.startDestination.value)
        advanceUntilIdle()
        assertEquals(Routes.ONBOARDING, viewModel.startDestination.value)
    }

    @Test
    fun `finishing records that the introduction has been seen`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.finish()
        advanceUntilIdle()

        assertTrue(repository.current().onboardingCompleted)
        assertTrue(viewModel.uiState.value.finished)
        // Untouched: the user never asked for manual mode.
        assertEquals(LocationMode.DEVICE, repository.current().locationMode)
    }

    @Test
    fun `choosing to type a city switches to manual mode`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.finish(chooseManualLocation = true)
        advanceUntilIdle()

        assertTrue(repository.current().onboardingCompleted)
        assertEquals(LocationMode.MANUAL, repository.current().locationMode)
    }

    @Test
    fun `the location answer is remembered so the copy can respond`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(FakeSettingsRepository())
        assertEquals(LocationChoice.UNANSWERED, viewModel.uiState.value.locationChoice)

        viewModel.onLocationResult(granted = false)
        assertEquals(LocationChoice.DECLINED, viewModel.uiState.value.locationChoice)
        assertFalse(viewModel.uiState.value.finished)

        viewModel.onLocationResult(granted = true)
        assertEquals(LocationChoice.GRANTED, viewModel.uiState.value.locationChoice)
    }

    @Test
    fun `the seen flag survives a restart`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        OnboardingViewModel(repository).finish()
        advanceUntilIdle()

        // A new start-up reading the same store must not show the intro again.
        val next = AppStartViewModel(repository)
        advanceUntilIdle()
        assertEquals(Routes.HOME, next.startDestination.value)
    }
}
