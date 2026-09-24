package com.weatherquips.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.weatherquips.app.ui.components.PANEL_REVEAL_MILLIS
import com.weatherquips.app.ui.components.LocalPanelReveal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import com.weatherquips.app.ui.theme.LocalAccents
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveable
import com.weatherquips.app.ui.components.WeatherAtmosphere
import com.weatherquips.app.ui.components.rememberAnimationsEnabled
import com.weatherquips.app.ui.theme.Motion
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.ui.components.AnimatedWeatherIcon
import com.weatherquips.app.ui.components.PokeballIcon
import com.weatherquips.app.ui.components.PokemonSilhouette
import com.weatherquips.app.ui.components.QuoteText
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.ui.components.RefreshButton
import com.weatherquips.app.ui.components.rememberAnimationsEnabled
import com.weatherquips.app.ui.components.plainQuote
import com.weatherquips.app.ui.theme.LocalAccents
import com.weatherquips.app.ui.theme.QuipHeadlineStyle
import com.weatherquips.app.utils.Formatters
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The home screen: a full-bleed quip, with the weather detail panel pulled up
 * from the bottom.
 *
 * The panel is a Material 3 bottom sheet, which brings anchored dragging and
 * nested scrolling with it — so a flick, a slow drag and a scroll inside the
 * panel all behave the way Android users expect, and the predictive-back
 * gesture is never swallowed by a custom gesture detector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrecipitationMap: (Coordinates) -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    mapHandoff: MapHandoff = remember { MapHandoff() },
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> onPermissionResult(grants.values.any { it }) }

    val requestLocationPermission: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    // Loading, errors and the weather crossfade into one another instead of
    // cutting. Success and Offline share a key: both are the weather screen,
    // and swapping between them must not rebuild it (or replay its entrance).
    AnimatedContent(
        targetState = uiState.phase,
        contentKey = { phase ->
            when (phase) {
                is HomePhase.Success, is HomePhase.Offline -> "weather"
                else -> phase::class
            }
        },
        transitionSpec = {
            fadeIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) togetherWith
                fadeOut(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate))
        },
        label = "home-phase",
    ) { phase ->
        when (phase) {
            is HomePhase.Loading -> LoadingScreen(modifier)

            is HomePhase.PermissionRequired -> LocationPermissionScreen(
                deniedOnce = phase.deniedOnce,
                onAllow = requestLocationPermission,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )

            is HomePhase.LocationUnavailable -> MessageScreen(
                message = stringResource(phase.issue.messageRes),
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )

            is HomePhase.Error -> MessageScreen(
                message = stringResource(phase.error.messageRes),
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )

            is HomePhase.Success -> WeatherDisplay(
                uiState = uiState,
                weather = phase.weather,
                coordinates = phase.coordinates,
                staleSinceMillis = null,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onOpenPrecipitationMap = onOpenPrecipitationMap,
                mapHandoff = mapHandoff,
                modifier = modifier,
            )

            is HomePhase.Offline -> WeatherDisplay(
                uiState = uiState,
                weather = phase.weather,
                coordinates = phase.coordinates,
                staleSinceMillis = phase.fetchedAtMillis,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onOpenPrecipitationMap = onOpenPrecipitationMap,
                mapHandoff = mapHandoff,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherDisplay(
    uiState: HomeUiState,
    weather: WeatherData,
    coordinates: Coordinates,
    staleSinceMillis: Long?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrecipitationMap: (Coordinates) -> Unit,
    mapHandoff: MapHandoff,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val expanded = sheetState.targetValue == SheetValue.Expanded
    // Full height of the scaffold, needed to turn the sheet offset into 0..1.
    val sheetHeightPx = remember { mutableFloatStateOf(0f) }

    // The hero recedes as the panel comes up. Following the sheet's live offset
    // makes a slow drag feel connected instead of snapping at the end; before the
    // sheet has been laid out (requireOffset would throw) we fall back to an
    // animation driven by the target state.
    val density = LocalDensity.current
    val fallbackProgress by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "hero-progress",
    )
    val heroProgress by remember(sheetState, density) {
        derivedStateOf {
            val offset = runCatching { sheetState.requireOffset() }.getOrNull()
                ?: return@derivedStateOf fallbackProgress
            val peekPx = with(density) { SHEET_PEEK_HEIGHT.toPx() }
            val collapsedOffset = sheetHeightPx.floatValue - peekPx
            if (collapsedOffset <= 0f) {
                fallbackProgress
            } else {
                (offset / collapsedOffset).coerceIn(0f, 1f)
            }
        }
    }

    // The panel's contents play their entrance every time it opens: it is
    // brief, and it is what makes the panel feel like it arrives with data
    // rather than sliding a static page up.
    val panelAnimations = rememberAnimationsEnabled()
    val panelReveal = remember { Animatable(1f) }
    LaunchedEffect(expanded) {
        if (expanded && panelAnimations) {
            panelReveal.snapTo(0f)
            panelReveal.animateTo(1f, tween(PANEL_REVEAL_MILLIS, easing = LinearEasing))
        }
    }

    val accents = LocalAccents.current

    val tint = if (uiState.isPokemonMode) {
        Modifier.background(pokemonTint(accents.pokemonRed, accents.cold))
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize().then(tint)) {
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sheetHeightPx.floatValue = it.height.toFloat() },
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetContentColor = MaterialTheme.colorScheme.onBackground,
        sheetShadowElevation = 0.dp,
        sheetTonalElevation = 0.dp,
        sheetShape = RectangleShape,
        sheetSwipeEnabled = true,
        sheetDragHandle = {
            SheetHandle(
                expanded = expanded,
                onToggle = {
                    scope.launch {
                        if (expanded) sheetState.partialExpand() else sheetState.expand()
                    }
                },
            )
        },
        sheetContent = {
            CompositionLocalProvider(LocalPanelReveal provides { panelReveal.value }) {
                WeatherDetailPanel(
                    uiState = uiState,
                    weather = weather,
                    staleSinceMillis = staleSinceMillis,
                    onRefresh = onRefresh,
                )
            }
        },
        // Transparent so the mode's wash runs behind the whole screen rather
        // than stopping at the sheet's peek height.
        containerColor = if (uiState.isPokemonMode) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.background
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HeroContent(
                uiState = uiState,
                weather = weather,
                progress = heroProgress,
                staleSinceMillis = staleSinceMillis,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onExpand = { scope.launch { sheetState.expand() } },
                onOpenMap = { onOpenPrecipitationMap(coordinates) },
                mapHandoff = mapHandoff,
            )
        }
    }
    }

    // Back collapses the panel before it ever leaves the screen.
    BackHandler(enabled = expanded) {
        scope.launch { sheetState.partialExpand() }
    }
}

