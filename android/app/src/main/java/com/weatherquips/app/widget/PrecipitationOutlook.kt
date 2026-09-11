package com.weatherquips.app.widget

import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.notifications.PrecipitationAlerts
import com.weatherquips.app.notifications.PrecipitationKind
import kotlin.math.abs
import kotlin.math.ceil

/** One sample on the widget's graph. */
data class ChartPoint(
    val minutesFromNow: Int,
    val millimetresPerHour: Double,
)

/** A labelled position on the graph's time axis. */
data class ChartTick(
    val minutesFromNow: Int,
    val label: String,
    /** Whole hours; the first to be drawn when the axis runs out of room. */
    val isMajor: Boolean = true,
)

/** How finely the forecast behind the graph is sampled. */
enum class ChartResolution { QUARTER_HOUR, HOURLY }

/**
 * Everything needed to draw the graph, with the time axis already resolved to
 * minutes so the renderer never has to parse a clock.
 */
data class PrecipitationChart(
    val points: List<ChartPoint>,
    val ticks: List<ChartTick>,
    val resolution: ChartResolution,
) {
    val startMinutes: Int get() = points.firstOrNull()?.minutesFromNow ?: 0
    val endMinutes: Int get() = points.lastOrNull()?.minutesFromNow ?: 0
    val peakMillimetresPerHour: Double get() = points.maxOfOrNull { it.millimetresPerHour } ?: 0.0
    val isEmpty: Boolean get() = points.size < 2
}

/** What the widget leads with. */
sealed interface Outlook {
    /** It is already falling, at this rate. */
    data class FallingNow(
        val kind: PrecipitationKind,
        val millimetresPerHour: Double = 0.0,
    ) : Outlook

    /** Dry now, but the nowcast can say how soon that ends. */
    data class StartsIn(
        val kind: PrecipitationKind,
        val time: String,
        val minutesAway: Int,
    ) : Outlook

    /** No nowcast to work from, so the hour and its probability are all we have. */
    data class StartsAt(
        val kind: PrecipitationKind,
        val time: String,
        val chancePercent: Int,
    ) : Outlook

    /** Nothing worth mentioning in the window. */
    data object Dry : Outlook
}

/** Everything the widget draws, derived once so the UI stays dumb. */
data class PrecipitationOutlook(
    val outlook: Outlook,
    val chart: PrecipitationChart,
    /** Intensity at the "now" line, which the widget prints next to the graph. */
    val nowMillimetresPerHour: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val location: String,
    val updatedAtMillis: Long,
    /** Where the forecast is for, so a tap can open the radar on it. */
    val coordinates: Coordinates,
)

/**
 * Turns a cached forecast into the widget's view of the next couple of hours.
 *
 * Pure on purpose: what the widget *says* is the part worth testing, and none
 * of it needs an Android framework to decide.
 */
object PrecipitationOutlooks {

    /** Hours of hourly forecast the headline may reach into. */
    const val WINDOW_HOURS = 6

    /** Hours the graph falls back to when a provider has no nowcast. */
    const val FALLBACK_CHART_HOURS = 4

    /** Probability at which the hourly fallback calls rain likely. */
    const val LIKELY_THRESHOLD = 40

    /** Probability high enough to call it already happening. */
    private const val FALLING_NOW_THRESHOLD = 60

    /** Gaps at or under this read as sub-hourly sampling. */
    private const val QUARTER_HOUR_GAP_MINUTES = 20

    private const val MINUTES_PER_HOUR = 60.0

    fun from(cached: CachedWeather): PrecipitationOutlook {
        val data = cached.data
        val nowcast = data.nowcast.ifEmpty { data.hourlyForecast.asNowcast() }
        val chart = chartFrom(nowcast)
        val nowRate = nowRate(nowcast)

        return PrecipitationOutlook(
            outlook = outlookFor(
                condition = data.condition,
                nowcast = nowcast,
                hasRealNowcast = data.nowcast.isNotEmpty(),
                nowRate = nowRate,
                hourly = data.hourlyForecast,
            ),
            chart = chart,
            nowMillimetresPerHour = nowRate,
            condition = data.condition,
            isDay = data.isDay,
            location = data.location,
            updatedAtMillis = cached.fetchedAtEpochMillis,
            coordinates = cached.coordinates,
        )
    }

