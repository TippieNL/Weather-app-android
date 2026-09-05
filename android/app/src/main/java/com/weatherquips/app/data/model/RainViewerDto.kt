package com.weatherquips.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RainViewerMaps(val radar: RainViewerRadar? = null)

@Serializable
data class RainViewerRadar(
    val past: List<RainViewerFrame> = emptyList(),
    val nowcast: List<RainViewerFrame> = emptyList(),
)

@Serializable
data class RainViewerFrame(val path: String, val time: Long)
