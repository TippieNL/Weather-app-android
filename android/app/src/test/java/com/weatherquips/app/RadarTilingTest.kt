package com.weatherquips.app

import com.weatherquips.app.domain.repository.RadarTile
import com.weatherquips.app.ui.precipitation.RadarPlayhead
import com.weatherquips.app.ui.precipitation.RadarTiling
import com.weatherquips.app.ui.precipitation.blendWeights
import com.weatherquips.app.ui.precipitation.PrecipitationViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which radar tiles cover the map, and how two frames are mixed. */
class RadarTilingTest {

    // --- zoom -------------------------------------------------------------

    @Test
    fun `the radar never asks for a zoom the source does not render`() {
        assertEquals(7, RadarTiling.zoomFor(mapZoom = 7.0, maxZoom = 7))
        assertEquals(7, RadarTiling.zoomFor(mapZoom = 9.4, maxZoom = 7))
        assertEquals(7, RadarTiling.zoomFor(mapZoom = 12.0, maxZoom = 7))
    }

    @Test
    fun `below the limit it uses the level at or below the map, like the base map`() {
        assertEquals(6, RadarTiling.zoomFor(mapZoom = 6.0, maxZoom = 7))
        assertEquals(6, RadarTiling.zoomFor(mapZoom = 6.99, maxZoom = 7))
        assertEquals(3, RadarTiling.zoomFor(mapZoom = 3.2, maxZoom = 7))
        assertEquals(0, RadarTiling.zoomFor(mapZoom = -1.0, maxZoom = 7))
    }

    // --- covering a viewport ------------------------------------------------

    @Test
    fun `a viewport on a tile grid gets exactly those tiles, edge to edge`() {
        // World of 4x4 tiles at 256px; the view covers columns 1-2, rows 1-2.
        val placed = RadarTiling.visible(256, 256, 768, 768, worldSize = 1024.0, zoom = 2)
        assertEquals(
            setOf(RadarTile(2, 1, 1), RadarTile(2, 2, 1), RadarTile(2, 1, 2), RadarTile(2, 2, 2)),
            placed.map { it.tile }.toSet(),
        )
        val topLeft = placed.single { it.tile == RadarTile(2, 1, 1) }
        assertEquals(listOf(0, 0, 256, 256), listOf(topLeft.left, topLeft.top, topLeft.right, topLeft.bottom))
    }

    @Test
    fun `a tile at a fractional zoom is scaled, and neighbours still meet exactly`() {
        // Zoom 7.3: one zoom-7 tile spans 256 * 2^0.3 ≈ 315.2 px.
        val worldSize = 256 * Math.pow(2.0, 7.3)
        val placed = RadarTiling.visible(10_000, 6_000, 11_080, 8_400, worldSize, zoom = 7)
        val rows = placed.groupBy { it.tile.y }
        rows.values.forEach { row ->
            val sorted = row.sortedBy { it.left }
            sorted.zipWithNext().forEach { (a, b) ->
                assertEquals("a seam or an overlap between ${a.tile} and ${b.tile}", a.right, b.left)
            }
        }
        placed.forEach { assertTrue(it.right - it.left in 315..316) }
    }

    @Test
    fun `the screen is covered completely`() {
        val worldSize = 256 * Math.pow(2.0, 7.5)
        val placed = RadarTiling.visible(5_000, 3_000, 6_080, 5_400, worldSize, zoom = 7, screenLeft = 0, screenTop = 0)
        assertTrue("dropped tiles to the cap", placed.size < RadarTiling.MAX_TILES)
        assertTrue(placed.minOf { it.left } <= 0)
        assertTrue(placed.minOf { it.top } <= 0)
        assertTrue(placed.maxOf { it.right } >= 1_080)
        assertTrue(placed.maxOf { it.bottom } >= 2_400)
    }

