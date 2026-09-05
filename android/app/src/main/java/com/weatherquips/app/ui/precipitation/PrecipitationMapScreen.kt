package com.weatherquips.app.ui.precipitation

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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
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
    tileUrlFor: (RadarFrame) -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val markerColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.background

    // Restored across rotation so the user keeps their pan/zoom.
    var savedZoom by rememberSaveable { mutableDoubleStateOf(DEFAULT_ZOOM) }
    var savedLatitude by rememberSaveable { mutableDoubleStateOf(coordinates.latitude) }
    var savedLongitude by rememberSaveable { mutableDoubleStateOf(coordinates.longitude) }

    val mapView = rememberMapView(
        darkTheme = darkTheme,
        markerColor = markerColor,
        coordinates = coordinates,
        initialZoom = savedZoom,
        initialCenter = GeoPoint(savedLatitude, savedLongitude),
        onCameraChanged = { center, zoom ->
            savedLatitude = center.latitude
            savedLongitude = center.longitude
            savedZoom = zoom
        },
        onPause = onPauseForLifecycle,
    )

    val radarOverlay = remember(mapView) { createRadarOverlay(mapView) }

    // Swapping the tile source on one provider keeps memory flat: osmdroid's
    // disk cache makes the loop smooth after the first pass, and no per-frame
    // overlay stack is left behind to leak.
    LaunchedEffect(uiState.currentFrame, radarOverlay) {
        val frame = uiState.currentFrame ?: return@LaunchedEffect
        radarOverlay.provider.setTileSource(
            RadarTileSource(frameName = "rainviewer-${frame.timeEpochSeconds}", urlTemplate = tileUrlFor(frame)),
        )
        radarOverlay.overlay.isEnabled = true
        mapView.invalidate()
    }

    DisposableEffect(radarOverlay) {
        onDispose {
            radarOverlay.overlay.onDetach(mapView)
            radarOverlay.provider.detach()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        AndroidView(
            factory = { mapView },
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
                text = stringResource(R.string.precipitation_forecast),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
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

private data class RadarOverlayHandle(
    val provider: MapTileProviderBasic,
    val overlay: TilesOverlay,
)

private fun createRadarOverlay(mapView: MapView): RadarOverlayHandle {
    val provider = MapTileProviderBasic(mapView.context)
    val overlay = TilesOverlay(provider, mapView.context).apply {
        loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        loadingLineColor = android.graphics.Color.TRANSPARENT
        // Matches the web app's 0.6 radar opacity over the base map.
        setColorFilter(radarOpacityFilter())
        isEnabled = false
    }
    mapView.overlays.add(overlay)
    return RadarOverlayHandle(provider, overlay)
}

/** 60 % alpha, applied through a colour matrix so no bitmap has to be recreated. */
private fun radarOpacityFilter(): ColorMatrixColorFilter =
    ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.6f, 0f,
            ),
        ),
    )

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
    coordinates: Coordinates,
    initialZoom: Double,
    initialCenter: GeoPoint,
    onCameraChanged: (GeoPoint, Double) -> Unit,
    onPause: () -> Unit,
): MapView {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(baseTileSource())
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter)
            overlays.add(
                LocationMarkerOverlay(
                    point = GeoPoint(coordinates.latitude, coordinates.longitude),
                    color = markerColor,
                    haloColor = markerColor,
                ),
            )
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
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
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

    return mapView
}

@Composable
private fun PrecipitationLegend(modifier: Modifier = Modifier) {
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
                .background(Brush.horizontalGradient(LEGEND_COLORS))
                .semantics { contentDescription = "Precipitation intensity scale, light to heavy" },
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
    onTogglePlay: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val hourFormatter = remember { SimpleDateFormat("HH", Locale.getDefault()) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = onTogglePlay,
                enabled = uiState.frames.isNotEmpty(),
                modifier = Modifier.testTag(TAG_PLAY_PAUSE),
            ) {
                Icon(
                    painter = painterResource(
                        if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = stringResource(
                        if (uiState.isPlaying) R.string.pause_radar else R.string.play_radar,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = uiState.currentFrame
                    ?.let { timeFormatter.format(Date(it.timeEpochSeconds * 1000)) }
                    ?: "--:--",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(TAG_CURRENT_TIME),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    uiState.currentFrame != null && uiState.isForecast(uiState.currentIndex) ->
                        stringResource(R.string.forecast_label)
                    uiState.currentFrame != null -> stringResource(R.string.radar_label)
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
            uiState.frames.forEachIndexed { index, frame ->
                val isActive = index == uiState.currentIndex
                val isPast = index < uiState.currentIndex
                val isForecast = uiState.isForecast(index)
                val onSurface = MaterialTheme.colorScheme.onSurface
                val color = when {
                    isActive -> onSurface
                    isPast -> onSurface.copy(alpha = if (isForecast) 0.25f else 0.35f)
                    else -> onSurface.copy(alpha = if (isForecast) 0.12f else 0.2f)
                }
                val label = hourFormatter.format(Date(frame.timeEpochSeconds * 1000))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                        .clickable { onSelectFrame(index) }
                        .semantics {
                            contentDescription = "Radar frame $label:00"
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

private val LEGEND_COLORS = listOf(
    Color(0xFF88FF88),
    Color(0xFFFFFF00),
    Color(0xFFFF8800),
    Color(0xFFFF0000),
    Color(0xFFCC00CC),
    Color(0xFF0000FF),
)

const val TAG_MAP = "precipitation-map"
const val TAG_BACK = "precipitation-back"
const val TAG_PLAY_PAUSE = "precipitation-play-pause"
const val TAG_CURRENT_TIME = "precipitation-current-time"
const val TAG_RADAR_STATUS = "precipitation-status"
const val TAG_TIMELINE = "precipitation-timeline"
