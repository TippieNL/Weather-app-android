package com.weatherquips.app.widget

import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.notifications.PrecipitationKind
import com.weatherquips.app.notifications.PrecipitationAlerts

/** One hour of the widget's chart. */
data class OutlookHour(
    val label: String,
    val chancePercent: Int,
    val isNow: Boolean,
)

/** What the widget leads with. */
sealed interface Outlook {
    /** It is already falling. */
    data class FallingNow(val kind: PrecipitationKind) : Outlook

    /** Dry now, but not for long. */
    data class StartsAt(val kind: PrecipitationKind, val time: String, val chancePercent: Int) : Outlook

    /** Nothing worth mentioning in the window. */
    data object Dry : Outlook
}

/** Everything the widget draws, derived once so the UI stays dumb. */
data class PrecipitationOutlook(
    val outlook: Outlook,
    val hours: List<OutlookHour>,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val location: String,
    val updatedAtMillis: Long,
    /** Where the forecast is for, so a tap can open the radar on it. */
    val coordinates: Coordinates,
)

/**
 * Turns a cached forecast into the widget's view of the next few hours.
 *
 * Pure on purpose: what the widget *says* is the part worth testing, and none
 * of it needs an Android framework to decide.
 */
object PrecipitationOutlooks {

    /** Hours drawn in the chart. Six is about as many as fits a 4-cell widget. */
    const val WINDOW_HOURS = 6

    /** Worth warning about. Below this, a forecast is noise. */
    const val LIKELY_THRESHOLD = 40

    /** High enough to call it already happening. */
    private const val FALLING_NOW_THRESHOLD = 60

    fun from(cached: CachedWeather): PrecipitationOutlook {
        val data = cached.data
        val window = data.hourlyForecast.take(WINDOW_HOURS)

        return PrecipitationOutlook(
            outlook = outlookFor(data.condition, window),
            hours = window.mapIndexed { index, hour ->
                OutlookHour(
                    label = hour.time.take(5),
                    chancePercent = hour.chancePercent(),
                    isNow = index == 0,
                )
            },
            condition = data.condition,
            isDay = data.isDay,
            location = data.location,
            updatedAtMillis = cached.fetchedAtEpochMillis,
            coordinates = cached.coordinates,
        )
    }

    private fun outlookFor(
        condition: WeatherCondition,
        window: List<HourlyForecast>,
    ): Outlook {
        val kind = PrecipitationAlerts.kindOf(condition)
        val fallingNow = condition == WeatherCondition.RAINY ||
            condition == WeatherCondition.SNOWY ||
            condition == WeatherCondition.STORMY ||
            (window.firstOrNull()?.chancePercent() ?: 0) >= FALLING_NOW_THRESHOLD
        if (fallingNow) return Outlook.FallingNow(kind)

        // The first hour is "now", which is already known to be dry here.
        val next = window.drop(1).firstOrNull { it.chancePercent() >= LIKELY_THRESHOLD }
            ?: return Outlook.Dry

        return Outlook.StartsAt(
            kind = kind,
            time = next.time.take(5),
            chancePercent = next.chancePercent(),
        )
    }

    private fun HourlyForecast.chancePercent() = precipitationChance.coerceIn(0, 100)
}
