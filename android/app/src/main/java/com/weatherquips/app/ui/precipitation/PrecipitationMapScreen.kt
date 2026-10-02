package com.weatherquips.app.ui.precipitation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import com.weatherquips.app.ui.theme.Motion
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarTile
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.weatherquips.app.ui.theme.LocalAccents
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native precipitation radar: an osmdroid map with RainViewer frames as a real
 * tile overlay — no WebView, no Leaflet.
 *
 * Lifecycle is handled strictly: the map is paused with the screen, the
 * animation is stopped whenever the screen is not resumed, and the MapView is
 * detached on disposal so no tile thread or bitmap outlives it.
 */
@Composable
fun PrecipitationMapScreen(
    coordinates: Coordinates,
    uiState: RadarUiState,
    onTogglePlay: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onPauseForLifecycle: () -> Unit,
    onResumeForLifecycle: () -> Unit,
    loadTile: suspend (RadarFrame, RadarTile) -> ByteArray?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFramesReady: (Set<Int>?) -> Unit = {},
) {
    val darkTheme = isSystemInDarkTheme()
    val markerColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val userMarkerColor = LocalAccents.current.cold.toArgb()
    val userOutlineColor = Color.White.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.background

    // Restored across rotation so the user keeps their pan/zoom.
    var savedZoom by rememberSaveable { mutableDoubleStateOf(DEFAULT_ZOOM) }
    var savedLatitude by rememberSaveable { mutableDoubleStateOf(coordinates.latitude) }
    var savedLongitude by rememberSaveable { mutableDoubleStateOf(coordinates.longitude) }

    val (mapView, markers) = rememberMapView(
        darkTheme = darkTheme,
        markerColor = markerColor,
        userMarkerColor = userMarkerColor,
        userOutlineColor = userOutlineColor,
        coordinates = coordinates,
        initialZoom = savedZoom,
        initialCenter = GeoPoint(savedLatitude, savedLongitude),
        onCameraChanged = { center, zoom ->
            savedLatitude = center.latitude
            savedLongitude = center.longitude
            savedZoom = zoom
        },
        onPause = onPauseForLifecycle,
        onResume = onResumeForLifecycle,
    )

    // What is on screen and how far it has blended: shared by the map and
    // the timeline so the two move together.
    val playhead = remember { RadarPlayhead(uiState.currentIndex) }
    val radarScope = rememberCoroutineScope()
    val currentLoadTile by rememberUpdatedState(loadTile)
    val currentOnFramesReady by rememberUpdatedState(onFramesReady)
    val radar = remember(mapView) {
        RadarLayer(
            mapView = mapView,
            scope = radarScope,
            playhead = playhead,
            load = { key -> currentLoadTile(key.frame, key.tile) },
            onFramesReady = { currentOnFramesReady(it) },
        ).also { layer ->
            // Under the markers, so the pins are never tinted by rain.
            mapView.overlays.add(0, layer.overlay)
        }
    }

    DisposableEffect(radar) {
        onDispose {
            mapView.overlays.remove(radar.overlay)
            // Nothing is waiting on this map any more.
            currentOnFramesReady(null)
        }
    }

    LaunchedEffect(radar, uiState.frames, uiState.maxTileZoom) {
        playhead.reset(uiState.currentIndex)
        radar.setFrames(uiState.frames, uiState.maxTileZoom)
    }

    // A new current frame from the view model: blend the map over to it.
    // During playback a step to the next frame takes most of the step; a tap
    // on the timeline or the loop starting over is a quick dissolve.
    LaunchedEffect(radar, uiState.currentIndex, uiState.frames) {
        val sequential = uiState.isPlaying && uiState.currentIndex == playhead.to + 1
        radar.refresh()
        playhead.moveTo(uiState.currentIndex, sequential) { mapView.invalidate() }
    }

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        AndroidView(
            factory = { mapView },
            // Applying state here rather than in a LaunchedEffect guarantees it
            // lands after the view is attached, and that every change ends in an
            // invalidate — the map does not redraw itself.
            update = { view ->
                markers.userPoint = uiState.userLocation
                    ?.let { GeoPoint(it.latitude, it.longitude) }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize().testTag(TAG_MAP),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor.copy(alpha = 0.85f))
                    .testTag(TAG_BACK),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.radar_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            uiState.userLocation?.let { userLocation ->
                IconButton(
                    onClick = {
                        mapView.controller.animateTo(
                            GeoPoint(userLocation.latitude, userLocation.longitude),
                            mapView.zoomLevelDouble,
                            RECENTER_ANIMATION_MILLIS,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor.copy(alpha = 0.85f))
                        .testTag(TAG_MY_LOCATION),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_locate),
                        contentDescription = stringResource(R.string.center_on_me),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Re-centre on the forecast location after panning around.
            IconButton(
            onClick = {
                mapView.controller.animateTo(
                    GeoPoint(coordinates.latitude, coordinates.longitude),
                    mapView.zoomLevelDouble,
                    RECENTER_ANIMATION_MILLIS,
                )
            },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor.copy(alpha = 0.85f))
                    .testTag(TAG_RECENTER),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_map_pin),
                    contentDescription = stringResource(R.string.recenter_map),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            PrecipitationLegend(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            RadarTimeline(
                uiState = uiState,
                playhead = playhead,
                onTogglePlay = onTogglePlay,
                onSelectFrame = onSelectFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Base map: standard OpenStreetMap tiles, free and key-free. */
private fun baseTileSource() = TileSourceFactory.MAPNIK

/** Desaturates the base map so the app's monochrome identity survives. */
private fun baseMapFilter(darkTheme: Boolean): ColorMatrixColorFilter {
    val matrix = ColorMatrix().apply { setSaturation(0.15f) }
    if (darkTheme) {
        // Invert luminance for a dark basemap that still reads as a map.
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    return ColorMatrixColorFilter(matrix)
}

@Composable
private fun rememberMapView(
    darkTheme: Boolean,
    markerColor: Int,
    userMarkerColor: Int,
    userOutlineColor: Int,
    coordinates: Coordinates,
    initialZoom: Double,
    initialCenter: GeoPoint,
    onCameraChanged: (GeoPoint, Double) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
): Pair<MapView, MapMarkersOverlay> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current

    val markers = remember(coordinates, markerColor, userMarkerColor, userOutlineColor) {
        MapMarkersOverlay(
            forecastPoint = GeoPoint(coordinates.latitude, coordinates.longitude),
            forecastColor = markerColor,
            userColor = userMarkerColor,
            userOutlineColor = userOutlineColor,
        )
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(baseTileSource())
            setMultiTouchControls(true)
            // The radar stops gaining detail at zoom 7 and is only scaled up
            // after that; past this it is a blur over street names.
            maxZoomLevel = MAX_MAP_ZOOM
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            overlays.add(markers)
            // The camera has to wait for a size. osmdroid cannot resolve a
            // centre on a view that has not been laid out, and the map then
            // stays blank until something else forces it to recompute the
            // projection — which is why it only appeared once it was touched.
            doOnLayout {
                controller.setZoom(initialZoom)
                controller.setCenter(initialCenter)
                invalidate()
            }
        }
    }

    LaunchedEffect(darkTheme, configuration) {
        mapView.overlayManager.tilesOverlay.setColorFilter(baseMapFilter(darkTheme))
        mapView.invalidate()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Stop the radar the moment the screen stops being visible.
                    onPause()
                    onCameraChanged(
                        GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude),
                        mapView.zoomLevelDouble,
                    )
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Navigating to this screen does not always produce a fresh ON_RESUME,
        // so start the map's tile threads explicitly rather than waiting for one.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onCameraChanged(
                GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude),
                mapView.zoomLevelDouble,
            )
            // Releases tile threads, the tile cache and every overlay.
            mapView.onDetach()
        }
    }

    return mapView to markers
}

