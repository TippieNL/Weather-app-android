package com.weatherquips.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bright Sky's view of the DWD radar composite: a small grid of cells around
 * the requested point, repeated for every five-minute frame.
 */
@Serializable
data class BrightskyRadarResponse(
    val radar: List<BrightskyRadarFrame> = emptyList(),
    /** Where the requested coordinates land in the grid, in fractional cells. */
    @SerialName("latlon_position") val position: BrightskyGridPosition? = null,
)

@Serializable
data class BrightskyRadarFrame(
    /** ISO-8601 with an offset, always UTC in practice. */
    val timestamp: String,
    /** Hundredths of a millimetre falling over the frame's five minutes. */
    @SerialName("precipitation_5") val precipitation5: List<List<Int>> = emptyList(),
)

@Serializable
data class BrightskyGridPosition(val x: Double, val y: Double)
