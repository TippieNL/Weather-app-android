package com.weatherquips.app.notifications

import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData
import kotlin.random.Random

/** What is about to fall on you. Decides the wording and the icon. */
enum class PrecipitationKind { RAIN, SNOW, STORM }

/**
 * A precipitation alert.
 *
 * [headline] is the joke, [summary] is the single line the collapsed
 * notification shows, and [detail] is the expanded text. The data always lives
 * in the summary, so the alert is useful even when it is never expanded and
 * even if the joke falls flat.
 */
data class PrecipitationAlert(
    val kind: PrecipitationKind,
    val headline: String,
    val summary: String,
    val detail: String,
    val location: String? = null,
)

/**
 * Alert wording, in the same voice as the quips on the home screen.
 *
 * Kept pure, and free of the `**highlight**` markers the in-app quotes use:
 * a notification is plain text, so a marker would be shown to the user rather
 * than rendered.
 */
object PrecipitationAlerts {

    /** The web app's trigger: a high daily chance, or wet/stormy conditions now. */
    fun shouldNotify(data: WeatherData): Boolean =
        data.precipitationChance > 50 ||
            data.condition == WeatherCondition.RAINY ||
            data.condition == WeatherCondition.SNOWY ||
            data.condition == WeatherCondition.STORMY

    fun kindOf(condition: WeatherCondition): PrecipitationKind = when (condition) {
        WeatherCondition.SNOWY -> PrecipitationKind.SNOW
        WeatherCondition.STORMY -> PrecipitationKind.STORM
        else -> PrecipitationKind.RAIN
    }

    private val headlines: Map<PrecipitationKind, List<String>> = mapOf(
        PrecipitationKind.RAIN to listOf(
            "The sky is about to ruin this",
            "Rain incoming. Act surprised.",
            "Water is falling out of the sky again",
            "Hope you enjoyed being dry",
            "The clouds have made their decision",
        ),
        PrecipitationKind.SNOW to listOf(
            "Everything is about to go white",
            "Snow. Because of course.",
            "Winter is being dramatic again",
            "The pavement is plotting against you",
            "Nature is redecorating in white",
        ),
        PrecipitationKind.STORM to listOf(
            "The sky is losing its temper",
            "Thunder is warming up out there",
            "Something loud this way comes",
            "Nature has chosen violence today",
            "The clouds are spoiling for a fight",
        ),
    )

    /** The closing jab. Advice, delivered rudely. */
    private val asides: Map<PrecipitationKind, List<String>> = mapOf(
        PrecipitationKind.RAIN to listOf(
            "Take the umbrella you will leave somewhere.",
            "Or don't. Get soaked. Live a little.",
            "Bring a coat, or bring regret.",
            "Your hair had plans. The sky disagrees.",
        ),
        PrecipitationKind.SNOW to listOf(
            "Bundle up and walk like a penguin.",
            "The roads will be a mess. So will you.",
            "Boots. Not those ones. Proper ones.",
            "Everything takes twice as long today.",
        ),
        PrecipitationKind.STORM to listOf(
            "Stay inside and feel smug about it.",
            "Bad day for a leisurely stroll.",
            "Unplug something. Feel prepared.",
            "Let the sky get it out of its system.",
        ),
    )

    fun headlinesFor(kind: PrecipitationKind): List<String> = headlines.getValue(kind)

    fun asidesFor(kind: PrecipitationKind): List<String> = asides.getValue(kind)

    fun build(
        data: WeatherData,
        random: Random = Random.Default,
    ): PrecipitationAlert {
        val kind = kindOf(data.condition)
        val timing = timingOf(data.hourlyForecast)
        val noun = when (kind) {
            PrecipitationKind.RAIN -> "rain"
            PrecipitationKind.SNOW -> "snow"
            PrecipitationKind.STORM -> "storms"
        }

        val summary = buildString {
            append("${data.precipitationChance}% chance of $noun")
            if (timing?.peakTime != null) {
                append(", heaviest around ${timing.peakTime}")
            }
        }

        val detail = buildString {
            append("${data.precipitationChance}% chance of $noun today.")
            if (timing != null) {
                when {
                    timing.start != null && timing.end != null && timing.start != timing.end ->
                        append(" Expect it between ${timing.start} and ${timing.end}.")
                    timing.start != null ->
                        append(" Most likely around ${timing.start}.")
                }
                if (timing.peakTime != null && timing.peakChance > 0) {
                    append(" It peaks at ${timing.peakChance}% around ${timing.peakTime}.")
                }
            }
            append("\n\n")
            append(asides.getValue(kind).random(random))
        }

        return PrecipitationAlert(
            kind = kind,
            headline = headlines.getValue(kind).random(random),
            summary = summary,
            detail = detail,
            location = data.location.takeIf { it.isNotBlank() },
        )
    }

    /** The demo alert behind the "Test notification" button in settings. */
    fun sample(random: Random = Random.Default): PrecipitationAlert {
        val kind = PrecipitationKind.RAIN
        return PrecipitationAlert(
            kind = kind,
            headline = headlines.getValue(kind).random(random),
            summary = "75% chance of rain, heaviest around 16:00",
            detail = "75% chance of rain today. Expect it between 14:00 and 18:00. " +
                "It peaks at 90% around 16:00.\n\n" +
                asides.getValue(kind).random(random),
            location = "Assen",
        )
    }

    private data class Timing(
        val start: String?,
        val end: String?,
        val peakTime: String?,
        val peakChance: Int,
    )

    /** Wet hours, the window they fall in, and the worst of them. */
    private fun timingOf(hourly: List<HourlyForecast>): Timing? {
        if (hourly.isEmpty()) return null
        val wet = hourly.filter { it.precipitationChance > 30 }.take(6)
        val peak = hourly.maxByOrNull { it.precipitationChance }
        val peakChance = peak?.precipitationChance ?: 0
        return Timing(
            start = wet.firstOrNull()?.time,
            end = wet.lastOrNull()?.time,
            peakTime = peak?.time?.takeIf { peakChance > 0 },
            peakChance = peakChance,
        )
    }
}

private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]
