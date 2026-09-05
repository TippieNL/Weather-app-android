package com.weatherquips.app

import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.RadarTimeline
import com.weatherquips.app.ui.precipitation.PrecipitationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Radar timeline state: animation, scrubbing and lifecycle pausing. */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarTimelineTest {

    private val dispatcher = StandardTestDispatcher()

    private val frames = listOf(
        RadarFrame("/v2/radar/1", 1_757_000_000),
        RadarFrame("/v2/radar/2", 1_757_000_600),
        RadarFrame("/v2/radar/nowcast", 1_757_001_200),
    )

    private class FakeRadar(
        private val timeline: RadarTimeline? = null,
        private val failure: Throwable? = null,
    ) : RadarRepository {
        override suspend fun loadTimeline(): RadarTimeline {
            failure?.let { throw it }
            return timeline!!
        }

        override fun tileUrlTemplate(frame: RadarFrame): String =
            "https://tilecache.rainviewer.com${frame.path}/256/{z}/{x}/{y}/2/1_1.png"
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()



    /**
     * Runs [block] against a freshly loaded view model and always stops its
     * animation afterwards. The radar loop never ends on its own, so leaving it
     * running would keep the test scheduler busy forever.
     */
    private fun kotlinx.coroutines.test.TestScope.withRadar(
        repository: RadarRepository,
        block: (PrecipitationViewModel) -> Unit,
    ) {
        val viewModel = PrecipitationViewModel(repository)
        try {
            runCurrent()
            block(viewModel)
        } finally {
            viewModel.pauseForLifecycle()
            runCurrent()
        }
    }

    @Test
    fun `a loaded timeline starts playing from the first frame`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            val state = viewModel.uiState.value
            assertEquals(frames, state.frames)
            assertEquals(2, state.pastCount)
            assertEquals(0, state.currentIndex)
            assertTrue(state.isPlaying)
            assertFalse(state.isLoading)
            assertFalse(state.hasError)
        }
    }

    @Test
    fun `the animation advances and wraps around`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            advanceTimeBy(PrecipitationViewModel.FRAME_INTERVAL_MILLIS + 1)
            assertEquals(1, viewModel.uiState.value.currentIndex)

            advanceTimeBy(PrecipitationViewModel.FRAME_INTERVAL_MILLIS * 2)
            assertEquals(0, viewModel.uiState.value.currentIndex)
        }
    }

    @Test
    fun `pausing stops the animation where it is`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            viewModel.togglePlay()
            val frozenAt = viewModel.uiState.value.currentIndex
            advanceTimeBy(PrecipitationViewModel.FRAME_INTERVAL_MILLIS * 5)

            assertFalse(viewModel.uiState.value.isPlaying)
            assertEquals(frozenAt, viewModel.uiState.value.currentIndex)
        }
    }

    @Test
    fun `scrubbing to a frame selects it and stops playback`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            viewModel.selectFrame(2)

            assertEquals(2, viewModel.uiState.value.currentIndex)
            assertFalse(viewModel.uiState.value.isPlaying)
            assertTrue(viewModel.uiState.value.isForecast(2))
            assertFalse(viewModel.uiState.value.isForecast(1))
        }
    }

    @Test
    fun `leaving the screen pauses and returning resumes`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            viewModel.pauseForLifecycle()
            assertFalse(viewModel.uiState.value.isPlaying)
            advanceTimeBy(PrecipitationViewModel.FRAME_INTERVAL_MILLIS * 3)
            assertEquals(0, viewModel.uiState.value.currentIndex)

            viewModel.resumeForLifecycle()
            assertTrue(viewModel.uiState.value.isPlaying)
        }
    }

    @Test
    fun `returning does not restart an animation the user paused`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            viewModel.togglePlay()
            viewModel.pauseForLifecycle()
            viewModel.resumeForLifecycle()

            assertFalse(viewModel.uiState.value.isPlaying)
        }
    }

    @Test
    fun `a radar outage is an error state, not a crash`() = runTest(dispatcher) {
        withRadar(FakeRadar(failure = IOException("offline"))) { viewModel ->
            val state = viewModel.uiState.value
            assertTrue(state.hasError)
            assertFalse(state.isPlaying)
            assertFalse(state.isLoading)
            assertTrue(state.frames.isEmpty())
        }
    }

    @Test
    fun `an empty timeline is treated as unavailable`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(emptyList(), pastCount = 0))) { viewModel ->
            assertTrue(viewModel.uiState.value.hasError)
        }
    }

    @Test
    fun `tile urls keep the web app's tile flavour`() = runTest(dispatcher) {
        withRadar(FakeRadar(RadarTimeline(frames, pastCount = 2))) { viewModel ->
            assertEquals(
                "https://tilecache.rainviewer.com/v2/radar/1/256/{z}/{x}/{y}/2/1_1.png",
                viewModel.tileUrl(frames[0]),
            )
        }
    }
}
