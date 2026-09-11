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

    private val startsAtAsides = listOf(
        "Enjoy the dry bit.",
        "Clock is ticking.",
        "You have been warned.",
        "Plan accordingly.",
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

        is Outlook.StartsAt -> WidgetCopy(
            headline = when (outlook.kind) {
                PrecipitationKind.RAIN -> "Rain by ${outlook.time}"
                PrecipitationKind.SNOW -> "Snow by ${outlook.time}"
                PrecipitationKind.STORM -> "Storms by ${outlook.time}"
            },
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

    private fun List<String>.rotate(hourOfDay: Int): String =
        this[((hourOfDay % size) + size) % size]
}
