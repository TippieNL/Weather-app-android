package com.weatherquips.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherApiResponse(
    val location: WeatherApiLocation? = null,
    val current: WeatherApiCurrent,
    val forecast: WeatherApiForecast,
)

@Serializable
data class WeatherApiLocation(val name: String? = null, val localtime: String? = null)

@Serializable
data class WeatherApiCurrent(
    @SerialName("temp_c") val tempC: Double,
    @SerialName("is_day") val isDay: Int? = null,
    val condition: WeatherApiCondition,
    @SerialName("wind_kph") val windKph: Double,
    @SerialName("feelslike_c") val feelsLikeC: Double,
    val humidity: Int,
    @SerialName("pressure_mb") val pressureMb: Double,
    val uv: Double = 0.0,
)

@Serializable
data class WeatherApiCondition(val code: Int, val text: String? = null)

@Serializable
data class WeatherApiForecast(val forecastday: List<WeatherApiForecastDay> = emptyList())

@Serializable
data class WeatherApiForecastDay(
    val date: String,
    val day: WeatherApiDay,
    val hour: List<WeatherApiHour> = emptyList(),
)

@Serializable
data class WeatherApiDay(
    @SerialName("maxtemp_c") val maxTempC: Double,
    @SerialName("mintemp_c") val minTempC: Double,
    @SerialName("daily_chance_of_rain") val dailyChanceOfRain: Int? = null,
)

@Serializable
data class WeatherApiHour(
    val time: String,
    @SerialName("temp_c") val tempC: Double,
    @SerialName("chance_of_rain") val chanceOfRain: Int? = null,
)
