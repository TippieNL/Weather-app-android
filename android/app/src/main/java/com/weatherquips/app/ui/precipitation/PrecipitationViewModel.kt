package com.weatherquips.app.ui.precipitation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weatherquips.app.AppContainer
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.RadarTile
import com.weatherquips.app.location.DeviceLocationSource
import com.weatherquips.app.location.LocationResult
import com.weatherquips.app.ui.home.requireContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class RadarUiState(
    val frames: List<RadarFrame> = emptyList(),
    val pastCount: Int = 0,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    /** Where the device is, when it is known and permitted. */
    val userLocation: Coordinates? = null,
    /** The deepest zoom the radar source renders; the map scales it up beyond. */
    val maxTileZoom: Int = 0,
    /** Playback is waiting for the next frame's tiles to arrive. */
    val isBuffering: Boolean = false,
) {
    val currentFrame: RadarFrame? get() = frames.getOrNull(currentIndex)

    /** Frames past [pastCount] are RainViewer's nowcast rather than observed radar. */
    fun isForecast(index: Int): Boolean = index >= pastCount
}

/**
 * Drives the radar timeline.
 *
 * The animation loop is a coroutine in `viewModelScope`, so it is cancelled
 * automatically when the screen goes away — no timer can outlive the screen and
 * keep waking the device.
 */
class PrecipitationViewModel(
    private val radarRepository: RadarRepository,
    private val locationProvider: DeviceLocationSource? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState(maxTileZoom = radarRepository.maxTileZoom))
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private var animationJob: Job? = null

    /**
     * Frames whose tiles for the area on screen have all arrived, as the map
     * reports them. Null while nobody is reporting, in which case playback
     * does not wait on anything.
     */
    private val readyFrames = MutableStateFlow<Set<Int>?>(null)

    /** Whether the animation was running when the screen went to the background. */
    private var wasPlayingBeforePause = false

    init {
        loadTimeline()
        loadUserLocation()
    }

    /**
     * One fix, once, and only if location is already permitted — the radar
     * screen is not a reason to prompt for a permission or to start streaming
     * GPS updates.
     */
    private fun loadUserLocation() {
        val provider = locationProvider ?: return
        if (!provider.hasPermission()) return
        viewModelScope.launch {
            val result = provider.currentLocation()
            if (result is LocationResult.Success) {
                _uiState.update { it.copy(userLocation = result.coordinates) }
            }
        }
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                val timeline = radarRepository.loadTimeline()
                if (timeline.frames.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                    return@launch
                }
                // Readiness was for the old frames. If a map is reporting, it
                // reports again for these; until then nothing is ready.
                if (readyFrames.value != null) readyFrames.value = emptySet()
                _uiState.update {
                    it.copy(
                        frames = timeline.frames,
                        pastCount = timeline.pastCount,
                        currentIndex = 0,
                        isLoading = false,
                        hasError = false,
                        isPlaying = true,
                    )
                }
                startAnimation()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, hasError = true, isPlaying = false) }
            }
        }
    }

    fun togglePlay() {
        wasPlayingBeforePause = false
        val playing = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = playing) }
        if (playing) startAnimation() else stopAnimation()
    }

    fun selectFrame(index: Int) {
        stopAnimation()
        _uiState.update {
            it.copy(currentIndex = index.coerceIn(0, (it.frames.size - 1).coerceAtLeast(0)), isPlaying = false)
        }
    }

    /** Called when the screen leaves the foreground: never animate off-screen. */
    fun pauseForLifecycle() {
        wasPlayingBeforePause = _uiState.value.isPlaying
        stopAnimation()
        _uiState.update { it.copy(isPlaying = false) }
    }

    /** Called when the screen is visible again; only resumes what it paused. */
    fun resumeForLifecycle() {
        if (!wasPlayingBeforePause || _uiState.value.frames.isEmpty()) return
        wasPlayingBeforePause = false
        _uiState.update { it.copy(isPlaying = true) }
        startAnimation()
    }

    suspend fun loadTile(frame: RadarFrame, tile: RadarTile): ByteArray? =
        radarRepository.loadTile(frame, tile)

    /** The map says which frames it can show whole; see [readyFrames]. */
    fun onFramesReady(ready: Set<Int>?) {
        readyFrames.value = ready
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                if (!state.isPlaying || state.frames.isEmpty()) break
                // A beat longer on the latest picture before starting over:
                // "now" is the frame people actually want to look at.
                val atLatest = state.currentIndex == state.frames.lastIndex
                delay(if (atLatest) FRAME_INTERVAL_MILLIS + LATEST_FRAME_HOLD_MILLIS else FRAME_INTERVAL_MILLIS)
                val frames = _uiState.value.frames
                if (frames.isEmpty()) break
                val next = (_uiState.value.currentIndex + 1) % frames.size
                awaitFrame(next)
                if (!_uiState.value.isPlaying) break
                _uiState.update { it.copy(currentIndex = next) }
            }
        }
    }

    /**
     * Holds playback until frame [index] can be drawn whole — stepping onto a
     * frame whose tiles are still downloading is what made the rain blink —
     * but never for long: a tile that will not come must not freeze the map.
     */
    private suspend fun awaitFrame(index: Int) {
        if (readyFrames.value.let { it == null || index in it }) return
        _uiState.update { it.copy(isBuffering = true) }
        try {
            withTimeoutOrNull(READY_TIMEOUT_MILLIS) {
                readyFrames.first { it == null || index in it }
            }
        } finally {
            _uiState.update { it.copy(isBuffering = false) }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    override fun onCleared() {
        stopAnimation()
        super.onCleared()
    }

    companion object {
        /**
         * Screen time per ten minutes of radar. Most of it is spent blending
         * into the next frame (see RadarPlayhead), so the motion is continuous.
         */
        const val FRAME_INTERVAL_MILLIS = 700L

        /** Extra time on the latest frame before the loop starts over. */
        const val LATEST_FRAME_HOLD_MILLIS = 1_300L

        /** The longest playback waits for a frame's tiles before moving on anyway. */
        const val READY_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                PrecipitationViewModel(resolved.radarRepository, resolved.locationProvider)
            }
        }
    }
}
