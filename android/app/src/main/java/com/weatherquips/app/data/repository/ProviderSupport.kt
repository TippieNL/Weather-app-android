package com.weatherquips.app.data.repository

import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.repository.WeatherError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** One weather provider. Each normalizes its API into the app's [WeatherData]. */
interface WeatherProvider {
    val service: WeatherService

    /**
     * @param locationName resolved place name, passed in so providers don't each
     *                     have to reverse-geocode.
     */
    suspend fun fetch(coordinates: Coordinates, apiKey: String, locationName: String): WeatherData
}

internal object ProviderSupport {

    private val WEEKDAYS = listOf(
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
    )

    /**
     * Day label for a forecast row: the first upcoming day is "tomorrow", the
     * rest are lowercase weekday names — same as the web server.
     */
    fun dayLabel(index: Int, isoDate: String): String {
        if (index == 0) return "tomorrow"
        val date = parseIsoDate(isoDate) ?: return "tomorrow"
        // java.time's Sunday is 7; the web used getUTCDay() where Sunday is 0.
        val dayOfWeek = date.dayOfWeek.value % 7
        return WEEKDAYS[dayOfWeek]
    }

    fun parseIsoDate(isoDate: String): LocalDate? = try {
        LocalDate.parse(isoDate.take(10))
    } catch (_: DateTimeParseException) {
        null
    }

    /** "2026-09-05T14:00" or "2026-09-05 14:00" → "14:00". */
    fun hourLabel(timestamp: String): String =
        if (timestamp.length >= 13) timestamp.substring(11, 13) + ":00" else timestamp

    /** "2026-09-05T14:15" or "2026-09-05 14:15" -> "14:15". */
    fun minuteLabel(timestamp: String): String =
        if (timestamp.length >= 16) timestamp.substring(11, 16) else hourLabel(timestamp)

    /** Whole minutes from [from] to [to], or null when either cannot be parsed. */
    fun minutesBetween(from: String, to: String): Int? {
        val start = parseLocalDateTime(from) ?: return null
        val end = parseLocalDateTime(to) ?: return null
        return Duration.between(start, end).toMinutes().toInt()
    }

    private fun parseLocalDateTime(timestamp: String): LocalDateTime? = try {
        LocalDateTime.parse(timestamp.take(16).replace(' ', 'T'))
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Nowcast for providers that only publish hourly totals.
     *
     * Coarse by necessity: one sample an hour, so the widget's graph is a
     * rough shape rather than the minute-by-minute picture Open-Meteo gives.
     * The chart labels itself hourly in this case, so it does not pretend
     * otherwise.
     */
    fun nowcastFromHourly(hours: List<HourlyForecast>, count: Int = 4): List<NowcastPoint> =
        hours.take(count).mapIndexed { index, hour ->
            NowcastPoint(
                time = hour.time,
                minutesFromNow = index * 60,
                millimetresPerHour = hour.precipitationMm.coerceAtLeast(0.0),
            )
        }

    /**
     * Index of the first hourly entry at or after [nowLocal].
     *
     * The web app indexed the location's hourly array with the *device's* hour,
     * which is wrong whenever the chosen city is in another timezone (a first
     * class feature here). Matching timestamps fixes that; if the timestamps
     * can't be compared we fall back to the old behaviour.
     */
    fun firstUpcomingHourIndex(times: List<String>, nowLocal: String?, deviceHour: Int): Int {
        if (times.isEmpty()) return 0
        if (nowLocal != null && nowLocal.length >= 13) {
            val marker = nowLocal.substring(0, 13)
            val index = times.indexOfFirst { it.length >= 13 && it.substring(0, 13) >= marker }
            if (index >= 0) return index
        }
        return deviceHour.coerceIn(0, (times.size - 1).coerceAtLeast(0))
    }
}

/** Turns transport/HTTP failures into the app's typed [WeatherError]s. */
internal fun Throwable.toWeatherError(): WeatherError = when (this) {
    is WeatherError -> this
    is UnknownHostException -> WeatherError.NoInternet
    is SocketTimeoutException -> WeatherError.Timeout
    is HttpException -> when (code()) {
        401, 403 -> WeatherError.InvalidApiKey
        429 -> WeatherError.RateLimited
        else -> WeatherError.Server(code())
    }
    is SerializationException -> WeatherError.MalformedResponse
    is IOException -> WeatherError.NoInternet
    else -> WeatherError.Unknown
}
