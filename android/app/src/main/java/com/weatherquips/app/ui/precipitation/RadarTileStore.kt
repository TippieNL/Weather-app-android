package com.weatherquips.app.ui.precipitation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarTile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** One tile of one radar frame. */
data class TileKey(val frame: RadarFrame, val tile: RadarTile)

/**
 * Radar tiles for every frame, kept apart so switching frames never throws
 * anything away.
 *
 * The map used to load each frame by swapping the tile source on a single
 * osmdroid provider, and osmdroid empties its tile cache on every swap: each
 * step of the animation started from nothing, so the rain vanished and came
 * back. Here every frame's tiles stay put.
 *
 * Tiles are held twice over: compressed, for all frames (a radar PNG is a
 * few kilobytes), and decoded, only for the frames on screen and the next
 * one (a decoded tile is a quarter of a megabyte). Decoding happens ahead of
 * time, off the main thread, so drawing never waits on it.
 */
class RadarTileStore(
    private val load: suspend (TileKey) -> ByteArray?,
    private val scope: CoroutineScope,
    /** Called, from any thread, when a tile arrives or finishes decoding. */
    private val onChanged: () -> Unit,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2),
    private val clock: () -> Long = SystemClock::uptimeMillis,
    parallelDownloads: Int = PARALLEL_DOWNLOADS,
    encodedBudgetBytes: Int = ENCODED_BUDGET_BYTES,
    decodedBudgetBytes: Int = DECODED_BUDGET_BYTES,
) {

    private val encoded = object : LruCache<TileKey, ByteArray>(encodedBudgetBytes) {
        override fun sizeOf(key: TileKey, value: ByteArray) = value.size
    }

    private val decoded = object : LruCache<TileKey, Bitmap>(decodedBudgetBytes) {
        override fun sizeOf(key: TileKey, value: Bitmap) = value.allocationByteCount
    }

    /** Tiles the source answered with nothing: settled, and nothing to draw. */
    private val empty: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()

    /** When each failed tile last failed, so it is retried, but not in a loop. */
    private val failedAt = ConcurrentHashMap<TileKey, Long>()

    private val inFlight: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()
    private val decoding: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()

    private val queue = ArrayDeque<TileKey>()

    /**
     * Wakes parked workers, one per signal. A busy worker never waits on it:
     * it goes straight back to the queue when its download finishes.
     */
    private val wake = Channel<Unit>(Channel.UNLIMITED)

    private val workers = parallelDownloads.coerceAtLeast(1)

    init {
        repeat(workers) {
            scope.launch {
                while (isActive) {
                    val next = synchronized(queue) { queue.removeFirstOrNull() }
                    if (next == null) wake.receive() else fetch(next)
                }
            }
        }
    }

    /**
     * Asks for [keys], most urgent first. Replaces whatever was still waiting
     * from an earlier request, so panning away from an area stops its tiles
     * from holding up the new one. Downloads already under way finish.
     */
    fun request(keys: List<TileKey>) {
        val now = clock()
        val wanted = keys.filter { needsFetch(it, now) }.distinct()
        synchronized(queue) {
            queue.clear()
            queue.addAll(wanted)
        }
        repeat(wanted.size.coerceAtMost(workers)) { wake.trySend(Unit) }
    }

    /**
     * Whether the source has answered for this tile — with a picture, with
     * nothing, or with a failure recent enough not to wait on again.
     */
    fun isSettled(key: TileKey): Boolean =
        encoded.get(key) != null || key in empty || failedAt.containsKey(key)

    /**
     * Indices of the frames whose every tile in [tiles] is settled: the ones
     * that can be shown without a hole.
     */
    fun settledFrames(frames: List<RadarFrame>, tiles: List<RadarTile>): Set<Int> =
        frames.indices.filterTo(HashSet()) { index ->
            tiles.all { isSettled(TileKey(frames[index], it)) }
        }

    /**
     * The decoded tile, if it is ready. If it is not, decoding is started and
     * null comes back now; [onChanged] fires when it is done.
     */
    fun bitmap(key: TileKey): Bitmap? {
        decoded.get(key)?.let { return it }
        prepare(key)
        return null
    }

    /**
     * Makes room for the frames being blended plus the one decoded ahead, at
     * [tilesPerFrame] tiles each. A big screen at a low zoom shows more tiles
     * than the default budget allows for, and a budget that cannot hold the
     * frames on screen would decode them over again on every draw.
     */
    fun ensureCapacity(tilesPerFrame: Int) {
        val needed = tilesPerFrame * FRAMES_DECODED * TILE_BYTES
        if (needed > decoded.maxSize()) decoded.resize(needed)
    }

    /** The decoded tile if it is ready, without asking for anything. */
    fun peek(key: TileKey): Bitmap? = decoded.get(key)

    /** Decodes a tile ahead of time so it is ready when it is drawn. */
    fun prepare(key: TileKey) {
        if (decoded.get(key) != null) return
        val bytes = encoded.get(key) ?: return
        if (!decoding.add(key)) return
        scope.launch(decodeDispatcher) {
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                    decoded.put(key, bitmap)
                    onChanged()
                }
            } finally {
                decoding.remove(key)
            }
        }
    }

    private fun needsFetch(key: TileKey, now: Long): Boolean {
        if (encoded.get(key) != null || key in empty || key in inFlight) return false
        val failed = failedAt[key] ?: return true
        return now - failed >= RETRY_AFTER_MILLIS
    }

    private suspend fun fetch(key: TileKey) {
        if (!inFlight.add(key)) return
        try {
            val bytes = load(key)
            if (bytes == null || bytes.isEmpty()) empty += key else encoded.put(key, bytes)
            failedAt.remove(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failedAt[key] = clock()
        } finally {
            inFlight.remove(key)
            onChanged()
        }
    }

    companion object {
        /** Enough to fill a screen quickly without hammering a free API. */
        const val PARALLEL_DOWNLOADS = 6

        /** A failed tile is not asked for again sooner than this. */
        const val RETRY_AFTER_MILLIS = 10_000L

        /** Compressed tiles: two hours of frames over several screens of map. */
        const val ENCODED_BUDGET_BYTES = 16 * 1024 * 1024

        /** Decoded tiles: the frames on screen and the next, on a large screen. */
        const val DECODED_BUDGET_BYTES = 40 * 1024 * 1024

        /** Outgoing, incoming and the one decoded ahead. */
        private const val FRAMES_DECODED = 3

        /** One decoded 256px tile. */
        private const val TILE_BYTES = 256 * 256 * 4
    }
}
