package com.weatherquips.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.ui.precipitation.PrecipitationMapScreen
import com.weatherquips.app.ui.precipitation.RadarUiState
import com.weatherquips.app.ui.precipitation.TAG_BACK
import com.weatherquips.app.ui.precipitation.TAG_CURRENT_TIME
import com.weatherquips.app.ui.precipitation.TAG_PLAY_PAUSE
import com.weatherquips.app.ui.precipitation.TAG_RADAR_STATUS
import com.weatherquips.app.ui.precipitation.TAG_RECENTER
import com.weatherquips.app.ui.precipitation.TAG_MY_LOCATION
import com.weatherquips.app.ui.precipitation.TAG_RECENTER
import com.weatherquips.app.ui.precipitation.TAG_TIMELINE
import com.weatherquips.app.ui.precipitation.RadarOverlay
import com.weatherquips.app.domain.repository.RadarTile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The radar screen builds a real osmdroid MapView, so this also proves the map
 * can be created, laid out and torn down on the host without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PrecipitationScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val frames = listOf(
        RadarFrame("/v2/radar/1757000000", 1_757_000_000),
        RadarFrame("/v2/radar/1757000600", 1_757_000_600),
        RadarFrame("/v2/radar/nowcast_1", 1_757_001_200),
    )

    /** Every tile the screen asked for, in order. */
    private val requested = java.util.concurrent.CopyOnWriteArrayList<Pair<RadarFrame, RadarTile>>()

    /** An opaque blue tile: unmistakable over the grey loading grid. */
    private val blueTile: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private val blueTiles: suspend (RadarFrame, RadarTile) -> ByteArray? = { frame, tile ->
        requested += frame to tile
        blueTile
    }

    /**
     * Rain cells near Assen in RainViewer's own "Universal Blue" colours, for
     * a screenshot that looks like the real thing: drizzle at the edges, blue
     * rain, and a heavy yellow-to-red core.
     */
    private val rainCells: suspend (RadarFrame, RadarTile) -> ByteArray? = { frame, tile ->
        val scale = 1 shl (tile.zoom - 7).coerceAtLeast(0)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(-tile.x * 256f, -tile.y * 256f)
        val shift = (frame.timeEpochSeconds - frames.first().timeEpochSeconds) / 60f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        listOf(Triple(66.25f, 41.55f, 1.0f), Triple(66.55f, 41.85f, 0.7f), Triple(65.95f, 41.25f, 0.5f)).forEach { (x, y, size) ->
            val cx = (x * 256f + shift) * scale
            val cy = y * 256f * scale
            listOf(
                60f to 0x82D6C88F.toInt(), 46f to 0xFF6CD1EB.toInt(), 34f to 0xFF0088BF.toInt(),
                20f to 0xFFFFD200.toInt(), 11f to 0xFFFF4400.toInt(),
            ).forEach { (radius, colour) ->
                paint.color = colour
                canvas.drawOval(cx - radius * size * 1.5f, cy - radius * size, cx + radius * size * 1.5f, cy + radius * size, paint)
            }
        }
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun render(
        state: RadarUiState,
        onTogglePlay: () -> Unit = {},
        onBack: () -> Unit = {},
        loadTile: suspend (RadarFrame, RadarTile) -> ByteArray? = { _, _ -> null },
        freezeClock: Boolean = true,
    ) {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                PrecipitationMapScreen(
                    coordinates = Coordinates(52.99, 6.56),
                    uiState = state,
                    onTogglePlay = onTogglePlay,
                    onSelectFrame = {},
                    onPauseForLifecycle = {},
                    onResumeForLifecycle = {},
                    loadTile = loadTile,
                    onBack = onBack,
                )
            }
        }
        if (freezeClock) {
            composeRule.mainClock.autoAdvance = false
            composeRule.mainClock.advanceTimeBy(500)
        }
    }

    /** The screen's pixels, drawn the way the display would draw them. */
    private fun screenPixels(): Bitmap {
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    /** Share of the map, sampled, in strong colour: the grey loading grid has none. */
    private fun Bitmap.radarCoverage(): Float {
        var coloured = 0
        var total = 0
        for (y in 0 until (height * 0.75f).toInt() step 8) {
            for (x in 0 until width step 8) {
                val pixel = getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (maxOf(r, g, b) - minOf(r, g, b) > 40) coloured++
                total++
            }
        }
        return coloured.toFloat() / total
    }

    /** Whether there is radar at a point on the map: blue clearly beating red. */
    private fun Bitmap.hasRadarAt(xFraction: Float, yFraction: Float): Boolean {
        val pixel = getPixel((width * xFraction).toInt(), (height * yFraction).toInt())
        return android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel) > 60
    }

    @Test
    fun `map, timeline and controls all render`() {
        var toggled = false
        var backPressed = false
        render(
            RadarUiState(
                frames = frames,
                pastCount = 2,
                currentIndex = 0,
                isPlaying = true,
                isLoading = false,
                userLocation = Coordinates(53.22, 6.57),
                maxTileZoom = 7,
            ),
            onTogglePlay = { toggled = true },
            onBack = { backPressed = true },
            loadTile = rainCells,
            freezeClock = false,
        )
        composeRule.waitUntil(5_000) { screenPixels().radarCoverage() > 0.02f }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(TAG_TIMELINE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CURRENT_TIME).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RECENTER).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_PLAY_PAUSE).performClick()
        assertTrue(toggled)

        composeRule.onNodeWithTag(TAG_BACK).performClick()
        assertTrue(backPressed)

        capture("precipitation-map")
    }

    @Test
    fun `the map is centred on the forecast location once it is laid out`() {
        // Regression test for a blank map: the camera used to be set while the
        // MapView still had no size, so osmdroid could not resolve a centre and
        // nothing was drawn until a touch forced it to recompute.
        render(
            RadarUiState(frames = frames, pastCount = 2, currentIndex = 0, isLoading = false),
        )

        val map = composeRule.activity.window.decorView.findMapView()
        assertNotNull("no MapView in the hierarchy", map)
        assertTrue("map was never laid out", map!!.width > 0 && map.height > 0)
        assertEquals(52.99, map.mapCenter.latitude, 0.01)
        assertEquals(6.56, map.mapCenter.longitude, 0.01)
        assertEquals(7.0, map.zoomLevelDouble, 0.01)
    }

    @Test
    fun `the radar is drawn by its own overlay, under the markers`() {
        render(RadarUiState(frames = frames, pastCount = 2, isLoading = false, maxTileZoom = 7))

        val map = composeRule.activity.window.decorView.findMapView()!!
        assertTrue("radar is not the bottom overlay", map.overlays.first() is RadarOverlay)
        // The old radar was a second osmdroid tile overlay whose cache was
        // emptied on every frame. There must not be one any more.
        assertTrue(map.overlays.none { it is TilesOverlay })
    }

    @Test
    fun `every frame is fetched for the area on screen, never past the zoom limit`() {
        render(
            RadarUiState(frames = frames, pastCount = 2, isLoading = false, maxTileZoom = 7),
            loadTile = blueTiles,
            freezeClock = false,
        )
        // The host draws nothing unless asked; a display draws every frame.
        composeRule.waitUntil(5_000) {
            screenPixels()
            requested.map { it.first }.toSet() == frames.toSet()
        }

        // Assen, at zoom 7, is tile 66/41.
        assertTrue(requested.any { it.second == RadarTile(7, 66, 41) })
        assertTrue(requested.all { it.second.zoom == 7 })

        // Zoomed far in, the radar is the same zoom-7 tiles drawn larger:
        // RainViewer has nothing deeper but a grey placeholder.
        val map = composeRule.activity.window.decorView.findMapView()!!
        composeRule.runOnUiThread { map.controller.setZoom(11.0) }
        composeRule.waitForIdle()
        screenPixels()
        composeRule.waitForIdle()
        val overlay = map.overlays.filterIsInstance<RadarOverlay>().single()
        assertTrue(overlay.visibleTiles.isNotEmpty())
        assertTrue(overlay.visibleTiles.all { it.zoom == 7 })
        assertTrue(requested.all { it.second.zoom <= 7 })
    }

    @Test
    fun `the rain stays on screen while the frames change`() {
        // Regression test for rain that vanished and came back on every step
        // of the animation: switching frames used to empty the tile cache.
        var state by androidx.compose.runtime.mutableStateOf(
            RadarUiState(frames = frames, pastCount = 2, currentIndex = 0, isLoading = false, maxTileZoom = 7),
        )
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                PrecipitationMapScreen(
                    coordinates = Coordinates(52.99, 6.56),
                    uiState = state,
                    onTogglePlay = {},
                    onSelectFrame = {},
                    onPauseForLifecycle = {},
                    onResumeForLifecycle = {},
                    loadTile = blueTiles,
                    onBack = {},
                )
            }
        }
        composeRule.waitUntil(5_000) { screenPixels().hasRadarAt(0.3f, 0.35f) }

        listOf(1, 2, 0).forEach { next ->
            composeRule.runOnUiThread { state = state.copy(currentIndex = next, isPlaying = true) }
            // Look at the map all the way through the change, not just at the end.
            repeat(6) {
                composeRule.mainClock.advanceTimeBy(100)
                assertTrue(
                    "the rain disappeared while moving to frame $next",
                    screenPixels().hasRadarAt(0.3f, 0.35f),
                )
            }
        }
    }

    @Test
    fun `the my-location control only appears once a fix is known`() {
        render(RadarUiState(frames = frames, pastCount = 2, isLoading = false))
        composeRule.onNodeWithTag(TAG_MY_LOCATION).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_RECENTER).assertExists()
    }

    @Test
    fun `a known position adds the my-location control`() {
        render(
            RadarUiState(
                frames = frames,
                pastCount = 2,
                isLoading = false,
                userLocation = Coordinates(53.2, 6.6),
            ),
        )
        composeRule.onNodeWithTag(TAG_MY_LOCATION).assertExists()
    }

    @Test
    fun `radar failure still leaves a usable screen`() {
        render(RadarUiState(isLoading = false, hasError = true))

        composeRule.onNodeWithTag(TAG_RADAR_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BACK).assertIsDisplayed()
    }

    /** Walks the hierarchy for the real MapView behind the AndroidView. */
    private fun View.findMapView(): MapView? = when {
        this is MapView -> this
        this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findMapView() }
        else -> null
    }

    private fun capture(name: String) {
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