@Composable
private fun HeroContent(
    uiState: HomeUiState,
    weather: WeatherData,
    progress: Float,
    staleSinceMillis: Long?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onExpand: () -> Unit,
    onOpenMap: () -> Unit,
    mapHandoff: MapHandoff,
) {
    val settings = uiState.settings
    var explodeTaps by remember { mutableStateOf(TapStreak()) }
    var exploding by remember { mutableStateOf(false) }

    LaunchedEffect(exploding) {
        if (exploding) {
            delay(1_000)
            exploding = false
        }
    }

    // The whole screen responds to a swipe, not just the handle at the bottom:
    // up opens the detail panel, right-to-left pulls in the radar. The screen
    // follows the finger the whole way, so the gesture is a physical pull
    // rather than a guess that is judged when the finger lifts.
    val density = LocalDensity.current
    val expandThresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    val mapThresholdPx = with(density) { MAP_SWIPE_THRESHOLD.toPx() }
    val axisLockPx = with(density) { AXIS_LOCK.toPx() }
    val flingMinPx = with(density) { FLING_MIN_TRAVEL.toPx() }
    val maxLiftPx = with(density) { MAX_LIFT.toPx() }

    // Raw finger travel, and what is drawn from it.
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var axis by remember { mutableStateOf<DragAxis?>(null) }
    /** How far the radar has been pulled in from the right edge, in px. */
    var pull by remember { mutableFloatStateOf(0f) }
    /** How far the hero has been lifted towards the panel, in px. */
    var lift by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val velocity = remember { VelocityTracker() }
    val view = LocalView.current

    fun springBack() {
        settleJob?.cancel()
        settleJob = scope.launch {
            launch { animate(pull, 0f, animationSpec = Motion.settle()) { v, _ -> pull = v } }
            launch { animate(lift, 0f, animationSpec = Motion.settle()) { v, _ -> lift = v } }
        }
    }

    fun openMap() {
        mapHandoff.revealedPx = pull
        onOpenMap()
        // If navigation is refused (a double tap, a destination already on
        // top), the screen must not stay half pulled. When navigation does
        // happen the hero leaves composition first and this never runs.
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(MAP_HANDOFF_TIMEOUT_MILLIS)
            springBack()
        }
    }

    // The swipe has no visible control, and TalkBack users cannot perform a
    // custom swipe, so the same action is offered in TalkBack's actions menu.
    val openMapLabel = stringResource(R.string.open_precipitation_map)

    // The entrance plays once per session. Saveable, so coming back from the
    // map or settings does not replay a two-second performance every time.
    val animationsEnabled = rememberAnimationsEnabled()
    var introPlayed by rememberSaveable { mutableStateOf(!animationsEnabled) }
    val intro = remember { Animatable(if (introPlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!introPlayed) {
            intro.animateTo(1f, tween(INTRO_MILLIS, easing = LinearEasing))
            introPlayed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_HERO)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(openMapLabel) {
                        openMap()
                        true
                    },
                )
            }
            .pointerInput(expandThresholdPx, mapThresholdPx) {
                detectDragGestures(
                    onDragStart = {
                        settleJob?.cancel()
                        dragX = 0f
                        dragY = 0f
                        axis = null
                        armed = false
                        velocity.resetTracking()
                    },
                    onDragCancel = {
                        axis = null
                        springBack()
                    },
                    onDragEnd = {
                        val fling = velocity.calculateVelocity()
                        // A flick carries on after the finger lifts: judge where
                        // it was heading, not only where it stopped. Short
                        // twitches do not get the benefit.
                        val projectedX = if (abs(dragX) >= flingMinPx) {
                            dragX + fling.x * FLING_PROJECTION_SECONDS
                        } else {
                            dragX
                        }
                        val projectedY = if (abs(dragY) >= flingMinPx) {
                            dragY + fling.y * FLING_PROJECTION_SECONDS
                        } else {
                            dragY
                        }
                        val swipe = HeroSwipe.classify(
                            projectedX,
                            projectedY,
                            expandThresholdPx,
                            mapThresholdPx,
                        )
                        // Once the drag has chosen an axis, only that axis's
                        // outcome can happen: what moved is what happens.
                        when {
                            swipe == HeroSwipe.OPEN_MAP && axis != DragAxis.VERTICAL -> openMap()
                            swipe == HeroSwipe.EXPAND && axis != DragAxis.HORIZONTAL -> {
                                onExpand()
                                springBack()
                            }
                            else -> springBack()
                        }
                        axis = null
                    },
                ) { change, delta ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    dragX += delta.x
                    dragY += delta.y

                    if (axis == null && (abs(dragX) > axisLockPx || abs(dragY) > axisLockPx)) {
                        axis = if (abs(dragX) > abs(dragY)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                    }
                    when (axis) {
                        DragAxis.HORIZONTAL -> {
                            pull = (-dragX).coerceAtLeast(0f)
                            lift = 0f
                        }
                        DragAxis.VERTICAL -> {
                            // Rubber-banded: the hero gives, but not all the way.
                            lift = rubberBand((-dragY).coerceAtLeast(0f), maxLiftPx)
                            pull = 0f
                        }
                        null -> Unit
                    }

                    val nowArmed = when (axis) {
                        DragAxis.HORIZONTAL -> pull >= mapThresholdPx
                        DragAxis.VERTICAL -> -dragY >= expandThresholdPx
                        null -> false
                    }
                    if (nowArmed != armed) {
                        armed = nowArmed
                        view.thresholdHaptic(activated = nowArmed)
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 24.dp)
                .graphicsLayer {
                    // Alpha per draw call rather than through an offscreen
                    // buffer, which costs an allocation every frame of the
                    // panel's fade.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    // Home drifts left at a fraction of the radar's pace: the
                    // parallax that makes the radar read as arriving on top.
                    // Deliberately not dimmed as well: fading a translated
                    // layer clipped it at its resting edge, cutting the quote
                    // in half as it slid.
                    translationX = -pull * HOME_PARALLAX
                    alpha = progress
                    translationY = -(1f - progress) * size.height * 0.4f - lift
                },
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    if (!uiState.isPokemonMode) {
                        WeatherAtmosphere(
                            condition = weather.condition,
                            isDay = weather.isDay,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = introStage(intro.value, 0) },
                        )
                    }
                    if (uiState.isPokemonMode) {
                        PokeballIcon(size = 160.dp)
                    } else {
                        AnimatedWeatherIcon(
                            condition = weather.condition,
                            isDay = weather.isDay,
                            size = 160.dp,
                            exploding = exploding,
                            contentDescription = stringResource(
                                R.string.detail_feels_like,
                                weather.condition.id,
                                Formatters.formatTemp(weather.feelsLike, settings.temperatureUnit),
                            ),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                explodeTaps = explodeTaps.register()
                                if (explodeTaps.reached(EXPLODE_TAP_COUNT)) {
                                    explodeTaps = TapStreak()
                                    exploding = true
                                }
                            },
                        )
                    }
                }
                if (uiState.isPokemonMode) {
                    PokemonSilhouette(
                        silhouette = uiState.pokemonSilhouette,
                        size = 76.dp,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // A refresh brings a new quip: the old one lifts away and the new
            // one rises into its place, so the change is seen, not just noticed.
            AnimatedContent(
                targetState = uiState.displayQuote,
                transitionSpec = {
                    (
                        slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 3 } +
                            fadeIn(tween(Motion.MEDIUM, delayMillis = Motion.STAGGER))
                        ) togetherWith (
                        slideOutVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { -it / 4 } +
                            fadeOut(tween(Motion.SHORT))
                        ) using SizeTransform(clip = false)
                },
                modifier = Modifier.staged(intro, 1),
                label = "quote",
            ) { quote ->
                QuoteText(
                    quote = quote,
                    temperatureCelsius = weather.temperature,
                    style = QuipHeadlineStyle.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_QUOTE)
                        .clearAndSetSemantics {
                            contentDescription = plainQuote(quote)
                        },
                )
            }

            Spacer(Modifier.height(12.dp))

            Crossfade(
                targetState = uiState.displaySubtitle,
                animationSpec = tween(Motion.MEDIUM),
                modifier = Modifier.staged(intro, 2),
                label = "subtitle",
            ) { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_SUBTITLE),
                )
            }

            if (staleSinceMillis != null) {
                Spacer(Modifier.height(12.dp))
                StaleBadge(staleSinceMillis)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.staged(intro, 3),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_thermometer),
                    contentDescription = stringResource(R.string.temperature),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formatters.formatTemp(weather.temperature, settings.temperatureUnit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_TEMPERATURE),
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_map_pin),
                    contentDescription = stringResource(R.string.location),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = weather.location,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_LOCATION),
                )
            }
        }

        // Above the hero: the radar arrives on top of home, exactly as the map
        // screen will a moment later.
        RadarPeek(
            pullPx = { pull },
            armed = armed && axis == DragAxis.HORIZONTAL,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        // Top controls fade out while the panel is up, and as the radar is
        // pulled in over them.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .graphicsLayer {
                    alpha = progress * (1f - (pull / mapThresholdPx).coerceIn(0f, 1f))
                },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(20.dp),
                )
            }
            RefreshButton(onRefresh = onRefresh, isRefreshing = uiState.isRefreshing)
        }

        if (uiState.isPokemonMode) {
            PokemonBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .graphicsLayer { alpha = progress },
            )
        }

    }
}

