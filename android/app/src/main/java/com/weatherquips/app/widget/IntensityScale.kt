package com.weatherquips.app.widget

/**
 * Named precipitation intensities, the way a rain radar labels them.
 *
 * The thresholds are the conventional meteorological ones (light below
 * 2.5 mm/h, heavy above 7.6 mm/h in the classic definition) nudged down a
 * little: a widget is read at a glance, and 2 mm/h already means "take a
 * coat", so it is more useful as the start of "moderate" than the end of it.
 */
enum class IntensityBand(val label: String, val millimetresPerHour: Double) {
    LIGHT("light", 0.5),
    MODERATE("moderate", 2.0),
    HEAVY("heavy", 6.0),
    VIOLENT("violent", 15.0),
}

/**
 * Maps a rate in mm/h onto a 0..1 height on the graph.
 *
 * Deliberately not linear. Precipitation rates are roughly logarithmic in
 * how they feel: the step from nothing to drizzle matters more than the step
 * from heavy to torrential, and on a linear axis a 0.3 mm/h drizzle is one
 * pixel tall and effectively invisible. Each band therefore gets an equal
 * quarter of the height, which also gives the graph evenly spaced gridlines
 * to hang its labels on.
 */
object IntensityScale {

    /** Below this the sensors are arguing with themselves; treat it as dry. */
    const val TRACE_MM_PER_HOUR = 0.05

    /** Wet enough to be worth a headline. */
    const val WET_MM_PER_HOUR = 0.1

    /**
     * Height given to the lightest measurable rain.
     *
     * Without it the bottom band is linear and 0.1 mm/h draws two pixels tall
     * on a widget — indistinguishable from dry, which is the complaint this
     * graph exists to answer. Anything the radar can measure gets a shape.
     */
    private const val TRACE_FRACTION = 0.10f

    /**
     * The axis, as (rate, height) anchors with straight lines between them.
     * Each named band still gets its own quarter; the extra anchor only
     * steepens the climb out of zero.
     */
    private val anchors: List<Pair<Double, Float>> = listOf(
        0.0 to 0f,
        WET_MM_PER_HOUR to TRACE_FRACTION,
        IntensityBand.LIGHT.millimetresPerHour to 0.25f,
        IntensityBand.MODERATE.millimetresPerHour to 0.5f,
        IntensityBand.HEAVY.millimetresPerHour to 0.75f,
        IntensityBand.VIOLENT.millimetresPerHour to 1f,
    )

    private val bandFractions = mapOf(
        IntensityBand.LIGHT to 0.25f,
        IntensityBand.MODERATE to 0.5f,
        IntensityBand.HEAVY to 0.75f,
        IntensityBand.VIOLENT to 1f,
    )

    /** Gridline height for [band], as a fraction of the plot. */
    fun fractionOf(band: IntensityBand): Float = bandFractions.getValue(band)

    /** The bands that get a labelled gridline. The top one is the ceiling. */
    val gridBands: List<IntensityBand> =
        listOf(IntensityBand.LIGHT, IntensityBand.MODERATE, IntensityBand.HEAVY)

    /** Height for a rate, interpolated between anchors and clamped at the top. */
    fun fraction(millimetresPerHour: Double): Float {
        if (millimetresPerHour <= 0.0) return 0f

        anchors.zipWithNext { (lowerRate, lowerFraction), (upperRate, upperFraction) ->
            if (millimetresPerHour <= upperRate) {
                val span = upperRate - lowerRate
                val progress =
                    if (span <= 0.0) 1f else ((millimetresPerHour - lowerRate) / span).toFloat()
                return lowerFraction + progress * (upperFraction - lowerFraction)
            }
        }
        return 1f
    }

    /** The band a rate falls in, or null when there is effectively nothing. */
    fun bandOf(millimetresPerHour: Double): IntensityBand? {
        if (millimetresPerHour < TRACE_MM_PER_HOUR) return null
        return IntensityBand.entries.firstOrNull { millimetresPerHour <= it.millimetresPerHour }
            ?: IntensityBand.VIOLENT
    }

    /**
     * A rate as the widget prints it: one decimal while the numbers are small
     * enough for the decimal to mean something, whole millimetres after that.
     */
    fun format(millimetresPerHour: Double): String {
        val rate = millimetresPerHour.coerceAtLeast(0.0)
        return if (rate < 10.0) {
            "${(Math.round(rate * 10) / 10.0)} mm/h"
        } else {
            "${Math.round(rate)} mm/h"
        }
    }
}
