package com.weatherquips.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weatherquips.app.ui.navigation.AppStartViewModel
import com.weatherquips.app.ui.navigation.WeatherQuipsNavHost
import com.weatherquips.app.ui.theme.WeatherQuipsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge with transparent system bars; each screen applies its own
        // window insets so nothing hides behind the status or navigation bar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            WeatherQuipsTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val startViewModel: AppStartViewModel =
                        viewModel(factory = AppStartViewModel.factory())
                    val startDestination by startViewModel.startDestination
                        .collectAsStateWithLifecycle()

                    // Blank for the frame or two it takes to read the flag,
                    // rather than showing Home and replacing it immediately.
                    startDestination?.let { WeatherQuipsNavHost(startDestination = it) }
                }
            }
        }
    }
}
