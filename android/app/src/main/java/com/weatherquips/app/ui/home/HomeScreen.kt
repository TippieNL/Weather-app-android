package com.weatherquips.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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

    when (val phase = uiState.phase) {
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
            modifier = modifier,
        )
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
            WeatherDetailPanel(
                uiState = uiState,
                weather = weather,
                staleSinceMillis = staleSinceMillis,
                onRefresh = onRefresh,
                onOpenPrecipitationMap = { onOpenPrecipitationMap(coordinates) },
            )
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

    // The whole screen responds to a swipe, not just the handle at the bottom —
    // the web app expanded on any upward drag past a threshold, and grabbing a
    // 56dp strip to open the panel feels broken on a phone.
    var dragTotal by remember { mutableFloatStateOf(0f) }
    // The threshold is a physical distance, so it must be expressed in dp and
    // converted — a raw pixel count means a different swipe on every density.
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(swipeThresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragCancel = { dragTotal = 0f },
                    onDragEnd = {
                        if (dragTotal <= -swipeThresholdPx) onExpand()
                        dragTotal = 0f
                    },
                ) { _, delta -> dragTotal += delta }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 24.dp)
                .graphicsLayer {
                    alpha = progress
                    translationY = -(1f - progress) * size.height * 0.4f
                },
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
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

            QuoteText(
                quote = uiState.displayQuote,
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
                        contentDescription = plainQuote(uiState.displayQuote)
                    },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = uiState.displaySubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_SUBTITLE),
            )

            if (staleSinceMillis != null) {
                Spacer(Modifier.height(12.dp))
                StaleBadge(staleSinceMillis)
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
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

        // Top controls fade out while the panel is up.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .graphicsLayer { alpha = progress },
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
    val transition = rememberInfiniteTransition(label = "chevron")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) -6f else 0f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "chevron-offset",
    )
    Icon(
        painter = painterResource(R.drawable.ic_chevron_up),
        contentDescription = stringResource(R.string.show_details),
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { translationY = offset },
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

/** Matches the web app's 50px swipe threshold. */
internal val SWIPE_THRESHOLD = 56.dp

internal const val EXPLODE_TAP_COUNT = 5
internal val SHEET_PEEK_HEIGHT = 56.dp

const val TAG_QUOTE = "home-quote"
const val TAG_SUBTITLE = "home-subtitle"
const val TAG_TEMPERATURE = "home-temperature"
const val TAG_LOCATION = "home-location"
const val TAG_EXPAND = "home-expand"
const val TAG_COLLAPSE = "home-collapse"
const val TAG_STALE = "home-stale"
