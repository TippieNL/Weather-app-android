package com.weatherquips.app.ui.home

import com.weatherquips.app.domain.model.DailyForecast
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.widget.IntensityBand
import com.weatherquips.app.widget.IntensityScale

/**
 * What the forecast says about one day's rain, decided before any UI sees it.
 *
 * Everything here comes from the provider; nothing is invented to fill a gap.
 * A provider that gives no total leaves [totalMm] null rather than zero, and
 * the screen says it does not know instead of promising a dry day.
 */
data class DayRain(
    val date: String,
    val outlook: Outlook,
    /** Expected total, snow as water. Null when the provider gives none. */
    val totalMm: Double?,
    /** The day's highest chance, 0–100. */
    val chance: Int?,
    /** Hours with rain or snow: the provider's figure, or counted from [bars]. */
    val wetHours: Double?,
    /** True when [wetHours] was counted here rather than given. */
    val wetHoursEstimated: Boolean,
    val snowfallCm: Double?,
    /** When it is expected to fall, as clock labels. Null on a dry day. */
    val window: Window?,
    /** The heaviest slot, when anything measurable falls at all. */
    val peak: Peak?,
    /** The day slot by slot, for the chart. Empty when there is no hourly data. */
    val bars: List<Bar>,
    /** Hours each bar spans. */
    val barHours: Int,
) {
    enum class Outlook {
        /** The provider said nothing about precipitation for this day. */
        UNKNOWN,

        /** Nothing measurable, and not much of a chance of it either. */
        DRY,

        /** A real chance, but only a trace to show for it. */
        TRACE,

        /** A measurable amount. */
        WET,
    }

    data class Window(
        val from: String,
        /** When the last wet slot ends: "20:00" for a wet 19:00 hour. */
        val until: String,
        /** Wet on and off across most of the day, where a range means little. */
        val allDay: Boolean,
    )

    data class Peak(val time: String, val millimetresPerHour: Double, val band: IntensityBand)

    data class Bar(val time: String, val millimetresPerHour: Double, val chance: Int)
}

object DayRains {

    /**
     * Below this a day total is noise: models happily sum a dozen 0.01 mm
     * hours into something that is not rain anyone would notice.
     */
    const val MEASURABLE_TOTAL_MM = 0.2

    /** A chance under this, with nothing measurable, is a dry day. */
    const val DRY_CHANCE = 20

    /** Chance from which an hour counts as "expected" when no amount is given. */
    const val LIKELY_CHANCE = 40

    /** Wet slots spanning this many hours read as "on and off all day". */
    const val ALL_DAY_HOURS = 18

    fun of(day: DailyForecast): DayRain {
        val step = day.hourStep.coerceAtLeast(1)
        val bars = day.hours.map { it.toBar() }

        // Hours with a measurable amount; failing that — a model that gives
        // probabilities but rounds amounts to zero — hours where it is likely.
        val wetByAmount = day.hours.filter { it.precipitationMm >= IntensityScale.WET_MM_PER_HOUR }
        val wet = wetByAmount.ifEmpty {
            day.hours.filter { it.precipitationChance >= LIKELY_CHANCE }
        }

        val total = day.precipitationMm
            ?: day.hours.takeIf { it.isNotEmpty() }?.sumOf { it.precipitationMm * step }
        val chance = day.precipitationChance
            ?: day.hours.maxOfOrNull { it.precipitationChance }

        val outlook = when {
            total == null && chance == null -> DayRain.Outlook.UNKNOWN
            (total ?: 0.0) >= MEASURABLE_TOTAL_MM -> DayRain.Outlook.WET
            (chance ?: 0) >= DRY_CHANCE || wetByAmount.isNotEmpty() -> DayRain.Outlook.TRACE
            else -> DayRain.Outlook.DRY
        }
        val wetOnly = outlook == DayRain.Outlook.WET || outlook == DayRain.Outlook.TRACE

        val peak = day.hours
            .maxByOrNull { it.precipitationMm }
            ?.takeIf { wetOnly }
            ?.let { hour ->
                IntensityScale.bandOf(hour.precipitationMm)?.let { band ->
                    DayRain.Peak(hour.time, hour.precipitationMm, band)
                }
            }

        val counted = wetByAmount.size.toDouble() * step
        val wetHours = day.precipitationHours ?: counted.takeIf { day.hours.isNotEmpty() }

        return DayRain(
            date = day.date,
            outlook = outlook,
            totalMm = total,
            chance = chance,
            wetHours = wetHours?.takeIf { wetOnly && it > 0.0 },
            wetHoursEstimated = day.precipitationHours == null,
            snowfallCm = day.snowfallCm?.takeIf { it > 0.0 },
            window = if (wetOnly) windowOf(wet, step) else null,
            peak = peak,
            bars = bars,
            barHours = step,
        )
    }

    private fun windowOf(wet: List<HourlyForecast>, step: Int): DayRain.Window? {
        if (wet.isEmpty()) return null
        val first = hourOf(wet.first().time) ?: return null
        val last = hourOf(wet.last().time) ?: return null
        val end = last + step
        return DayRain.Window(
            from = wet.first().time,
            until = "%02d:00".format(end % 24),
            allDay = end - first >= ALL_DAY_HOURS,
        )
    }

    private fun hourOf(time: String): Int? = time.substringBefore(':').toIntOrNull()

    private fun HourlyForecast.toBar() = DayRain.Bar(
        time = time,
        millimetresPerHour = precipitationMm.coerceAtLeast(0.0),
        chance = precipitationChance.coerceIn(0, 100),
    )
}