    @Test
    fun `across the date line the columns wrap but stay where they are drawn`() {
        // The view starts half a tile west of longitude -180.
        val placed = RadarTiling.visible(-128, 0, 384, 256, worldSize = 1024.0, zoom = 2)
        val wrapped = placed.single { it.tile.x == 3 }
        assertEquals(-128, wrapped.left)
        assertEquals(128, wrapped.right)
        assertTrue(placed.any { it.tile.x == 0 && it.left == 128 })
    }

    @Test
    fun `nothing is asked for past the poles`() {
        val placed = RadarTiling.visible(0, -500, 512, 300, worldSize = 1024.0, zoom = 2)
        assertTrue(placed.all { it.tile.y in 0..3 })
        assertTrue(placed.none { it.top < -256 })
    }

    @Test
    fun `the middle of the screen fills in first`() {
        val placed = RadarTiling.visible(0, 0, 1024, 1024, worldSize = 1024.0, zoom = 2)
        val first = placed.first().tile
        assertTrue("started at $first", first.x in 1..2 && first.y in 1..2)
    }

    @Test
    fun `a degenerate viewport cannot ask for the whole world`() {
        val placed = RadarTiling.visible(0, 0, 1 shl 20, 1 shl 20, worldSize = (1 shl 20).toDouble(), zoom = 7)
        assertEquals(RadarTiling.MAX_TILES, placed.size)
        assertTrue(RadarTiling.visible(0, 0, 0, 100, worldSize = 1024.0, zoom = 2).isEmpty())
    }

    @Test
    fun `a coarser tile can stand in for a finer one`() {
        val tile = RadarTile(7, 67, 43)
        assertEquals(RadarTile(6, 33, 21), RadarTiling.ancestor(tile, 1))
        assertEquals(RadarTile(5, 16, 10), RadarTiling.ancestor(tile, 2))
        assertNull(RadarTiling.ancestor(RadarTile(0, 0, 0), 1))
        // 67 is the right half and 43 the bottom half of the zoom-6 tile.
        assertEquals(Triple(0.5, 0.5, 0.5), RadarTiling.portionOfAncestor(tile, 1))
    }

    @Test
    fun `a tile's four finer tiles come in reading order`() {
        assertEquals(
            listOf(RadarTile(8, 134, 86), RadarTile(8, 135, 86), RadarTile(8, 134, 87), RadarTile(8, 135, 87)),
            RadarTiling.children(RadarTile(7, 67, 43)),
        )
    }

    // --- mixing two frames --------------------------------------------------

    @Test
    fun `two loaded frames are mixed, one missing frame is never a hole`() {
        assertEquals(0.75f to 0.25f, blendWeights(hasFrom = true, hasTo = true, blend = 0.25f))
        assertEquals(1f to 0f, blendWeights(hasFrom = true, hasTo = false, blend = 0.25f))
        assertEquals(0f to 1f, blendWeights(hasFrom = false, hasTo = true, blend = 0.25f))
        assertEquals(0f to 0f, blendWeights(hasFrom = false, hasTo = false, blend = 0.25f))
    }

    @Test
    fun `the playhead weighs the frames and hands the clock over halfway`() = runBlocking {
        val playhead = RadarPlayhead(0)
        assertEquals(1f, playhead.weight(0))
        assertEquals(0f, playhead.weight(1))

        playhead.showBlend(from = 3, to = 4, blend = 0.3f)
        assertEquals(0.7f, playhead.weight(3), 0.0001f)
        assertEquals(0.3f, playhead.weight(4), 0.0001f)
        assertEquals(0f, playhead.weight(5))
        assertEquals(3, playhead.shown)

        playhead.showBlend(from = 3, to = 4, blend = 0.6f)
        assertEquals(4, playhead.shown)
    }

    @Test
    fun `a blend finishes inside its playback step`() {
        // Otherwise every step would cut the previous blend short.
        assertTrue(RadarPlayhead.STEP_BLEND_MILLIS < PrecipitationViewModel.FRAME_INTERVAL_MILLIS)
        assertTrue(RadarPlayhead.JUMP_BLEND_MILLIS < PrecipitationViewModel.FRAME_INTERVAL_MILLIS)
    }
}
