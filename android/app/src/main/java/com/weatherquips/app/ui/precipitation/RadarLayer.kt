package com.weatherquips.app.ui.precipitation

import com.weatherquips.app.domain.repository.RadarFrame
import kotlinx.coroutines.CoroutineScope
import org.osmdroid.views.MapView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ties the radar's parts to one map: the tile store, the overlay that draws
 * from it, and the bookkeeping between them — fetching for whatever is on
 * screen, most urgent frame first, and telling playback which frames are
 * ready to be shown.
 */
class RadarLayer(
    private val mapView: MapView,
    scope: CoroutineScope,
    private val playhead: RadarPlayhead,
    load: suspend (TileKey) -> ByteArray?,
    /** Main thread. Null when nothing is on screen to judge by. */
    private val onFramesReady: (Set<Int>?) -> Unit,
) {
    private val refreshPending = AtomicBoolean(false)

    val store = RadarTileStore(load = load, scope = scope, onChanged = ::onStoreChanged)

    val overlay = RadarOverlay(store, playhead, onVisibleTilesChanged = { refresh() })

    private var reported: Set<Int>? = null
    private var hasReported = false

    fun setFrames(frames: List<RadarFrame>, maxZoom: Int) {
        overlay.frames = frames
        overlay.maxZoom = maxZoom
        refresh()
        mapView.invalidate()
    }

    /**
     * Asks for what is still missing, starting from the frame playback is
     * heading to, and reports which frames are whole. Cheap enough to run on
     * every arrival: the store skips what it has or is already fetching.
     */
    fun refresh() {
        val frames = overlay.frames
        val tiles = overlay.visibleTiles
        if (frames.isEmpty() || tiles.isEmpty()) {
            report(null)
            return
        }
        val start = playhead.to.coerceIn(frames.indices)
        val order = frames.indices.map { frames[(start + it) % frames.size] }
        store.request(order.flatMap { frame -> tiles.map { TileKey(frame, it) } })
        report(store.settledFrames(frames, tiles))
    }

    private fun report(ready: Set<Int>?) {
        if (hasReported && ready == reported) return
        hasReported = true
        reported = ready
        onFramesReady(ready)
    }

    /** From any thread: repaint now, and refresh once per burst of arrivals. */
    private fun onStoreChanged() {
        mapView.postInvalidate()
        if (refreshPending.compareAndSet(false, true)) {
            mapView.post {
                refreshPending.set(false)
                refresh()
            }
        }
    }
}
