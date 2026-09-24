package com.weatherquips.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import com.weatherquips.app.ui.home.HOME_PARALLAX
import com.weatherquips.app.ui.home.MapHandoff
import com.weatherquips.app.ui.theme.Motion
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
    val mapHandoff = remember { MapHandoff() }

    // A notification tap lands here. It is consumed once, so returning to the
    // app later does not bounce the user back onto the radar.
    LaunchedEffect(pendingDestination) {
        val target = pendingDestination ?: return@LaunchedEffect
        val coordinates = target.coordinates
        if (target.route == Routes.PRECIPITATION && coordinates != null) {
            // Nothing was pulled: the map makes its full entrance.
            mapHandoff.revealedPx = 0f
            navController.navigate(Routes.precipitation(coordinates)) { launchSingleTop = true }
        }
        onPendingDestinationHandled()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        // Navigation's own default is a 700ms crossfade, which reads as lag.
        enterTransition = { fadeIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) },
        exitTransition = { fadeOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) },
        popEnterTransition = { fadeIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) },
        popExitTransition = { fadeOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) },
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

        composable(
            route = Routes.HOME,
            // Home steps aside to the left as the radar comes in from the
            // right, so the swipe that opened it and the motion agree.
            // To the radar: home steps aside at a third of the radar's pace, the
            // parallax that puts the map on top. To settings: home recedes a
            // little as settings come forward.
            exitTransition = {
                if (targetState.destination.route == Routes.PRECIPITATION) {
                    slideOutHorizontally(Motion.gestureSpring) { -(it * HOME_PARALLAX).toInt() }
                } else {
                    recede()
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == Routes.PRECIPITATION) {
                    slideInHorizontally(popSpec()) { -(it * HOME_PARALLAX).toInt() }
                } else {
                    returnForward()
                }
            },
        ) {
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
                mapHandoff = mapHandoff,
            )
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = { comeForward() },
            popExitTransition = { fallBack() },
        ) {
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
            // The map starts where the finger left the peek's edge and a spring
            // carries it the rest of the way, so a swipe finishes the motion it
            // began instead of restarting it from the far edge.
            enterTransition = {
                slideInHorizontally(Motion.gestureSpring) { width ->
                    (width - mapHandoff.revealedPx.toInt()).coerceIn(0, width)
                }
            },
            // Back is a tween, not a spring: with predictive back the system
            // scrubs this animation under the user's thumb.
            popExitTransition = { slideOutHorizontally(popSpec()) { it } },
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

// Settings move on the depth axis: they come forward over home and fall back
// into it, where the radar moves sideways. Different places, different axes.
private fun comeForward(): EnterTransition =
    fadeIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) +
        scaleIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate), initialScale = 0.92f)

private fun fallBack(): ExitTransition =
    fadeOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) +
        scaleOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate), targetScale = 0.92f)

private fun recede(): ExitTransition =
    fadeOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) +
        scaleOut(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate), targetScale = 1.04f)

private fun returnForward(): EnterTransition =
    fadeIn(tween(Motion.MEDIUM, delayMillis = Motion.STAGGER / 2)) +
        scaleIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate), initialScale = 1.04f)

private fun popSpec() = tween<IntOffset>(Motion.MEDIUM, easing = Motion.Standard)

/** Guards against a double tap queueing the same destination twice. */
private fun NavHostController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
