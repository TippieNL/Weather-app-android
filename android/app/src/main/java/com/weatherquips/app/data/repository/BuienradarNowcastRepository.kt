package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.BuienradarApi
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.repository.RadarNowcastRepository
import kotlin.math.pow

/**
 * Parsing for Buienradar's `raintext` feed, kept separate from the transport
 * so the arithmetic can be tested without a network.
 *
 * The feed is one line per five minutes, `value|HH:MM`, where the value is a
 * radar reflectivity byte on a logarithmic scale rather than a millimetre
 * figure. Out of coverage the endpoint answers with a single sentence of
 * English prose, which parses to nothing and is treated as "no radar here".
 */
internal object BuienradarNowcast {

    /** Five-minute steps, two hours ahead. */
    const val STEP_MINUTES = 5

    /** Fewer than this and there is no curve worth drawing. */
    const val MIN_POINTS = 4

    /**
     * Buienradar's own conversion: `mm/h = 10^((value - 109) / 32)`.
     * Value 109 is 1 mm/h, 77 is 0.1 mm/h, 141 is 10 mm/h.
     */
    private const val UNIT_RATE_VALUE = 109.0
    private const val VALUES_PER_DECADE = 32.0

    /** Below this the log scale is just reporting its own noise floor. */
    private const val NEGLIGIBLE_MM_PER_HOUR = 0.01

    /** The service answers only for the Netherlands and Belgium. */
    private const val MIN_LATITUDE = 49.4
    private const val MAX_LATITUDE = 53.8
    private const val MIN_LONGITUDE = 2.4
    private const val MAX_LONGITUDE = 7.4

    private val TIME = Regex("""\d{2}:\d{2}""")

    /**
     * Rough bounding box for the service area. Only an optimisation — the
     * endpoint is the real authority — but it keeps the app from firing a
     * request at a Dutch server on behalf of a user in Sydney.
     */
    fun covers(coordinates: Coordinates): Boolean =
        coordinates.latitude in MIN_LATITUDE..MAX_LATITUDE &&
            coordinates.longitude in MIN_LONGITUDE..MAX_LONGITUDE

    fun parse(body: String): List<NowcastPoint> =
        body.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapIndexedNotNull { index, line ->
                val separator = line.indexOf('|')
                if (separator <= 0) return@mapIndexedNotNull null
                val value = line.take(separator).trim().toIntOrNull()
                    ?: return@mapIndexedNotNull null
                val time = line.substring(separator + 1).trim()
                if (!TIME.matches(time)) return@mapIndexedNotNull null
                NowcastPoint(
                    time = time,
                    // The first line is the current five-minute bucket, so
                    // position in the feed is the offset. No timezone maths,
                    // which matters because the clock times are Dutch local
                    // and the phone may not be.
                    minutesFromNow = index * STEP_MINUTES,
                    millimetresPerHour = rateOf(value),
                )
            }
            .toList()

    private fun rateOf(value: Int): Double {
        val rate = 10.0.pow((value.coerceIn(0, 255) - UNIT_RATE_VALUE) / VALUES_PER_DECADE)
        return if (rate < NEGLIGIBLE_MM_PER_HOUR) 0.0 else Math.round(rate * 100) / 100.0
    }
}

/** Buienradar's radar nowcast, for the corner of the world it covers. */
class BuienradarNowcastRepository(private val api: BuienradarApi) : RadarNowcastRepository {

    override suspend fun nowcast(coordinates: Coordinates): List<NowcastPoint>? {
        if (!BuienradarNowcast.covers(coordinates)) return null

        val body = runCatching {
            api.rainText(coordinates.latitude, coordinates.longitude).use { it.string() }
        }.getOrNull() ?: return null

        return BuienradarNowcast.parse(body)
            .takeIf { it.size >= BuienradarNowcast.MIN_POINTS }
    }
}
