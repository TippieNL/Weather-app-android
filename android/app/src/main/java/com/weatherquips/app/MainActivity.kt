package com.weatherquips.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.ui.navigation.AppStartViewModel
import com.weatherquips.app.ui.navigation.Routes
import com.weatherquips.app.ui.navigation.WeatherQuipsNavHost
import com.weatherquips.app.ui.theme.WeatherQuipsTheme

/** A screen to open on launch, carried in from a notification. */
data class PendingDestination(val route: String, val coordinates: Coordinates?)

class MainActivity : ComponentActivity() {

    /**
     * Screen requested by a notification tap, if any. Held as state because the
     * activity is singleTop: a second tap arrives through onNewIntent rather
     * than through a fresh onCreate.
     */
    private val pendingDestination = mutableStateOf<PendingDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge with transparent system bars; each screen applies its own
        // window insets so nothing hides behind the status or navigation bar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDestination.value = intent.readDestination()

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
                    startDestination?.let {
                        WeatherQuipsNavHost(
                            startDestination = it,
                            pendingDestination = pendingDestination.value,
                            onPendingDestinationHandled = { pendingDestination.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination.value = intent.readDestination()
    }
}

/** Reads the screen a notification asked for, if the extras carry one. */
internal fun Intent.readDestination(): PendingDestination? {
    val destination = getStringExtra(NotificationHelper.EXTRA_DESTINATION) ?: return null
    if (destination != NotificationHelper.DESTINATION_RADAR) return null
    if (!hasExtra(NotificationHelper.EXTRA_LATITUDE)) return null
    return PendingDestination(
        route = Routes.PRECIPITATION,
        coordinates = Coordinates(
            latitude = getDoubleExtra(NotificationHelper.EXTRA_LATITUDE, 0.0),
            longitude = getDoubleExtra(NotificationHelper.EXTRA_LONGITUDE, 0.0),
        ),
    )
}