/**
 * How far the radar had been pulled in when the finger let go.
 *
 * The map screen's entrance reads it so the real map starts exactly where the
 * peek's edge was and carries on from there. Without it the map would restart
 * from the far right edge, and a swipe that was already two-thirds done would
 * jump backwards before completing.
 */
@Stable
class MapHandoff {
    var revealedPx by mutableFloatStateOf(0f)
}

internal enum class DragAxis { HORIZONTAL, VERTICAL }

/**
 * The radar arriving under the finger: a panel attached to the right edge,
 * exactly as wide as the finger has pulled. It shows where the swipe leads,
 * and past the point of no return the icon lights up to say so.
 *
 * Takes the pull as a lambda so a drag redraws only this panel, not the hero.
 */
@Composable
private fun RadarPeek(
    pullPx: () -> Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccents.current.cold
    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1.2f else 1f,
        animationSpec = Motion.pop(),
        label = "peek-icon-scale",
    )
    val tint by animateColorAsState(
        targetValue = if (armed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.SHORT),
        label = "peek-icon-tint",
    )
    Box(
        modifier = modifier
            .testTag(TAG_RADAR_PEEK)
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val width = pullPx().roundToInt().coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width),
                )
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                // The icon arrives as the panel opens up, not before there is
                // room for it.
                val opened = (pullPx() / (PEEK_ICON_ROOM_DP * density)).coerceIn(0f, 1f)
                alpha = opened
                scaleX = iconScale * (0.6f + 0.4f * opened)
                scaleY = iconScale * (0.6f + 0.4f * opened)
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.radar_label),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/**
 * Resistance for a drag that should give a little but not follow all the way:
 * close to 1:1 at first, flattening towards [limit].
 */
