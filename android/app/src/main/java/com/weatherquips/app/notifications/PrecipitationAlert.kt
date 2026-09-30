package com.weatherquips.app.notifications

import android.content.res.Resources
import com.weatherquips.app.R
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
 * The words an alert is made of, in some language.
 *
 * Whole sentences with slots rather than fragments to glue together, so a
 * translation can put the time before the chance, or the noun anywhere.
 */
interface AlertText {
    fun headlines(kind: PrecipitationKind): List<String>
    fun asides(kind: PrecipitationKind): List<String>
    fun summary(chance: Int, kind: PrecipitationKind): String
    fun summaryWithPeak(chance: Int, kind: PrecipitationKind, peakTime: String): String
    fun detailChance(chance: Int, kind: PrecipitationKind): String
    fun detailWindow(start: String, end: String): String
    fun detailAround(time: String): String
    fun detailPeak(chance: Int, time: String): String
}

/** [AlertText] from string resources — res/values/alerts.xml and its translations. */
class ResourceAlertText(private val resources: Resources) : AlertText {

    override fun headlines(kind: PrecipitationKind): List<String> = resources.getStringArray(
        when (kind) {
            PrecipitationKind.RAIN -> R.array.alert_headlines_rain
            PrecipitationKind.SNOW -> R.array.alert_headlines_snow
            PrecipitationKind.STORM -> R.array.alert_headlines_storm
        },
    ).toList()

    override fun asides(kind: PrecipitationKind): List<String> = resources.getStringArray(
        when (kind) {
            PrecipitationKind.RAIN -> R.array.alert_asides_rain
            PrecipitationKind.SNOW -> R.array.alert_asides_snow
            PrecipitationKind.STORM -> R.array.alert_asides_storm
        },
    ).toList()

    private fun noun(kind: PrecipitationKind) = resources.getString(
        when (kind) {
            PrecipitationKind.RAIN -> R.string.alert_noun_rain
            PrecipitationKind.SNOW -> R.string.alert_noun_snow
            PrecipitationKind.STORM -> R.string.alert_noun_storm
        },
    )

    override fun summary(chance: Int, kind: PrecipitationKind) =
        resources.getString(R.string.alert_summary, chance, noun(kind))

    override fun summaryWithPeak(chance: Int, kind: PrecipitationKind, peakTime: String) =
        resources.getString(R.string.alert_summary_peak, chance, noun(kind), peakTime)

    override fun detailChance(chance: Int, kind: PrecipitationKind) =
        resources.getString(R.string.alert_detail_chance, chance, noun(kind))

    override fun detailWindow(start: String, end: String) =
        resources.getString(R.string.alert_detail_window, start, end)

    override fun detailAround(time: String) = resources.getString(R.string.alert_detail_around, time)

    override fun detailPeak(chance: Int, time: String) =
        resources.getString(R.string.alert_detail_peak, chance, time)
}

/**
 * Alert wording, in the same voice as the quips on the home screen.
 *
 * Pure: the words come in through [AlertText], so the decisions — what to
 * say, when, and which data backs it — are testable without Android. Free of
 * the `**highlight**` markers the in-app quotes use: a notification is plain
 * text, so a marker would be shown to the user rather than rendered.
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

    fun build(
        data: WeatherData,
        text: AlertText,
        random: Random = Random.Default,
    ): PrecipitationAlert {
        val kind = kindOf(data.condition)
        val timing = timingOf(data.hourlyForecast)
        val chance = data.precipitationChance

        val summary = if (timing?.peakTime != null) {
            text.summaryWithPeak(chance, kind, timing.peakTime)
        } else {
            text.summary(chance, kind)
        }

        val sentences = buildList {
            add(text.detailChance(chance, kind))
            if (timing != null) {
                when {
                    timing.start != null && timing.end != null && timing.start != timing.end ->
                        add(text.detailWindow(timing.start, timing.end))
                    timing.start != null -> add(text.detailAround(timing.start))
                }
                if (timing.peakTime != null && timing.peakChance > 0) {
                    add(text.detailPeak(timing.peakChance, timing.peakTime))
                }
            }
        }

        return PrecipitationAlert(
            kind = kind,
            headline = text.headlines(kind).random(random),
            summary = summary,
            detail = sentences.joinToString(" ") + "\n\n" + text.asides(kind).random(random),
            location = data.location.takeIf { it.isNotBlank() },
        )
    }

    /** The demo alert behind the "Test notification" button in settings. */
    fun sample(text: AlertText, random: Random = Random.Default): PrecipitationAlert {
        val kind = PrecipitationKind.RAIN
        val detail = listOf(
            text.detailChance(75, kind),
            text.detailWindow("14:00", "18:00"),
            text.detailPeak(90, "16:00"),
        ).joinToString(" ")
        return PrecipitationAlert(
            kind = kind,
            headline = text.headlines(kind).random(random),
            summary = text.summaryWithPeak(75, kind, "16:00"),
            detail = detail + "\n\n" + text.asides(kind).random(random),
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
