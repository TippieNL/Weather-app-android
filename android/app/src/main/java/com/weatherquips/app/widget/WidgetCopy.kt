package com.weatherquips.app.widget

import com.weatherquips.app.notifications.PrecipitationKind

/** The two lines at the top of the widget: the fact, then the remark. */
data class WidgetCopy(val headline: String, val aside: String)

/**
 * Widget wording.
 *
 * Inverted from the notification on purpose. A notification is read once, so
 * the joke leads and the figures follow; a widget is glanced at twenty times a
 * day, so the fact leads and the remark is the small print.
 *
 * The remark varies with the hour rather than at random: a widget that tells a
 * different joke every time it refreshes is noise, but one that never changes
 * gets stale by lunchtime.
 */
object WidgetCopyWriter {

    /** Minutes are useful inside the hour; past that, say the clock time. */
    private const val MINUTES_WORTH_COUNTING = 90

    /** Nobody acts on "in 23 minutes". */
    private const val ROUNDING_MINUTES = 5

    private val fallingNowAsides = mapOf(
        PrecipitationKind.RAIN to listOf(
            "Of course it is.",
            "Right on cue.",
            "Hope you're already inside.",
        ),
        PrecipitationKind.SNOW to listOf(
            "Naturally.",
            "Walk carefully out there.",
            "It is settling, too.",
        ),
        PrecipitationKind.STORM to listOf(
            "Stay in.",
            "Nature is busy.",
            "Bad time for a walk.",
        ),
    )

    /** For the nowcast, where the arrival is close enough to plan around. */
    private val imminentAsides = listOf(
        "Walk fast.",
        "Clock is ticking.",
        "You have been warned.",
        "Time to find a roof.",
    )

    private val startsAtAsides = listOf(
        "Enjoy the dry bit.",
        "Plan accordingly.",
        "Later, but not much later.",
        "Consider yourself told.",
    )

    private val dryAsides = listOf(
        "Nothing falling. Yet.",
        "No excuses today.",
        "Make the most of it.",
        "Suspiciously pleasant.",
    )

    /**
     * @param hourOfDay used only to rotate the remark, so it changes a few
     *                  times a day but never mid-refresh.
     */
    fun write(outlook: Outlook, hourOfDay: Int): WidgetCopy = when (outlook) {
        is Outlook.FallingNow -> WidgetCopy(
            headline = when (outlook.kind) {
                PrecipitationKind.RAIN -> "Raining now"
                PrecipitationKind.SNOW -> "Snowing now"
                PrecipitationKind.STORM -> "Storming now"
            },
            aside = fallingNowAsides.getValue(outlook.kind).rotate(hourOfDay),
        )

        is Outlook.StartsIn -> WidgetCopy(
            headline = if (outlook.minutesAway <= MINUTES_WORTH_COUNTING) {
                "${outlook.kind.noun} in ${roundMinutes(outlook.minutesAway)} min"
            } else {
                "${outlook.kind.noun} by ${outlook.time}"
            },
            aside = if (outlook.minutesAway <= MINUTES_WORTH_COUNTING) {
                imminentAsides.rotate(hourOfDay)
            } else {
                startsAtAsides.rotate(hourOfDay)
            },
        )

        is Outlook.StartsAt -> WidgetCopy(
            headline = "${outlook.kind.noun} by ${outlook.time}",
            aside = startsAtAsides.rotate(hourOfDay),
        )

        Outlook.Dry -> WidgetCopy(
            headline = "Dry for now",
            aside = dryAsides.rotate(hourOfDay),
        )
    }

    /** Copy for a widget that has never managed to load anything. */
    fun empty() = WidgetCopy(
        headline = "No weather yet",
        aside = "Open the app once and I'll catch up.",
    )

    private val PrecipitationKind.noun: String
        get() = when (this) {
            PrecipitationKind.RAIN -> "Rain"
            PrecipitationKind.SNOW -> "Snow"
            PrecipitationKind.STORM -> "Storms"
        }

    /** Rounded to five minutes, but never down to "in 0 min". */
    private fun roundMinutes(minutes: Int): Int =
        (Math.round(minutes / ROUNDING_MINUTES.toDouble()).toInt() * ROUNDING_MINUTES)
            .coerceAtLeast(ROUNDING_MINUTES)

    private fun List<String>.rotate(hourOfDay: Int): String =
        this[((hourOfDay % size) + size) % size]
}
