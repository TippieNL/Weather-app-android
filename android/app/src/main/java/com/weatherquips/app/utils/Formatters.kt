package com.weatherquips.app.utils

import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Central formatting helpers, ported from `client/src/contexts/settings.tsx`.
 * Every screen goes through these so unit conversion lives in exactly one place.
 */
object Formatters {

    /** Celsius → the selected unit, rounded like the web app's `convertTemp`. */
    fun convertTemp(celsius: Double, unit: TemperatureUnit): Int = when (unit) {
        TemperatureUnit.FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
        TemperatureUnit.CELSIUS -> celsius.roundToInt()
    }

    /** Celsius → "21°C" / "70°F". */
    fun formatTemp(celsius: Double, unit: TemperatureUnit): String {
        val value = convertTemp(celsius, unit)
        return if (unit == TemperatureUnit.FAHRENHEIT) "$value°F" else "$value°C"
    }

    /** Celsius → "21°" for the compact forecast rows. */
    fun formatTempDegrees(celsius: Double, unit: TemperatureUnit): String =
        "${convertTemp(celsius, unit)}°"

    /**
     * "HH:mm" (as returned by the weather APIs) → the selected clock format.
     * Unparseable input is returned untouched rather than throwing.
     */
    fun formatTime(timeStr: String, format: TimeFormat): String {
        if (format == TimeFormat.H24) return timeStr
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return timeStr
        val minute = parts.getOrNull(1) ?: "00"
        val suffix = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$displayHour:$minute $suffix"
    }

    /** Compact hour label used by the hourly strip ("14", "2p"). */
    fun formatHourLabel(timeStr: String, format: TimeFormat): String =
        formatTime(timeStr, format)
            .replace(":00", "")
            .replace(" AM", "a")
            .replace(" PM", "p")

    /** "yyyy-MM-dd" → the selected date format. */
    fun formatDate(dateStr: String, format: DateFormat): String {
        val parts = dateStr.split("-")
        if (parts.size != 3) return dateStr
        val (year, month, day) = parts
        return when (format) {
            DateFormat.MM_DD -> "$month/$day"
            DateFormat.DD_MM -> "$day/$month"
            DateFormat.ISO -> "$year-$month-$day"
        }
    }

    /** Wind speed is always km/h in this app, matching the web UI. */
    fun formatWind(kmh: Double): Int = kmh.roundToInt()

    /** UV index keeps one decimal, exactly like the web API's rounding. */
    fun formatUv(uv: Double): String {
        val rounded = (uv * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
