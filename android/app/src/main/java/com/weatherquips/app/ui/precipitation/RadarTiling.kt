package com.weatherquips.app.ui.precipitation

import com.weatherquips.app.domain.repository.RadarTile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** A radar tile and the screen rectangle it is drawn into, in pixels. */
data class PlacedTile(
    val tile: RadarTile,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Which radar tiles cover the map, and where each one goes.
 *
 * Pure arithmetic on osmdroid's own numbers — the visible area in world
 * pixels and the world's size at the current zoom — so it is exact at any
 * fractional zoom and testable without a map.
 */
object RadarTiling {

    /** A ceiling on tiles per frame, against a degenerate viewport. */
    const val MAX_TILES = 64

    /**
     * The radar zoom for a map zoom: the whole level at or below it — what
     * osmdroid does for the base map, and plenty for data that is about a
     * kilometre per pixel to begin with — but never deeper than [maxZoom].
     * Past that the same tiles are drawn larger, which is blurrier but true,
     * rather than asking for tiles the source does not have.
     */
    fun zoomFor(mapZoom: Double, maxZoom: Int): Int =
        floor(mapZoom).toInt().coerceIn(0, maxZoom)

    /**
     * The tiles covering a viewport, nearest the centre first so the middle
     * of the screen fills in before the edges.
     *
     * @param viewLeft the visible area in world pixels at the map's zoom
     *                 (osmdroid's "mercator" viewport). Horizontally it may
     *                 lie outside the first copy of the world when the map
     *                 wraps; tile columns are wrapped to match.
     * @param worldSize the width of the world in pixels at the map's zoom.
     * @param screenLeft where [viewLeft] sits on screen.
     */
    fun visible(
        viewLeft: Long,
        viewTop: Long,
        viewRight: Long,
        viewBottom: Long,
        worldSize: Double,
        zoom: Int,
        screenLeft: Int = 0,
        screenTop: Int = 0,
    ): List<PlacedTile> {
        if (worldSize <= 0.0 || viewRight <= viewLeft || viewBottom <= viewTop) return emptyList()
        val count = 1 shl zoom
        val span = worldSize / count

        val firstColumn = floor(viewLeft / span).toLong()
        val lastColumn = floor((viewRight - 1) / span).toLong()
        // No wrapping north–south: past the poles there is no radar.
        val firstRow = max(0L, floor(viewTop / span).toLong())
        val lastRow = min(count - 1L, floor((viewBottom - 1) / span).toLong())
        if (firstRow > lastRow) return emptyList()

        val centreX = (viewLeft + viewRight) / 2.0
        val centreY = (viewTop + viewBottom) / 2.0
        val tiles = ArrayList<PlacedTile>()
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                // Edges are rounded from the world position, not from the
                // previous tile's width, so neighbours always share an edge:
                // no hairline seams, no overlap.
                val left = screenLeft + (column * span - viewLeft).roundToLong().toInt()
                val right = screenLeft + ((column + 1) * span - viewLeft).roundToLong().toInt()
                val top = screenTop + (row * span - viewTop).roundToLong().toInt()
                val bottom = screenTop + ((row + 1) * span - viewTop).roundToLong().toInt()
                tiles += PlacedTile(
                    tile = RadarTile(zoom, Math.floorMod(column, count.toLong()).toInt(), row.toInt()),
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                )
            }
        }
        return tiles
            .sortedBy { placed ->
                val dx = (placed.left + placed.right) / 2.0 - (screenLeft + centreX - viewLeft)
                val dy = (placed.top + placed.bottom) / 2.0 - (screenTop + centreY - viewTop)
                dx * dx + dy * dy
            }
            .take(MAX_TILES)
    }

    /**
     * Where a deeper tile's area lies inside one of its ancestors, as a
     * fraction of the ancestor's width: (left, top, size). Used to stand an
     * already-loaded coarser tile in for one that has not arrived yet.
     */
    fun portionOfAncestor(tile: RadarTile, levelsUp: Int): Triple<Double, Double, Double> {
        val divisions = 1 shl levelsUp
        val size = 1.0 / divisions
        val left = Math.floorMod(tile.x, divisions) * size
        val top = Math.floorMod(tile.y, divisions) * size
        return Triple(left, top, size)
    }

    /** The four tiles one level finer, in reading order: top-left, top-right, bottom-left, bottom-right. */
    fun children(tile: RadarTile): List<RadarTile> = listOf(
        RadarTile(tile.zoom + 1, tile.x * 2, tile.y * 2),
        RadarTile(tile.zoom + 1, tile.x * 2 + 1, tile.y * 2),
        RadarTile(tile.zoom + 1, tile.x * 2, tile.y * 2 + 1),
        RadarTile(tile.zoom + 1, tile.x * 2 + 1, tile.y * 2 + 1),
    )

    fun ancestor(tile: RadarTile, levelsUp: Int): RadarTile? {
        if (levelsUp <= 0 || tile.zoom - levelsUp < 0) return null
        return RadarTile(tile.zoom - levelsUp, tile.x shr levelsUp, tile.y shr levelsUp)
    }
}
