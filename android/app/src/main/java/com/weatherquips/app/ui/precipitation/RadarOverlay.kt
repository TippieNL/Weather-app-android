package com.weatherquips.app.ui.precipitation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarTile
import org.osmdroid.util.RectL
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.roundToInt

/**
 * The radar, drawn straight from [RadarTileStore] as two frames blended.
 *
 * The blend is exact: both frames go into an offscreen layer — the outgoing
 * one at (1 − t), the incoming one *added* at t — and the layer is laid over
 * the map at the radar's opacity. Drawing the two semi-transparent frames
 * straight onto the map instead would dim any rain that is in both of them
 * halfway through every step, and the whole radar would pulse.
 */
class RadarOverlay(
    private val store: RadarTileStore,
    private val picture: RadarPicture,
    /** The tiles on screen changed: time to fetch for the new area. Main thread. */
    private val onVisibleTilesChanged: (List<RadarTile>) -> Unit,
) : Overlay() {

    var frames: List<RadarFrame> = emptyList()
    var maxZoom: Int = 0

    /** The tiles drawn last, at the zoom drawn last. */
    var visibleTiles: List<RadarTile> = emptyList()
        private set

    private val viewport = RectL()
    private val layerBounds = RectF()
    private val source = Rect()
    private val target = Rect()
    private val destination = Rect()

    private val outgoing = Paint(Paint.FILTER_BITMAP_FLAG)
    private val incoming = Paint(Paint.FILTER_BITMAP_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || frames.isEmpty()) return
        val projection = mapView.projection
        projection.getMercatorViewPort(viewport)
        val screen = projection.intrinsicScreenRect
        val zoom = RadarTiling.zoomFor(projection.zoomLevel, maxZoom)
        val placed = RadarTiling.visible(
            viewLeft = viewport.left,
            viewTop = viewport.top,
            viewRight = viewport.right,
            viewBottom = viewport.bottom,
            worldSize = projection.worldMapSize,
            zoom = zoom,
            screenLeft = screen.left,
            screenTop = screen.top,
        )
        noteVisible(mapView, placed)

        val from = picture.from.coerceIn(frames.indices)
        val to = picture.to.coerceIn(frames.indices)
        val blend = if (from == to) 1f else picture.blend

        layerBounds.set(screen)
        val layer = canvas.saveLayerAlpha(layerBounds, RADAR_ALPHA)
        for (tile in placed) {
            target.set(tile.left, tile.top, tile.right, tile.bottom)
            if (from == to) {
                // At rest. A tile that never arrived shows the last thing
                // known there rather than a hole.
                val parts = find(to, tile.tile, framesBack = LOOK_BACK_FRAMES)
                outgoing.alpha = 255
                parts.forEach { drawPart(canvas, it, outgoing) }
                continue
            }
            // Mid-blend. The outgoing side may borrow from earlier frames for
            // the same reason; the incoming side is the frame itself or nothing.
            val old = find(from, tile.tile, framesBack = LOOK_BACK_FRAMES)
            val new = find(to, tile.tile, framesBack = 0)
            val (oldWeight, newWeight) = blendWeights(old.isNotEmpty(), new.isNotEmpty(), blend)
            var drewOld = false
            if (old.isNotEmpty() && oldWeight > 0f) {
                outgoing.alpha = (oldWeight * 255).roundToInt()
                old.forEach { drawPart(canvas, it, outgoing) }
                drewOld = true
            }
            if (new.isNotEmpty() && newWeight > 0f) {
                incoming.alpha = (newWeight * 255).roundToInt()
                // Added onto the outgoing frame, not laid over it: that is
                // what makes the blend a true mix of the two.
                incoming.xfermode = if (drewOld) add else null
                new.forEach { drawPart(canvas, it, incoming) }
            }
        }
        canvas.restoreToCount(layer)

        prepareAhead(to, placed)
    }

    /**
     * Something to draw for a tile: a bitmap, the part of it to take, and the
     * part of the tile it covers, as fractions. One shape serves the tile
     * itself, a cropped coarser tile and a quarter-size finer one.
     */
    private class Part(
        val bitmap: Bitmap,
        val sourceLeft: Double = 0.0,
        val sourceTop: Double = 0.0,
        val sourceSize: Double = 1.0,
        val targetLeft: Double = 0.0,
        val targetTop: Double = 0.0,
        val targetSize: Double = 1.0,
    )

    /**
     * The best picture of [tile] for frame [index], looking back up to
     * [framesBack] frames: the tile itself; else, just after a zoom, the
     * tiles of the level the map came from — four finer ones after zooming
     * out, a coarser one cropped after zooming in — so a change of zoom
     * never empties the radar while the new level downloads.
     */
    private fun find(index: Int, tile: RadarTile, framesBack: Int): List<Part> {
        for (back in 0..framesBack) {
            val frame = frames.getOrNull(index - back) ?: break
            store.bitmap(TileKey(frame, tile))?.let { return listOf(Part(it)) }

            val finer = RadarTiling.children(tile).mapIndexedNotNull { quarter, child ->
                store.peek(TileKey(frame, child))?.let {
                    Part(it, targetLeft = (quarter % 2) * 0.5, targetTop = (quarter / 2) * 0.5, targetSize = 0.5)
                }
            }
            if (finer.size == 4) return finer

            for (up in 1..ANCESTOR_LEVELS) {
                val ancestor = RadarTiling.ancestor(tile, up) ?: break
                val bitmap = store.peek(TileKey(frame, ancestor)) ?: continue
                val (left, top, size) = RadarTiling.portionOfAncestor(tile, up)
                return listOf(Part(bitmap, sourceLeft = left, sourceTop = top, sourceSize = size))
            }
            if (finer.isNotEmpty()) return finer
        }
        return emptyList()
    }

    private fun drawPart(canvas: Canvas, part: Part, paint: Paint) {
        val width = part.bitmap.width
        val height = part.bitmap.height
        source.set(
            (part.sourceLeft * width).roundToInt(),
            (part.sourceTop * height).roundToInt(),
            ((part.sourceLeft + part.sourceSize) * width).roundToInt(),
            ((part.sourceTop + part.sourceSize) * height).roundToInt(),
        )
        val tileWidth = target.width()
        val tileHeight = target.height()
        destination.set(
            target.left + (part.targetLeft * tileWidth).roundToInt(),
            target.top + (part.targetTop * tileHeight).roundToInt(),
            target.left + ((part.targetLeft + part.targetSize) * tileWidth).roundToInt(),
            target.top + ((part.targetTop + part.targetSize) * tileHeight).roundToInt(),
        )
        canvas.drawBitmap(part.bitmap, source, destination, paint)
    }

    /** Decodes the next frame while this one is showing, so the step never waits. */
    private fun prepareAhead(current: Int, placed: List<PlacedTile>) {
        val next = frames.getOrNull((current + 1) % frames.size) ?: return
        val now = frames[current]
        placed.forEach {
            store.prepare(TileKey(now, it.tile))
            store.prepare(TileKey(next, it.tile))
        }
    }

    private fun noteVisible(mapView: MapView, placed: List<PlacedTile>) {
        val tiles = placed.map { it.tile }
        if (tiles == visibleTiles) return
        visibleTiles = tiles
        store.ensureCapacity(tiles.size)
        // Not from inside draw: fetching starts work and may change state.
        mapView.post { onVisibleTilesChanged(tiles) }
    }

    companion object {
        /** The web app's 0.6 radar opacity over the base map. */
        const val RADAR_ALPHA = 153

        /** How far back in time a missing tile may borrow from. */
        const val LOOK_BACK_FRAMES = 3

        /** How many zoom levels up a stand-in tile may come from. */
        const val ANCESTOR_LEVELS = 2
    }
}