internal fun rubberBand(distance: Float, limit: Float): Float {
    if (distance <= 0f || limit <= 0f) return 0f
    return limit * (1f - 1f / (distance / limit * RUBBER_STIFFNESS + 1f))
}

/**
 * A tick as the swipe crosses the point of no return, and another if it is
 * dragged back. The dedicated gesture-threshold effects exist from Android
 * 14; older phones get the closest equivalent.
 */
private fun android.view.View.thresholdHaptic(activated: Boolean) {
    val effect = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            if (activated) {
                android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else {
                android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            }
        activated -> android.view.HapticFeedbackConstants.CONTEXT_CLICK
        else -> android.view.HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(effect)
}

/**
 * What a finished drag on the hero meant.
 *
 * Pure so the geometry can be tested without synthesising touch events. The
 * horizontal case must clearly dominate: a thumb swiping up often drifts
 * sideways, and opening a map by accident costs a screen transition and a
 * radar download, where opening the panel by accident costs nothing.
 */
internal enum class HeroSwipe {
    EXPAND,
    OPEN_MAP;

    companion object {
        /** How much further sideways than vertical a map swipe must travel. */
        const val HORIZONTAL_DOMINANCE = 1.5f

        fun classify(
            dx: Float,
            dy: Float,
            expandThresholdPx: Float,
            mapThresholdPx: Float,
        ): HeroSwipe? = when {
            dx <= -mapThresholdPx && abs(dx) >= abs(dy) * HORIZONTAL_DOMINANCE -> OPEN_MAP
            dy <= -expandThresholdPx && abs(dy) > abs(dx) -> EXPAND
            else -> null
        }
    }
}

@Composable
private fun SheetHandle(expanded: Boolean, onToggle: () -> Unit) {
    val hideDetails = stringResource(R.string.hide_details)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_PEEK_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
            IconButton(onClick = onToggle, modifier = Modifier.testTag(TAG_EXPAND)) {
                BouncingChevron()
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .testTag(TAG_COLLAPSE)
                    .semantics { contentDescription = hideDetails },
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun BouncingChevron() {
    val animationsEnabled = rememberAnimationsEnabled()
    // This was a six *pixel* bounce — two dp on a modern phone, which nobody
    // saw. Now a double nudge upward and a rest, like a hand beckoning: easier
    // to notice than constant motion, and less tiring to live with.
    val transition = rememberInfiniteTransition(label = "chevron")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2_600
                0f at 0 using Motion.Standard
                1f at 260 using Motion.Standard
                0f at 560 using Motion.Standard
                0.6f at 780 using Motion.Standard
                0f at 1_060
                0f at 2_600
            },
        ),
        label = "chevron-lift",
    )
    Icon(
        painter = painterResource(R.drawable.ic_chevron_up),
        contentDescription = stringResource(R.string.show_details),
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                val amount = if (animationsEnabled) lift else 0f
                translationY = -CHEVRON_LIFT_DP * density * amount
                alpha = 0.7f + 0.3f * amount
            },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StaleBadge(fetchedAtMillis: Long) {
    val relative = remember(fetchedAtMillis) {
        android.text.format.DateUtils.getRelativeTimeSpanString(
            fetchedAtMillis,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.cached_notice, relative),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_STALE),
        )
    }
}

