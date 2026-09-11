package com.weatherquips.app

import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.RadarTimeline
import com.weatherquips.app.location.DeviceLocationSource
import com.weatherquips.app.location.LocationResult
import com.weatherquips.app.ui.precipitation.PrecipitationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The radar screen shows where the user is, without prompting or polling. */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarUserLocationTest {

    private val dispatcher = StandardTestDispatcher()

    private val timeline = RadarTimeline(
        frames = listOf(RadarFrame("/v2/radar/1", 1_757_000_000)),
        pastCount = 1,
    )

    private class StubRadar(private val timeline: RadarTimeline) : RadarRepository {
        override suspend fun loadTimeline() = timeline
        override fun tileUrlTemplate(frame: RadarFrame) = "https://example.invalid${frame.path}"
    }

    private class StubLocation(
        private val permitted: Boolean,
        private val result: LocationResult = LocationResult.Unavailable,
    ) : DeviceLocationSource {
        var requests = 0
            private set

        override fun hasPermission() = permitted
        override fun isLocationEnabled() = true
        override suspend fun currentLocation(): LocationResult {
            requests++
            return result
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a known position is published for the map to draw`() = runTest(dispatcher) {
        val location = StubLocation(
            permitted = true,
            result = LocationResult.Success(Coordinates(52.99, 6.56)),
        )
        val viewModel = PrecipitationViewModel(StubRadar(timeline), location)
        runCurrent()

        assertEquals(Coordinates(52.99, 6.56), viewModel.uiState.value.userLocation)
        viewModel.pauseForLifecycle()
    }

    @Test
    fun `location is asked for exactly once, not polled`() = runTest(dispatcher) {
        val location = StubLocation(
            permitted = true,
            result = LocationResult.Success(Coordinates(1.0, 2.0)),
        )
        val viewModel = PrecipitationViewModel(StubRadar(timeline), location)
        runCurrent()
        viewModel.togglePlay()
        viewModel.togglePlay()
        runCurrent()

        assertEquals(1, location.requests)
        viewModel.pauseForLifecycle()
    }

    @Test
    fun `without permission the radar never asks for a fix`() = runTest(dispatcher) {
        val location = StubLocation(permitted = false)
        val viewModel = PrecipitationViewModel(StubRadar(timeline), location)
        runCurrent()

        assertEquals(0, location.requests)
        assertNull(viewModel.uiState.value.userLocation)
        viewModel.pauseForLifecycle()
    }

    @Test
    fun `an unavailable fix simply leaves the dot off the map`() = runTest(dispatcher) {
        val location = StubLocation(permitted = true, result = LocationResult.Unavailable)
        val viewModel = PrecipitationViewModel(StubRadar(timeline), location)
        runCurrent()

        assertNull(viewModel.uiState.value.userLocation)
        viewModel.pauseForLifecycle()
    }

    @Test
    fun `radar still loads when there is no location source at all`() = runTest(dispatcher) {
        val viewModel = PrecipitationViewModel(StubRadar(timeline), locationProvider = null)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.frames.size)
        assertNull(viewModel.uiState.value.userLocation)
        viewModel.pauseForLifecycle()
    }
}
