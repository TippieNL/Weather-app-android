package com.weatherquips.app.notifications

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData

/** Title + body of a precipitation alert. */
data class PrecipitationAlert(val title: String, val body: String)

/**
 * Alert wording, ported from the notification effect in `client/src/pages/home.tsx`.
 * Kept pure so it can be unit-tested without an Android framework.
 */
object PrecipitationAlerts {

    const val TITLE = "Precipitation Alert"

    /** The web app's trigger: a high daily chance, or wet/stormy conditions now. */
    fun shouldNotify(data: WeatherData): Boolean =
        data.precipitationChance > 50 ||
            data.condition == WeatherCondition.RAINY ||
            data.condition == WeatherCondition.SNOWY ||
            data.condition == WeatherCondition.STORMY

    fun build(data: WeatherData): PrecipitationAlert {
        val precipType = when (data.condition) {
            WeatherCondition.SNOWY -> "Snow"
            WeatherCondition.STORMY -> "Storms"
            else -> "Rain"
        }
        val body = StringBuilder("$precipType expected — ${data.precipitationChance}% chance today.")

        if (data.hourlyForecast.isNotEmpty()) {
            val upcoming = data.hourlyForecast.filter { it.precipitationChance > 30 }.take(6)
            val peakHour = data.hourlyForecast.maxByOrNull { it.precipitationChance }
            val peakChance = peakHour?.precipitationChance ?: 0

            if (upcoming.isNotEmpty()) {
                val start = upcoming.first().time
                val end = upcoming.last().time
                if (start == end) {
                    body.append(" Most likely around $start.")
                } else {
                    body.append(" Expected between $start–$end.")
                }
            }
            if (peakChance > 0 && peakHour != null) {
                body.append(" Peak: $peakChance% at ${peakHour.time}.")
            }
        }

        body.append(
            when (data.condition) {
                WeatherCondition.SNOWY -> " Bundle up and watch for slippery conditions."
                WeatherCondition.STORMY -> " Stay indoors if you can."
                else -> " You might want an umbrella."
            },
        )

        return PrecipitationAlert(TITLE, body.toString())
    }

    /** The demo alert shown by the "Test notification" button in settings. */
    fun sample(): PrecipitationAlert = PrecipitationAlert(
        TITLE,
        "Rain expected — 75% chance today. Expected between 14:00–18:00. " +
            "Peak: 90% at 16:00. You might want an umbrella.",
    )
}
