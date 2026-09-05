package com.weatherquips.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.DisposableEffect
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.ui.home.HomeScreen
import com.weatherquips.app.ui.home.HomeViewModel
import com.weatherquips.app.ui.precipitation.PrecipitationMapScreen
import com.weatherquips.app.ui.precipitation.PrecipitationViewModel
import com.weatherquips.app.ui.settings.SettingsScreen
import com.weatherquips.app.ui.settings.SettingsViewModel

/**
 * Routes. Precipitation carries its coordinates in the route, the same way the
 * web app used `?lat=&lon=`, so the screen is restorable and deep-linkable.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PRECIPITATION = "precipitation/{lat}/{lon}"

    fun precipitation(coordinates: Coordinates): String =
        "precipitation/${coordinates.latitude}/${coordinates.longitude}"
}

/**
 * Navigation Compose owns the back stack, so system back behaves correctly with
 * both gesture and three-button navigation: Settings and the map pop back to
 * Home, and Home lets the system finish the activity.
 */
@Composable
fun WeatherQuipsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory())
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Permission or location services may have changed while away.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            HomeScreen(
                uiState = uiState,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onOpenSettings = { navController.navigateSingleTop(Routes.SETTINGS) },
                onOpenPrecipitationMap = { coordinates ->
                    navController.navigateSingleTop(Routes.precipitation(coordinates))
                },
                onPermissionResult = viewModel::onPermissionResult,
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory())
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                uiState = uiState,
                actions = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PRECIPITATION,
            arguments = listOf(
                navArgument("lat") { type = NavType.FloatType },
                navArgument("lon") { type = NavType.FloatType },
            ),
        ) { entry ->
            val latitude = entry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
            val longitude = entry.arguments?.getFloat("lon")?.toDouble() ?: 0.0
            val viewModel: PrecipitationViewModel =
                viewModel(factory = PrecipitationViewModel.factory())
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PrecipitationMapScreen(
                coordinates = Coordinates(latitude, longitude),
                uiState = uiState,
                onTogglePlay = viewModel::togglePlay,
                onSelectFrame = viewModel::selectFrame,
                onPauseForLifecycle = viewModel::pauseForLifecycle,
                tileUrlFor = viewModel::tileUrl,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Guards against a double tap queueing the same destination twice. */
private fun NavHostController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
