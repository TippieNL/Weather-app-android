package com.weatherquips.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weatherquips.app.AppContainer
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.ui.home.requireContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happened on the location page, so the copy can react to it. */
enum class LocationChoice { UNANSWERED, GRANTED, DECLINED }

data class OnboardingUiState(
    val locationChoice: LocationChoice = LocationChoice.UNANSWERED,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onLocationResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                locationChoice = if (granted) LocationChoice.GRANTED else LocationChoice.DECLINED,
            )
        }
    }

    /**
     * Records that the introduction has been seen. Declining location switches
     * the app to manual mode so the user lands on the city search rather than
     * on a permission wall they have already said no to.
     */
    fun finish(chooseManualLocation: Boolean = false) {
        viewModelScope.launch {
            settingsRepository.update { settings ->
                settings.copy(
                    onboardingCompleted = true,
                    locationMode = if (chooseManualLocation) {
                        LocationMode.MANUAL
                    } else {
                        settings.locationMode
                    },
                )
            }
            _uiState.update { it.copy(finished = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                OnboardingViewModel(resolved.settingsRepository)
            }
        }
    }
}
