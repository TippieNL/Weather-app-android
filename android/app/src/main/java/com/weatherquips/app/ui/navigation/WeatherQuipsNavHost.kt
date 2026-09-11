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
import com.weatherquips.app.PendingDestination
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.ui.home.HomeScreen
import com.weatherquips.app.ui.home.HomeViewModel
import com.weatherquips.app.ui.onboarding.OnboardingScreen
import com.weatherquips.app.ui.onboarding.OnboardingViewModel
import com.weatherquips.app.ui.precipitation.PrecipitationMapScreen
import com.weatherquips.app.ui.precipitation.PrecipitationViewModel
import com.weatherquips.app.ui.settings.SettingsScreen
import com.weatherquips.app.ui.settings.SettingsViewModel

/**
 * Routes. Precipitation carries its coordinates in the route, the same way the
 * web app used `?lat=&lon=`, so the screen is restorable and deep-linkable.
 */
object Routes {
    const val ONBOARDING = "onboarding"
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
    startDestination: String,
    modifier: Modifier = Modifier,
    pendingDestination: PendingDestination? = null,
    onPendingDestinationHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // A notification tap lands here. It is consumed once, so returning to the
    // app later does not bounce the user back onto the radar.
    LaunchedEffect(pendingDestination) {
        val target = pendingDestination ?: return@LaunchedEffect
        val coordinates = target.coordinates
        if (target.route == Routes.PRECIPITATION && coordinates != null) {
            navController.navigate(Routes.precipitation(coordinates)) { launchSingleTop = true }
        }
        onPendingDestinationHandled()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory())
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Leaving onboarding behind on the back stack would let the back
            // gesture drop the user straight back into the intro.
            LaunchedEffect(uiState.finished) {
                if (uiState.finished) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            OnboardingScreen(
                uiState = uiState,
                onLocationResult = viewModel::onLocationResult,
                onFinish = viewModel::finish,
            )
        }

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
                onResumeForLifecycle = viewModel::resumeForLifecycle,
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
