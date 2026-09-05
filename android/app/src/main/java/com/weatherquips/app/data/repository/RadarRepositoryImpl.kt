package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.RainViewerApi
import com.weatherquips.app.domain.repository.RadarFrame
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.RadarTimeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RainViewer radar metadata. `past` frames are observed radar, `nowcast` frames
 * are the short-term forecast — the timeline shows both, past first.
 */
class RadarRepositoryImpl(
    private val api: RainViewerApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RadarRepository {

    override suspend fun loadTimeline(): RadarTimeline = withContext(dispatcher) {
        val radar = api.weatherMaps().radar
        val past = radar?.past.orEmpty().map { RadarFrame(it.path, it.time) }
        val nowcast = radar?.nowcast.orEmpty().map { RadarFrame(it.path, it.time) }
        RadarTimeline(frames = past + nowcast, pastCount = past.size)
    }

    /**
     * 256px tiles, colour scheme 2, smoothed with snow shown — the exact tile
     * flavour the web app's Leaflet layer used.
     */
    override fun tileUrlTemplate(frame: RadarFrame): String =
        "https://tilecache.rainviewer.com${frame.path}/256/{z}/{x}/{y}/2/1_1.png"
}
