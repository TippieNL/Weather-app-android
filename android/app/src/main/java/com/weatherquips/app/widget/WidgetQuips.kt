package com.weatherquips.app.widget

import android.content.res.Resources
import androidx.annotation.ArrayRes
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.notifications.PrecipitationKind
import kotlin.random.Random

/**
 * What the widget's quip is about.
 *
 * Coarser than the forecast on purpose: the headline already carries the
 * facts, so the joke only needs to know whether it is wet now, wet soon, or
 * what kind of dry. Each mood has a day pool and a night pool in
 * `res/values/widget.xml`; adding lines there needs no code.
 */
enum class WidgetMood {
    RAIN_NOW,
    SNOW_NOW,
    STORM_NOW,
    RAIN_SOON,
    SNOW_SOON,
    CLEAR,
    CLOUDY,
    FOG,
    WIND,
    HOT,
    COLD;

    companion object {
        fun of(outlook: Outlook, condition: WeatherCondition): WidgetMood = when (outlook) {
            is Outlook.FallingNow -> when (outlook.kind) {
                PrecipitationKind.RAIN -> RAIN_NOW
                PrecipitationKind.SNOW -> SNOW_NOW
                PrecipitationKind.STORM -> STORM_NOW
            }
            // An approaching storm still calls for the umbrella lines.
            is Outlook.StartsIn -> soon(outlook.kind)
            is Outlook.StartsAt -> soon(outlook.kind)
            Outlook.Dry -> when (condition) {
                WeatherCondition.CLEAR -> CLEAR
                WeatherCondition.CLOUDY -> CLOUDY
                WeatherCondition.FOGGY -> FOG
                WeatherCondition.WINDY -> WIND
                WeatherCondition.HOT -> HOT
                WeatherCondition.COLD -> COLD
                // Wet conditions always read as falling now, so these only
                // arrive here if that ever changes. Overcast is the safe joke.
                WeatherCondition.RAINY,
                WeatherCondition.SNOWY,
                WeatherCondition.STORMY,
                -> CLOUDY
            }
        }

        private fun soon(kind: PrecipitationKind) =
            if (kind == PrecipitationKind.SNOW) SNOW_SOON else RAIN_SOON
    }
}

/**
 * Picks the widget's quip.
 *
 * A widget is glanced at twenty times a day, so the choice is a schedule, not
 * a dice roll: the line holds for the whole hour — refreshes in between do
 * not reshuffle it — and then moves on. Each pool is dealt out like a deck,
 * every line once before any line repeats, in a fresh order each round, and
 * never the same line two hours running, even across the seam between two
 * rounds.
 */
object WidgetQuips {

    private const val MILLIS_PER_HOUR = 3_600_000L

    @ArrayRes
    fun array(mood: WidgetMood, isDay: Boolean): Int = when (mood) {
        WidgetMood.RAIN_NOW -> if (isDay) R.array.widget_quips_rain_now_day else R.array.widget_quips_rain_now_night
        WidgetMood.SNOW_NOW -> if (isDay) R.array.widget_quips_snow_now_day else R.array.widget_quips_snow_now_night
        WidgetMood.STORM_NOW -> if (isDay) R.array.widget_quips_storm_now_day else R.array.widget_quips_storm_now_night
        WidgetMood.RAIN_SOON -> if (isDay) R.array.widget_quips_rain_soon_day else R.array.widget_quips_rain_soon_night
        WidgetMood.SNOW_SOON -> if (isDay) R.array.widget_quips_snow_soon_day else R.array.widget_quips_snow_soon_night
        WidgetMood.CLEAR -> if (isDay) R.array.widget_quips_clear_day else R.array.widget_quips_clear_night
        WidgetMood.CLOUDY -> if (isDay) R.array.widget_quips_cloudy_day else R.array.widget_quips_cloudy_night
        WidgetMood.FOG -> if (isDay) R.array.widget_quips_fog_day else R.array.widget_quips_fog_night
        WidgetMood.WIND -> if (isDay) R.array.widget_quips_wind_day else R.array.widget_quips_wind_night
        WidgetMood.HOT -> if (isDay) R.array.widget_quips_hot_day else R.array.widget_quips_hot_night
        WidgetMood.COLD -> if (isDay) R.array.widget_quips_cold_day else R.array.widget_quips_cold_night
    }

    fun pick(resources: Resources, mood: WidgetMood, isDay: Boolean, nowMillis: Long): String =
        lineup(resources, mood, isDay, nowMillis).firstOrNull().orEmpty()

    /**
     * This hour's line first, then the rest of the pool after it, as
     * understudies for when the chosen line will not fit the space there is.
     */
    fun lineup(resources: Resources, mood: WidgetMood, isDay: Boolean, nowMillis: Long): List<String> {
        val lines = resources.getStringArray(array(mood, isDay))
        if (lines.isEmpty()) return emptyList()
        val first = index(lines.size, hourOf(nowMillis), salt(mood, isDay))
        return lines.indices.map { lines[(first + it) % lines.size] }
    }

    /** Hours since the epoch: the quip's clock. */
    fun hourOf(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_HOUR)

    /**
     * Position in a pool of [size] lines for a given [hour].
     *
     * Hours are grouped into rounds of [size]; each round deals the whole
     * pool in its own shuffled order. When a round would open on the line
     * the previous one closed with, its first two lines swap. That swap
     * never touches a round's last line (with three or more lines), so the
     * previous round's closer can be computed without recursing further back.
     */
    fun index(size: Int, hour: Long, salt: Int): Int {
        if (size <= 1) return 0
        if (size == 2) return Math.floorMod(hour, 2L).toInt()

        val round = Math.floorDiv(hour, size.toLong())
        val position = Math.floorMod(hour, size.toLong()).toInt()
        val order = order(size, round, salt)
        if (position <= 1 && order[0] == order(size, round - 1, salt).last()) {
            return order[1 - position]
        }
        return order[position]
    }

    /** Separate moods and day/night shuffle independently. */
    private fun salt(mood: WidgetMood, isDay: Boolean) = mood.ordinal * 2 + if (isDay) 1 else 0

    private fun order(size: Int, round: Long, salt: Int): List<Int> =
        (0 until size).shuffled(Random(round * SALT_STRIDE + salt))

    private const val SALT_STRIDE = 64L
}
