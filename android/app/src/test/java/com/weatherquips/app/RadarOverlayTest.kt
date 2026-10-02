package com.weatherquips.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarTile
import com.weatherquips.app.ui.precipitation.RadarOverlay
import com.weatherquips.app.ui.precipitation.RadarPicture
import com.weatherquips.app.ui.precipitation.RadarTileStore
import com.weatherquips.app.ui.precipitation.TileKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sinh

/**
 * The radar overlay, drawn to pixels and checked against osmdroid's own idea
 * of where things are. Also writes PNGs of a blend for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RadarOverlayTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * Plain fields, not the Compose-backed playhead: writing Compose state
     * from a test with no Compose rule leaves later Compose tests in the same
     * JVM waiting forever for changes nobody applies.
     */
    private class Still(
        override var from: Int = 0,
        override var to: Int = 0,
        override var blend: Float = 1f,
    ) : RadarPicture {
        fun show(from: Int, to: Int, blend: Float) {
            this.from = from
            this.to = to
            this.blend = blend
        }
    }

    @After
    fun tearDown() = scope.cancel()

    private val frames = listOf(
        RadarFrame("/v2/radar/a", 1_000),
        RadarFrame("/v2/radar/b", 1_600),
        RadarFrame("/v2/radar/c", 2_200),
    )

    private val mapWidth = 822
    private val mapHeight = 1_400

    private fun mapView(zoom: Double, centre: GeoPoint): MapView =
        MapView(ApplicationProvider.getApplicationContext()).apply {
            isTilesScaledToDpi = true
            measure(
                View.MeasureSpec.makeMeasureSpec(mapWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mapHeight, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, mapWidth, mapHeight)
            controller.setZoom(zoom)
            controller.setCenter(centre)
        }

    private fun png(paint: (Canvas) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        paint(Canvas(bitmap))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun solid(color: Int) = png { it.drawColor(color) }

    /**
     * A store that answers at once and decodes inline, so a draw sees what
     * was loaded without any waiting.
     */
    private fun store(tiles: (TileKey) -> ByteArray?) = RadarTileStore(
        load = { tiles(it) },
        scope = scope,
        onChanged = {},
        decodeDispatcher = Dispatchers.Unconfined,
    )

    /**
     * Draws over white, loading every frame for what is on screen first —
     * the first draw finds the tiles, the next decodes them, the last is the
     * picture.
     */
    private fun render(
        map: MapView,
        store: RadarTileStore,
        playhead: RadarPicture,
        load: List<RadarFrame> = frames,
    ): Bitmap {
        val overlay = RadarOverlay(store, playhead, onVisibleTilesChanged = {}).apply {
            this.frames = this@RadarOverlayTest.frames
            maxZoom = 7
        }
        val scratch = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888)
        overlay.draw(Canvas(scratch), map, false)
        store.request(load.flatMap { frame -> overlay.visibleTiles.map { TileKey(frame, it) } })
        overlay.draw(Canvas(scratch), map, false)
        val out = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        overlay.draw(canvas, map, false)
        return out
    }

    /** The geographic point at a position in tile space: (66.5, 41.5) is the middle of tile 66/41. */
    private fun geo(tileX: Double, tileY: Double, zoom: Int): GeoPoint {
        val n = 1 shl zoom
        val longitude = tileX / n * 360.0 - 180.0
        val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * tileY / n))))
        return GeoPoint(latitude, longitude)
    }

    private fun Bitmap.at(map: MapView, point: GeoPoint): Int {
        val screen = map.projection.toPixels(point, Point())
        return getPixel(screen.x, screen.y)
    }

    private fun assertColour(message: String, expected: Int, actual: Int, tolerance: Int = 3) {
        val channels = listOf(Color::red, Color::green, Color::blue)
        val off = channels.maxOf { abs(it(expected) - it(actual)) }
        assertTrue(
            "$message: expected #${Integer.toHexString(expected)} but was #${Integer.toHexString(actual)}",
            off <= tolerance,
        )
    }

    /** 60 % of [colour] over white, which is how the radar sits on the map. */
    private fun overWhite(colour: Int, opacity: Float = 0.6f): Int = Color.rgb(
        (Color.red(colour) * opacity + 255 * (1 - opacity)).toInt(),
        (Color.green(colour) * opacity + 255 * (1 - opacity)).toInt(),
        (Color.blue(colour) * opacity + 255 * (1 - opacity)).toInt(),
    )

    // --- where tiles land -----------------------------------------------------

    @Test
    fun `tiles land exactly where osmdroid puts the places they show`() {
        // Tile 66/41 red, everything else green, at a fractional zoom.
        val target = RadarTile(7, 66, 41)
        val store = store { key -> if (key.tile == target) solid(Color.RED) else solid(Color.GREEN) }
        val map = mapView(zoom = 7.3, centre = geo(66.5, 41.5, 7))
        val pixels = render(map, store, Still())

        val red = overWhite(Color.RED)
        val green = overWhite(Color.GREEN)
        // Just inside each edge of the tile, and just outside it.
        listOf(0.04 to 0.5, 0.96 to 0.5, 0.5 to 0.04, 0.5 to 0.96, 0.5 to 0.5).forEach { (dx, dy) ->
            assertColour("inside at $dx,$dy", red, pixels.at(map, geo(66 + dx, 41 + dy, 7)))
        }
        listOf(-0.04 to 0.5, 1.04 to 0.5, 0.5 to -0.04, 0.5 to 1.04).forEach { (dx, dy) ->
            assertColour("outside at $dx,$dy", green, pixels.at(map, geo(66 + dx, 41 + dy, 7)))
        }
    }

    @Test
    fun `zoomed past the source's limit the same tiles are drawn larger`() {
        val target = RadarTile(7, 66, 41)
        val asked = mutableSetOf<Int>()
        val store = store { key ->
            asked += key.tile.zoom
            if (key.tile == target) solid(Color.RED) else solid(Color.GREEN)
        }
        val map = mapView(zoom = 10.2, centre = geo(66.3, 41.6, 7))
        val pixels = render(map, store, Still())

        assertEquals(setOf(7), asked)
        assertColour("inside", overWhite(Color.RED), pixels.at(map, geo(66.3, 41.6, 7)))
    }

    // --- blending ---------------------------------------------------------------

    @Test
    fun `rain in both frames does not dim halfway through the blend`() {
        // Two semi-transparent frames drawn over each other would show the
        // map through both at once, and every step of the animation would
        // pulse. The blend has to come out as strong as either frame alone.
        val store = store { solid(Color.RED) }
        val map = mapView(zoom = 7.0, centre = GeoPoint(52.0, 5.5))
        val playhead = Still()
        val centre = GeoPoint(52.0, 5.5)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { blend ->
            playhead.show(from = 0, to = 1, blend = blend)
            assertColour("at blend $blend", overWhite(Color.RED), render(map, store, playhead).at(map, centre))
        }
    }

    @Test
    fun `rain in one frame only fades in proportion`() {
        val store = store { key -> if (key.frame == frames[0]) solid(Color.RED) else solid(Color.TRANSPARENT) }
        val map = mapView(zoom = 7.0, centre = GeoPoint(52.0, 5.5))
        val playhead = Still()
        val centre = GeoPoint(52.0, 5.5)

        playhead.show(from = 0, to = 1, blend = 0.5f)
        assertColour("halfway", overWhite(Color.RED, opacity = 0.3f), render(map, store, playhead).at(map, centre))
        playhead.show(from = 0, to = 1, blend = 1f)
        assertColour("done", Color.WHITE, render(map, store, playhead).at(map, centre))
    }

    // --- never a hole ---------------------------------------------------------

    @Test
    fun `a frame that has not arrived shows the last one that has, not an empty map`() {
        // The original bug: on every step the radar was gone until the new
        // frame's tiles came in. Now the old picture stays until then.
        val store = store { solid(Color.BLUE) }
        val map = mapView(zoom = 7.0, centre = GeoPoint(52.0, 5.5))
        val playhead = Still()
        val centre = GeoPoint(52.0, 5.5)
        val blue = overWhite(Color.BLUE)

        // Only the first frame loaded; playback has moved on twice.
        playhead.show(from = 0, to = 1, blend = 0.5f)
        assertColour("mid-blend", blue, render(map, store, playhead, load = frames.take(1)).at(map, centre))
        playhead.show(from = 2, to = 2, blend = 1f)
        assertColour("at rest", blue, render(map, store, playhead, load = frames.take(1)).at(map, centre))
    }

    @Test
    fun `zooming in borrows the coarser tiles until the finer ones arrive`() {
        val store = store { key -> if (key.tile.zoom == 6) solid(Color.MAGENTA) else null }
        // Load the zoom-6 tiles first, as a map that was just zoomed out on would have.
        val coarse = mapView(zoom = 6.0, centre = GeoPoint(52.0, 5.5))
        render(coarse, store, Still())

        val map = mapView(zoom = 7.0, centre = GeoPoint(52.0, 5.5))
        val overlay = RadarOverlay(store, Still(), onVisibleTilesChanged = {}).apply {
            frames = this@RadarOverlayTest.frames
            maxZoom = 7
        }
        val out = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out).apply { drawColor(Color.WHITE) }
        overlay.draw(canvas, map, false)

        assertColour("borrowed", overWhite(Color.MAGENTA), out.at(map, GeoPoint(52.0, 5.5)))
    }

    @Test
    fun `zooming out borrows the finer tiles until the coarser ones arrive`() {
        // Zooming out from 7 to 6 changes every tile on screen. Until the
        // zoom-6 tiles are in, the zoom-7 ones already loaded are drawn at
        // a quarter size instead of the radar going blank.
        val store = store { key -> if (key.tile.zoom == 7) solid(Color.CYAN) else null }
        render(mapView(zoom = 7.0, centre = GeoPoint(52.0, 5.5)), store, Still())

        val map = mapView(zoom = 6.0, centre = GeoPoint(52.0, 5.5))
        val overlay = RadarOverlay(store, Still(), onVisibleTilesChanged = {}).apply {
            frames = this@RadarOverlayTest.frames
            maxZoom = 7
        }
        val out = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888)
        overlay.draw(Canvas(out).apply { drawColor(Color.WHITE) }, map, false)

        assertTrue(overlay.visibleTiles.all { it.zoom == 6 })
        assertColour("borrowed", overWhite(Color.CYAN), out.at(map, GeoPoint(52.0, 5.5)))
    }

    // --- for the eye --------------------------------------------------------------

    @Test
    fun `render a blend for review`() {
        // Two rain cells drifting east by a few kilometres per frame.
        fun cells(frame: Int) = { key: TileKey ->
            png { canvas ->
                canvas.translate(-key.tile.x * 256f, -key.tile.y * 256f)
                val shift = frame * 10f
                listOf(Triple(66.3f, 41.4f, 70f), Triple(66.9f, 41.9f, 45f)).forEach { (x, y, r) ->
                    val cx = x * 256f + shift
                    val cy = y * 256f
                    listOf(r to 0xFF88FF88.toInt(), r * 0.6f to 0xFFFFFF00.toInt(), r * 0.3f to 0xFFFF8800.toInt())
                        .forEach { (radius, colour) ->
                            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
                        }
                }
            }
        }
        val store = store { key -> cells(frames.indexOf(key.frame))(key) }
        val map = mapView(zoom = 7.6, centre = geo(66.6, 41.6, 7))
        val playhead = Still()
        val dir = File("build/screenshots").apply { mkdirs() }
        listOf(0f, 0.5f, 1f).forEachIndexed { step, blend ->
            playhead.show(from = 0, to = 1, blend = blend)
            val pixels = render(map, store, playhead)
            File(dir, "radar-blend-$step.png").outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
