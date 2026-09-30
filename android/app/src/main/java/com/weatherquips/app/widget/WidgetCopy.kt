package com.weatherquips.app.widget

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.weatherquips.app.R
import com.weatherquips.app.notifications.PrecipitationKind
import java.util.Locale

/**
 * The two lines at the top of the widget: the fact, then the joke.
 *
 * [understudies] are the jokes to fall back on, in order, should [quip] not
 * fit beside what the widget has to show next to it.
 */
data class WidgetCopy(
    val headline: String,
    val quip: String,
    val understudies: List<String> = emptyList(),
)

/**
 * Widget wording, in whatever language [resources] are in.
 *
 * Inverted from the notification on purpose. A notification is read once, so
 * the joke leads and the figures follow; a widget is glanced at twenty times a
 * day, so the fact leads and the joke sits under it.
 *
 * The joke comes from [WidgetQuips], which rotates it by the hour: a widget
 * that tells a different joke every refresh is noise, but one that never
 * changes gets stale by lunchtime.
 */
class WidgetCopyWriter(private val resources: Resources) {

    private val locale: Locale =
        ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()

    fun write(outlook: PrecipitationOutlook, nowMillis: Long): WidgetCopy {
        val lineup = WidgetQuips.lineup(
            resources = resources,
            mood = WidgetMood.of(outlook.outlook, outlook.condition),
            isDay = outlook.isDay,
            nowMillis = nowMillis,
        )
        return WidgetCopy(
            headline = headline(outlook.outlook),
            quip = lineup.firstOrNull().orEmpty(),
            understudies = lineup.drop(1),
        )
    }

    fun headline(outlook: Outlook): String = when (outlook) {
        is Outlook.FallingNow -> resources.getString(
            when (outlook.kind) {
                PrecipitationKind.RAIN -> R.string.widget_raining_now
                PrecipitationKind.SNOW -> R.string.widget_snowing_now
                PrecipitationKind.STORM -> R.string.widget_storming_now
            },
        )

        is Outlook.StartsIn -> if (outlook.minutesAway <= MINUTES_WORTH_COUNTING) {
            resources.getString(inMinutes(outlook.kind), roundMinutes(outlook.minutesAway))
        } else {
            resources.getString(byTime(outlook.kind), outlook.time)
        }

        is Outlook.StartsAt -> resources.getString(byTime(outlook.kind), outlook.time)

        Outlook.Dry -> resources.getString(R.string.widget_dry)
    }

    /** Copy for a widget that has never managed to load anything. */
    fun empty() = WidgetCopy(
        headline = resources.getString(R.string.widget_empty_headline),
        quip = resources.getString(R.string.widget_empty_quip),
    )

    /** What goes where the graph would be, when there is none to draw. */
    fun noGraph(hasForecast: Boolean): String = resources.getString(
        if (hasForecast) R.string.widget_empty_graph else R.string.widget_empty_first_run,
    )

    /**
     * The line beside the quip: where, and either how hard it is raining or
     * how old the answer is. The most informative version first.
     */
    fun meta(outlook: PrecipitationOutlook, wide: Boolean): String? = metaChoices(outlook, wide).first()

    /**
     * Versions of the meta line, most to least informative, for the layout to
     * pick the first one that leaves the quip room. `null` means no meta.
     *
     * Age wins over rate, because a rate from three hours ago is not a rate.
     * It is also the only way a user can tell a quiet afternoon from a widget
     * that has quietly stopped refreshing — so it is never dropped. The place
     * goes first: the user nearly always knows where they are.
     */
    fun metaChoices(outlook: PrecipitationOutlook, wide: Boolean): List<String?> {
        val place = outlook.location.lowercase(locale).let {
            // A long place name would squeeze the quip beside it to nothing.
            if (it.length > MAX_PLACE_CHARS) it.take(MAX_PLACE_CHARS - 1).trimEnd() + "…" else it
        }
        val stale = outlook.ageMinutes >= PrecipitationWidget.STALE_MINUTES
        val wet = outlook.nowMillimetresPerHour >= IntensityScale.WET_MM_PER_HOUR

        return when {
            stale -> {
                val age = age(outlook.ageMinutes)
                if (wide) listOf(resources.getString(R.string.widget_meta, place, age), age) else listOf(age)
            }
            // Narrow: the graph already says it all; the quip gets the width.
            !wide -> listOf(null)
            wet -> {
                val rate = rate(outlook.nowMillimetresPerHour)
                listOf(resources.getString(R.string.widget_meta, place, rate), rate, null)
            }
            else -> listOf(place, null)
        }
    }

    /** A rate as the widget prints it, unit included. */
    fun rate(millimetresPerHour: Double): String =
        resources.getString(R.string.rate_mm_per_hour, IntensityScale.formatNumber(millimetresPerHour, locale))

    /** The words drawn into the graph bitmap. */
    fun graphLabels() = GraphLabels(
        now = resources.getString(R.string.now),
        light = resources.getString(R.string.intensity_light),
        moderate = resources.getString(R.string.intensity_moderate),
        heavy = resources.getString(R.string.intensity_heavy),
    )

    private fun age(minutes: Int): String = if (minutes < 60) {
        resources.getString(R.string.widget_age_minutes, minutes)
    } else {
        resources.getString(R.string.widget_age_hours, minutes / 60)
    }

    private fun inMinutes(kind: PrecipitationKind) = when (kind) {
        PrecipitationKind.RAIN -> R.string.widget_rain_in
        PrecipitationKind.SNOW -> R.string.widget_snow_in
        PrecipitationKind.STORM -> R.string.widget_storms_in
    }

    private fun byTime(kind: PrecipitationKind) = when (kind) {
        PrecipitationKind.RAIN -> R.string.widget_rain_by
        PrecipitationKind.SNOW -> R.string.widget_snow_by
        PrecipitationKind.STORM -> R.string.widget_storms_by
    }

    /** Rounded to five minutes, but never down to "in 0 min". */
    private fun roundMinutes(minutes: Int): Int =
        (Math.round(minutes / ROUNDING_MINUTES.toDouble()).toInt() * ROUNDING_MINUTES)
            .coerceAtLeast(ROUNDING_MINUTES)

    private companion object {
        /** Minutes are useful inside the hour; past that, say the clock time. */
        const val MINUTES_WORTH_COUNTING = 90

        /** Nobody acts on "in 23 minutes". */
        const val ROUNDING_MINUTES = 5

        /** Enough for nearly every town; the rest get an ellipsis. */
        const val MAX_PLACE_CHARS = 16
    }
}
