package com.weatherquips.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weatherquips.app.AppContainer
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.ui.home.requireContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decides where the app opens.
 *
 * The answer lives on disk, so it starts as null and the UI waits rather than
 * rendering Home and yanking it away a frame later. It is read once: the
 * start destination must not change under a live back stack.
 */
class AppStartViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _startDestination.value = if (settings.onboardingCompleted) {
                Routes.HOME
            } else {
                Routes.ONBOARDING
            }
        }
    }

    companion object {
        fun factory(container: AppContainer? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val resolved = container ?: requireContainer()
                AppStartViewModel(resolved.settingsRepository)
            }
        }
    }
}
