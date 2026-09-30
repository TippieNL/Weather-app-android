package com.weatherquips.app.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.widget.IntensityBand
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/**
 * Words for data. The model speaks in ids — `cloudy`, `2026-10-01` — which
 * are English, or not words at all; everything a person reads goes through
 * here so it arrives in their language.
 */

@StringRes
fun conditionLabel(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.condition_clear
    WeatherCondition.CLOUDY -> R.string.condition_cloudy
    WeatherCondition.RAINY -> R.string.condition_rainy
    WeatherCondition.STORMY -> R.string.condition_stormy
    WeatherCondition.SNOWY -> R.string.condition_snowy
    WeatherCondition.FOGGY -> R.string.condition_foggy
    WeatherCondition.WINDY -> R.string.condition_windy
    WeatherCondition.HOT -> R.string.condition_hot
    WeatherCondition.COLD -> R.string.condition_cold
}

@StringRes
fun intensityLabel(band: IntensityBand): Int = when (band) {
    IntensityBand.LIGHT -> R.string.intensity_light
    IntensityBand.MODERATE -> R.string.intensity_moderate
    IntensityBand.HEAVY -> R.string.intensity_heavy
    IntensityBand.VIOLENT -> R.string.intensity_violent
}

/** The locale the UI is currently drawn in, which follows the app language. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/**
 * "tomorrow" for the first forecast day, otherwise the weekday in the current
 * language, lowercase to match the app's labels. The providers used to ship
 * this as English text inside the forecast, which no translation could reach.
 */
@Composable
fun forecastDayName(index: Int, isoDate: String): String {
    if (index == 0) return stringResource(R.string.tomorrow)
    return weekdayName(isoDate, currentLocale()) ?: isoDate
}

fun weekdayName(isoDate: String, locale: Locale): String? = try {
    LocalDate.parse(isoDate.take(10)).dayOfWeek
        .getDisplayName(TextStyle.FULL, locale)
        .lowercase(locale)
} catch (_: DateTimeParseException) {
    null
}

/** A number with at most [maxDecimals] decimals, with the locale's own separator. */
fun formatDecimal(value: Double, locale: Locale, maxDecimals: Int = 1): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = maxDecimals
    }.format(value)
