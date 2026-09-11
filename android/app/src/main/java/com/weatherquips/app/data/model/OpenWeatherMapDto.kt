package com.weatherquips.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwmCurrentResponse(
    val weather: List<OwmWeather> = emptyList(),
    val main: OwmMain,
    val wind: OwmWind = OwmWind(),
    val dt: Long? = null,
    val timezone: Int? = null,
    val sys: OwmSys? = null,
)

@Serializable
data class OwmWeather(val id: Int, val main: String? = null, val icon: String? = null)

@Serializable
data class OwmMain(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    @SerialName("temp_min") val tempMin: Double,
    @SerialName("temp_max") val tempMax: Double,
    val pressure: Int,
    val humidity: Int,
)

@Serializable
data class OwmWind(val speed: Double = 0.0)

@Serializable
data class OwmSys(val sunrise: Long? = null, val sunset: Long? = null)

@Serializable
data class OwmForecastResponse(val list: List<OwmForecastEntry> = emptyList())

@Serializable
data class OwmForecastEntry(
    val dt: Long,
    val main: OwmMain,
    val pop: Double? = null,
    val rain: OwmVolume? = null,
    val snow: OwmVolume? = null,
)

/** Volume over the entry's three-hour window, in millimetres. */
@Serializable
data class OwmVolume(@SerialName("3h") val threeHours: Double = 0.0)