@Composable
private fun PrecipitationLegend(modifier: Modifier = Modifier) {
    val legendDescription = stringResource(R.string.legend_description)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.legend_light),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(colorStops = LEGEND_STOPS))
                .semantics { contentDescription = legendDescription },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.legend_heavy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadarTimeline(
    uiState: RadarUiState,
    playhead: RadarPlayhead,
    onTogglePlay: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val hourFormatter = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    // The frame that dominates the picture: the clock turns over halfway
    // through a blend, when the new frame starts to win.
    val shownIndex by remember { derivedStateOf { playhead.shown } }
    val shownFrame = uiState.frames.getOrNull(shownIndex)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = onTogglePlay,
                enabled = uiState.frames.isNotEmpty(),
                modifier = Modifier.testTag(TAG_PLAY_PAUSE),
            ) {
                // The glyph turns over rather than swapping, so a tap visibly
                // did something even before the radar starts to move.
                AnimatedContent(
                    targetState = uiState.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(Motion.SHORT)) + scaleIn(Motion.pop(), initialScale = 0.4f)) togetherWith
                            (fadeOut(tween(Motion.SHORT / 2)) + scaleOut(tween(Motion.SHORT), targetScale = 0.4f))
                    },
                    label = "play-pause",
                ) { playing ->
                    Icon(
                        painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(
                            if (playing) R.string.pause_radar else R.string.play_radar,
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // The clock rolls over like an odometer as playback moves on:
            // forward frames roll up, stepping back rolls down.
            // Keyed on the timestamp, not the text, so the direction is right
            // across midnight too.
            AnimatedContent(
                targetState = shownFrame?.timeEpochSeconds,
                transitionSpec = {
                    val forward = (targetState ?: 0L) >= (initialState ?: 0L)
                    val direction = if (forward) 1 else -1
                    (
                        slideInVertically(tween(Motion.SHORT, easing = Motion.EmphasizedDecelerate)) { it * direction / 2 } +
                            fadeIn(tween(Motion.SHORT))
                        ) togetherWith (
                        slideOutVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { -it * direction / 2 } +
                            fadeOut(tween(Motion.SHORT / 2))
                        ) using SizeTransform(clip = false)
                },
                label = "radar-time",
            ) { epochSeconds ->
                Text(
                    text = epochSeconds?.let { timeFormatter.format(Date(it * 1000)) } ?: "--:--",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(TAG_CURRENT_TIME),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    uiState.isBuffering -> stringResource(R.string.radar_loading)
                    shownFrame != null && uiState.isForecast(shownIndex) ->
                        stringResource(R.string.forecast_label)
                    shownFrame != null -> stringResource(R.string.radar_label)
                    uiState.hasError -> stringResource(R.string.radar_unavailable)
                    else -> stringResource(R.string.radar_loading)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_RADAR_STATUS),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .testTag(TAG_TIMELINE),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val onSurface = MaterialTheme.colorScheme.onSurface
            uiState.frames.forEachIndexed { index, frame ->
                val isForecast = uiState.isForecast(index)
                val isPast = index < shownIndex
                val resting = onSurface.copy(
                    alpha = when {
                        isPast -> if (isForecast) 0.25f else 0.35f
                        else -> if (isForecast) 0.12f else 0.2f
                    },
                )
                val label = timeFormatter.format(Date(frame.timeEpochSeconds * 1000))
                val frameDescription = stringResource(R.string.radar_frame, label)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelectFrame(index) }
                        .semantics {
                            contentDescription = frameDescription
                            selected = index == shownIndex
                        }
                        // Read while drawing, not composing: the bars follow
                        // the blend frame by frame, so the highlight sweeps
                        // along with the rain instead of hopping a bar a step.
                        .drawBehind {
                            val weight = playhead.weight(index)
                            val height = size.height * lerp(INACTIVE_FRAME_HEIGHT, 1f, weight)
                            drawRoundRect(
                                color = lerp(resting, onSurface, weight),
                                topLeft = Offset(0f, size.height - height),
                                size = Size(size.width, height),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            uiState.frames.forEachIndexed { index, frame ->
                val hour = hourFormatter.format(Date(frame.timeEpochSeconds * 1000))
                val previousHour = uiState.frames.getOrNull(index - 1)
                    ?.let { hourFormatter.format(Date(it.timeEpochSeconds * 1000)) }
                Box(modifier = Modifier.weight(1f)) {
                    if (hour != previousHour) {
                        Text(
                            text = hour,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private const val DEFAULT_ZOOM = 7.0
private const val MAX_MAP_ZOOM = 12.0
private const val RECENTER_ANIMATION_MILLIS = 500L

/**
 * The colours RainViewer's "Universal Blue" scheme (the `/2/` in the tile
 * URL) actually paints, from its published colour table: beige drizzle, blue
 * rain from light to dark, then yellow, orange, red and magenta for the
 * heavy end. The bands meet at hard edges, as they do on the radar, rather
 * than blending through colours the radar never shows.
 *
 * The old legend ran green to yellow to red to blue, which read heavy rain as
 * the lightest colour on the map.
 */
private val LEGEND_STOPS = arrayOf(
    0.00f to Color(0xFFCEC087), // 10 dBZ, drizzle
    0.16f to Color(0xFFDED097),
    0.16f to Color(0xFF88DDEE), // 15 dBZ, light rain
    0.44f to Color(0xFF004768), // 34 dBZ
    0.44f to Color(0xFFFFEE00), // 35 dBZ, heavy
    0.62f to Color(0xFFFF8100),
    0.62f to Color(0xFFFF4400), // 45 dBZ, very heavy
    0.82f to Color(0xFF8F0000),
    0.82f to Color(0xFFFFAAFF), // 55 dBZ, extreme
    1.00f to Color(0xFFFF4EFF),
)

private const val INACTIVE_FRAME_HEIGHT = 0.6f

const val TAG_MAP = "precipitation-map"
const val TAG_BACK = "precipitation-back"
const val TAG_PLAY_PAUSE = "precipitation-play-pause"
const val TAG_CURRENT_TIME = "precipitation-current-time"
const val TAG_RADAR_STATUS = "precipitation-status"
const val TAG_TIMELINE = "precipitation-timeline"
const val TAG_RECENTER = "precipitation-recenter"
const val TAG_MY_LOCATION = "precipitation-my-location"
