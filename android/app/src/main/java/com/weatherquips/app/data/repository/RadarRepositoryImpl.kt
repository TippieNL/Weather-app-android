package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.RainViewerApi
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.RadarTile
import com.weatherquips.app.domain.repository.RadarTimeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RainViewer radar: the frame list, and the tiles for each frame.
 *
 * `past` frames are observed radar ten minutes apart; `nowcast` frames were
 * a short forecast, which RainViewer has stopped publishing — the list comes
 * back empty, so the timeline simply ends at the latest observation.
 */
class RadarRepositoryImpl(
    private val api: RainViewerApi,
    /** Its own client so tiles get an HTTP disk cache; RainViewer marks them cacheable for two days. */
    private val tileClient: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RadarRepository {

    /**
     * RainViewer renders radar down to zoom 7 and answers anything deeper
     * with a grey "Zoom Level Not Supported" image, which drawn over the map
     * looks like weather. So nothing deeper is ever asked for.
     */
    override val maxTileZoom: Int = MAX_ZOOM

    override suspend fun loadTimeline(): RadarTimeline = withContext(dispatcher) {
        val radar = api.weatherMaps().radar
        val past = radar?.past.orEmpty().map { RadarFrame(it.path, it.time) }
        val nowcast = radar?.nowcast.orEmpty().map { RadarFrame(it.path, it.time) }
        RadarTimeline(frames = past + nowcast, pastCount = past.size)
    }

    override suspend fun loadTile(frame: RadarFrame, tile: RadarTile): ByteArray? {
        if (tile.zoom > maxTileZoom) return null
        val request = Request.Builder().url(tileUrl(frame, tile)).build()
        return tileClient.newCall(request).await().use { response ->
            when {
                response.isSuccessful -> withContext(dispatcher) { response.body?.bytes() }
                response.code == HTTP_NOT_FOUND -> null
                else -> throw IOException("Radar tile answered ${response.code}")
            }
        }
    }

    companion object {
        const val MAX_ZOOM = 7

        /**
         * 256px tiles, colour scheme 2, smoothed with snow shown — the tile
         * flavour the web app's Leaflet layer used.
         */
        fun tileUrl(frame: RadarFrame, tile: RadarTile): String =
            "https://tilecache.rainviewer.com${frame.path}/256/${tile.zoom}/${tile.x}/${tile.y}/2/1_1.png"

        private const val HTTP_NOT_FOUND = 404
    }
}

/** Runs the call on OkHttp's own threads, and cancels it if the caller gives up. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                // A response that arrives after cancellation still holds a
                // connection, so it is closed rather than dropped.
                continuation.resume(response) { _ -> response.close() }
            }
        },
    )
}
