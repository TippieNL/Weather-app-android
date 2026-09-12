package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.BrightskyApi
import com.weatherquips.app.data.model.BrightskyRadarResponse
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.repository.RadarNowcastRepository
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * Turns Bright Sky's radar grids into the app's nowcast series.
 *
 * Separated from the transport so the sampling and the arithmetic can be
 * tested against a fixed response, which is the only way to be sure the
 * millimetres on the graph mean what they say.
 */
internal object DwdRadar {

    /** How far ahead to ask for. DWD's RV nowcast runs two hours out. */
    const val FORECAST_MINUTES = 120L

    /**
     * How much observed radar to keep. The widget draws from cache, so a
     * little history in hand lets the graph stay honest between refreshes.
     */
    const val HISTORY_MINUTES = 60L

    /** Fewer than this and there is no curve worth drawing. */
    const val MIN_POINTS = 6

    /** Bright Sky reports hundredths of a millimetre per five-minute frame. */
    private const val MM_PER_UNIT = 0.01
    private const val FRAMES_PER_HOUR = 12

    private val lastDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun lastDate(now: Instant): String =
        lastDateFormat.format(now.plusSeconds(FORECAST_MINUTES * 60))

    /**
     * @param utcOffsetSeconds the clock at the place being forecast, so a city
     *                         in another timezone is labelled in its own time.
     */
    fun points(
        response: BrightskyRadarResponse,
        now: Instant,
        utcOffsetSeconds: Int,
    ): List<NowcastPoint> {
        val position = response.position ?: return emptyList()
        val zone = runCatching { ZoneOffset.ofTotalSeconds(utcOffsetSeconds) }
            .getOrDefault(ZoneOffset.UTC)

        return response.radar.mapNotNull { frame ->
            val instant = parseInstant(frame.timestamp) ?: return@mapNotNull null
            val offset = Duration.between(now, instant).toMinutes().toInt()
            if (offset < -HISTORY_MINUTES) return@mapNotNull null

            val rate = sample(frame.precipitation5, position.x, position.y)
                ?: return@mapNotNull null

            NowcastPoint(
                time = DateTimeFormatter.ofPattern("HH:mm").format(instant.atOffset(zone)),
                minutesFromNow = offset,
                millimetresPerHour = rate,
            )
        }
    }

    /**
     * The wettest cell touching the point.
     *
     * Not the single cell it lands in: radar cells are a kilometre across and
     * a shower's position is uncertain by about that much, so a narrow core
     * one cell over is rain that is about to be overhead rather than rain
     * happening to somebody else. Under-reporting is the failure this widget
     * exists to avoid.
     */
    private fun sample(grid: List<List<Int>>, x: Double, y: Double): Double? {
        if (grid.isEmpty() || grid.first().isEmpty()) return null
        val centreY = y.roundToInt().coerceIn(0, grid.lastIndex)
        val centreX = x.roundToInt().coerceIn(0, grid.first().lastIndex)

        var peak = 0
        for (row in (centreY - 1)..(centreY + 1)) {
            val cells = grid.getOrNull(row) ?: continue
            for (column in (centreX - 1)..(centreX + 1)) {
                val value = cells.getOrNull(column) ?: continue
                if (value > peak) peak = value
            }
        }
        return peak.coerceAtLeast(0) * MM_PER_UNIT * FRAMES_PER_HOUR
    }

    private fun parseInstant(timestamp: String): Instant? = try {
        OffsetDateTime.parse(timestamp).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Radar nowcast from the German weather service, via Bright Sky.
 *
 * Chosen over a forecast model because the app's own map shows radar: when
 * the two disagree, the radar is the one the user is looking at. Coverage is
 * the DWD composite — Germany, the Low Countries, Denmark, Austria,
 * Switzerland — and outside it the endpoint refuses, which reads here as "no
 * radar for this place" rather than as a failure.
 */
class DwdRadarNowcastRepository(
    private val api: BrightskyApi,
    private val clock: () -> Instant = Instant::now,
) : RadarNowcastRepository {

    override suspend fun nowcast(
        coordinates: Coordinates,
        utcOffsetSeconds: Int,
    ): List<NowcastPoint>? {
        val now = clock()
        val response = runCatching {
            api.radar(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                lastDate = DwdRadar.lastDate(now),
            )
        }.getOrNull() ?: return null

        return DwdRadar.points(response, now, utcOffsetSeconds)
            .takeIf { it.size >= DwdRadar.MIN_POINTS }
    }
}