@Composable
private fun PokemonBanner(modifier: Modifier = Modifier) {
    val accents = LocalAccents.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = PokemonQuips.BANNER,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accents.pokemonRed)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * A hint of red at the top and blue at the bottom. Kept to the very edges with
 * a wide clear middle: a full-height wash tinted the whole screen pink and
 * fought with the artwork.
 */
private fun pokemonTint(red: Color, blue: Color) = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to red.copy(alpha = 0.12f),
        0.28f to Color.Transparent,
        0.72f to Color.Transparent,
        1f to blue.copy(alpha = 0.12f),
    ),
)

/** Tap streak used by the "explode" Easter egg (5 taps within 300 ms of each other). */
internal data class TapStreak(val count: Int = 0, val lastTapMillis: Long = 0L) {
    fun register(now: Long = System.currentTimeMillis()): TapStreak =
        if (lastTapMillis != 0L && now - lastTapMillis > TAP_WINDOW_MILLIS) {
            TapStreak(count = 1, lastTapMillis = now)
        } else {
            TapStreak(count = count + 1, lastTapMillis = now)
        }

    fun reached(target: Int): Boolean = count >= target

    private companion object {
        const val TAP_WINDOW_MILLIS = 300L
    }
}

/**
 * One element's share of the entrance: each starts [Motion.STAGGER] after the
 * one before, rises into place and fades up over [Motion.LONG].
 */
