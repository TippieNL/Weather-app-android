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
import com.weatherquips.app.location.DeviceLocationSource
import com.weatherquips.app.location.LocationResult
import com.weatherquips.app.ui.home.requireContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RadarUiState(
    val frames: List<RadarFrame> = emptyList(),
    val pastCount: Int = 0,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    /** Where the device is, when it is known and permitted. */
    val userLocation: Coordinates? = null,
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

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private var animationJob: Job? = null

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

    fun tileUrl(frame: RadarFrame): String = radarRepository.tileUrlTemplate(frame)

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            while (true) {
                delay(FRAME_INTERVAL_MILLIS)
                val state = _uiState.value
                if (!state.isPlaying || state.frames.isEmpty()) break
                _uiState.update {
                    it.copy(currentIndex = (it.currentIndex + 1) % it.frames.size)
                }
            }
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
        const val FRAME_INTERVAL_MILLIS = 800L

        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                PrecipitationViewModel(resolved.radarRepository, resolved.locationProvider)
            }
        }
    }
}