    /** Hourly totals stood in for a nowcast: a step per hour, no invented curve. */
    private fun List<HourlyForecast>.asNowcast(): List<NowcastPoint> =
        take(FALLBACK_CHART_HOURS).mapIndexed { index, hour ->
            NowcastPoint(
                time = hour.time,
                minutesFromNow = index * 60,
                millimetresPerHour = hour.precipitationMm.coerceAtLeast(0.0),
            )
        }

    private fun chartFrom(nowcast: List<NowcastPoint>): PrecipitationChart {
        val points = nowcast.map { ChartPoint(it.minutesFromNow, it.millimetresPerHour) }
        val gap = nowcast.zipWithNext { a, b -> b.minutesFromNow - a.minutesFromNow }
            .minOrNull() ?: 60
        val resolution =
            if (gap <= QUARTER_HOUR_GAP_MINUTES) ChartResolution.QUARTER_HOUR else ChartResolution.HOURLY

        return PrecipitationChart(
            points = points,
            ticks = ticksFor(nowcast, resolution),
            resolution = resolution,
        )
    }

    /**
     * Axis labels. On the quarter-hourly graph only the whole and half hours
     * are labelled — eleven timestamps in 280dp is a smear, not an axis.
     */
    private fun ticksFor(
        nowcast: List<NowcastPoint>,
        resolution: ChartResolution,
    ): List<ChartTick> = when (resolution) {
        ChartResolution.HOURLY -> nowcast.map { ChartTick(it.minutesFromNow, it.time) }
        ChartResolution.QUARTER_HOUR -> nowcast
            .filter { it.time.endsWith(":00") || it.time.endsWith(":30") }
            .map { ChartTick(it.minutesFromNow, it.time, isMajor = it.time.endsWith(":00")) }
    }

    /** The sample sitting closest to now, preferring the most recent past one. */
    private fun nowRate(nowcast: List<NowcastPoint>): Double {
        val current = nowcast.filter { it.minutesFromNow <= 0 }.maxByOrNull { it.minutesFromNow }
            ?: nowcast.minByOrNull { abs(it.minutesFromNow) }
        return current?.millimetresPerHour?.coerceAtLeast(0.0) ?: 0.0
    }

    private fun outlookFor(
        condition: WeatherCondition,
        nowcast: List<NowcastPoint>,
        hasRealNowcast: Boolean,
        nowRate: Double,
        hourly: List<HourlyForecast>,
    ): Outlook {
        val kind = PrecipitationAlerts.kindOf(condition)
        val window = hourly.take(WINDOW_HOURS)

        val wetCondition = condition == WeatherCondition.RAINY ||
            condition == WeatherCondition.SNOWY ||
            condition == WeatherCondition.STORMY
        if (nowRate >= IntensityScale.WET_MM_PER_HOUR || wetCondition) {
            return Outlook.FallingNow(kind, nowRate)
        }

        if (hasRealNowcast) {
            // A nowcast can say "in twenty minutes", which is the difference
            // between waiting for it to pass and walking into it.
            nowcast.firstOrNull {
                it.minutesFromNow > 0 && it.millimetresPerHour >= IntensityScale.WET_MM_PER_HOUR
            }?.let { return Outlook.StartsIn(kind, it.time, it.minutesFromNow) }

            // Past the end of the nowcast, probabilities are all there is.
            val coveredHours = ceil(
                (nowcast.maxOfOrNull { it.minutesFromNow } ?: 0) / MINUTES_PER_HOUR,
            ).toInt().coerceAtLeast(1)
            return window.drop(coveredHours).likelyHour()
                ?.let { Outlook.StartsAt(kind, it.time.take(5), it.chancePercent()) }
                ?: Outlook.Dry
        }

        if ((window.firstOrNull()?.chancePercent() ?: 0) >= FALLING_NOW_THRESHOLD) {
            return Outlook.FallingNow(kind, nowRate)
        }
        // The first hour is "now", which is already known to be dry here.
        return window.drop(1).likelyHour()
            ?.let { Outlook.StartsAt(kind, it.time.take(5), it.chancePercent()) }
            ?: Outlook.Dry
    }

    private fun List<HourlyForecast>.likelyHour(): HourlyForecast? =
        firstOrNull { it.chancePercent() >= LIKELY_THRESHOLD }

    private fun HourlyForecast.chancePercent() = precipitationChance.coerceIn(0, 100)
}
