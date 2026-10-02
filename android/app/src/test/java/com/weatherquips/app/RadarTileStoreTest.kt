package com.weatherquips.app

import android.graphics.Bitmap
import android.graphics.Color
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarTile
import com.weatherquips.app.ui.precipitation.RadarTileStore
import com.weatherquips.app.ui.precipitation.TileKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Fetching, keeping and decoding radar tiles. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RadarTileStoreTest {

    private val frameA = RadarFrame("/v2/radar/a", 1_000)
    private val frameB = RadarFrame("/v2/radar/b", 1_600)

    private fun key(frame: RadarFrame, x: Int) = TileKey(frame, RadarTile(7, x, 41))

    private val png: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun TestScope.store(
        parallel: Int = RadarTileStore.PARALLEL_DOWNLOADS,
        load: suspend (TileKey) -> ByteArray?,
    ) = RadarTileStore(
        load = load,
        scope = backgroundScope,
        onChanged = {},
        decodeDispatcher = StandardTestDispatcher(testScheduler),
        clock = { testScheduler.currentTime },
        parallelDownloads = parallel,
    )

    @Test
    fun `each tile is downloaded once, however often it is asked for`() = runTest {
        val calls = mutableListOf<TileKey>()
        val store = store { calls += it; png }
        val keys = (0 until 4).map { key(frameA, it) }

        store.request(keys)
        store.request(keys)
        runCurrent()
        store.request(keys)
        runCurrent()

        assertEquals(keys.toSet(), calls.toSet())
        assertEquals(4, calls.size)
    }

    @Test
    fun `downloads run side by side, up to the limit`() = runTest {
        var active = 0
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val store = store(parallel = 6) {
            active++
            peak = maxOf(peak, active)
            gate.await()
            active--
            png
        }
        // As in the app: the workers are idle and parked long before any
        // tiles are asked for, so each one has to be woken.
        runCurrent()
        val keys = (0 until 20).map { key(frameA, it) }
        store.request(keys)
        runCurrent()
        assertEquals(6, peak)

        gate.complete(Unit)
        runCurrent()
        assertTrue(keys.all { store.isSettled(it) })
    }

    @Test
    fun `a new request drops what was still waiting from the old one`() = runTest {
        // Panning away must not leave the new area queued behind the old one.
        val calls = mutableListOf<TileKey>()
        val gate = CompletableDeferred<Unit>()
        val store = store(parallel = 1) { calls += it; gate.await(); png }

        store.request(listOf(key(frameA, 0), key(frameA, 1), key(frameA, 2)))
        runCurrent()
        store.request(listOf(key(frameB, 9)))
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(key(frameA, 0), key(frameB, 9)), calls)
    }

    @Test
    fun `a failed tile does not hold playback up, and is tried again later`() = runTest {
        var offline = true
        var calls = 0
        val store = store {
            calls++
            if (offline) throw IOException("offline") else png
        }
        val tile = key(frameA, 0)

        store.request(listOf(tile))
        runCurrent()
        assertTrue("a failure must count as an answer", store.isSettled(tile))
        assertEquals(setOf(0), store.settledFrames(listOf(frameA), listOf(tile.tile)))

        // Not straight away: a dead connection is not hammered.
        offline = false
        store.request(listOf(tile))
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(RadarTileStore.RETRY_AFTER_MILLIS)
        store.request(listOf(tile))
        runCurrent()
        assertEquals(2, calls)
        assertNull(store.bitmap(tile))
        runCurrent()
        assertNotNull(store.bitmap(tile))
    }

    @Test
    fun `a tile with nothing in it is settled, never drawn and never fetched again`() = runTest {
        var calls = 0
        val store = store { calls++; null }
        val tile = key(frameA, 0)

        store.request(listOf(tile))
        runCurrent()
        store.request(listOf(tile))
        runCurrent()

        assertTrue(store.isSettled(tile))
        assertNull(store.bitmap(tile))
        assertEquals(1, calls)
    }

    @Test
    fun `a frame is ready only when every tile on screen has been answered`() = runTest {
        val store = store { if (it.frame == frameB && it.tile.x == 1) CompletableDeferred<ByteArray>().await() else png }
        val tiles = listOf(RadarTile(7, 0, 41), RadarTile(7, 1, 41))

        store.request(listOf(frameA, frameB).flatMap { frame -> tiles.map { TileKey(frame, it) } })
        runCurrent()

        assertEquals(setOf(0), store.settledFrames(listOf(frameA, frameB), tiles))
    }

    @Test
    fun `decoding happens ahead and off to the side, and peeking does not start it`() = runTest {
        val store = store { png }
        val tile = key(frameA, 0)
        store.request(listOf(tile))
        runCurrent()

        assertNull(store.peek(tile))
        runCurrent()
        assertNull("peek must not decode", store.peek(tile))

        store.prepare(tile)
        runCurrent()
        val bitmap = store.peek(tile)
        assertNotNull(bitmap)
        assertEquals(Color.RED, bitmap!!.getPixel(10, 10))
        assertFalse(bitmap.isRecycled)
    }
}