internal fun introStage(intro: Float, index: Int): Float {
    val elapsed = intro * INTRO_MILLIS - index * Motion.STAGGER
    val linear = (elapsed / Motion.LONG).coerceIn(0f, 1f)
    return Motion.EmphasizedDecelerate.transform(linear)
}

private fun Modifier.staged(intro: Animatable<Float, *>, index: Int): Modifier =
    graphicsLayer {
        val stage = introStage(intro.value, index)
        alpha = stage
        translationY = (1f - stage) * INTRO_RISE_DP * density
    }

/** Long enough for the last staged element to land. */
internal const val INTRO_MILLIS = Motion.LONG + 3 * Motion.STAGGER
private const val INTRO_RISE_DP = 28f
private const val CHEVRON_LIFT_DP = 9f

/** Matches the web app's 50px swipe threshold. */
internal val SWIPE_THRESHOLD = 56.dp

/**
 * A little further than the upward swipe: the map is a heavier destination,
 * and a sideways brush while scrolling past should not land on it.
 */
internal val MAP_SWIPE_THRESHOLD = 72.dp

/** Travel before a drag commits to being horizontal or vertical. */
private val AXIS_LOCK = 10.dp

/** A flick must travel at least this far before its speed counts for anything. */
private val FLING_MIN_TRAVEL = 24.dp

/** How far ahead a flick is projected when judging where it was going. */
private const val FLING_PROJECTION_SECONDS = 0.12f

/** The most the hero lifts with the finger before the panel takes over. */
private val MAX_LIFT = 72.dp
private const val RUBBER_STIFFNESS = 0.55f

/** Home's pace relative to the radar: a parallax, so the radar reads as on top. */
internal const val HOME_PARALLAX = 0.3f

/** Room the peek needs before its icon is fully in. */
private const val PEEK_ICON_ROOM_DP = 96f

/** How long a committed swipe waits for navigation before giving up. */
private const val MAP_HANDOFF_TIMEOUT_MILLIS = 900L

internal const val EXPLODE_TAP_COUNT = 5
internal val SHEET_PEEK_HEIGHT = 56.dp

const val TAG_QUOTE = "home-quote"
const val TAG_SUBTITLE = "home-subtitle"
const val TAG_TEMPERATURE = "home-temperature"
const val TAG_LOCATION = "home-location"
const val TAG_EXPAND = "home-expand"
const val TAG_HERO = "home-hero"
const val TAG_RADAR_PEEK = "home-radar-peek"
const val TAG_COLLAPSE = "home-collapse"
const val TAG_STALE = "home-stale"
