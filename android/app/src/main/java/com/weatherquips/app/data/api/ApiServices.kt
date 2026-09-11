package com.weatherquips.app.data.api

import com.weatherquips.app.data.model.NominatimPlace
import com.weatherquips.app.data.model.NominatimReverse
import com.weatherquips.app.data.model.OpenMeteoResponse
import com.weatherquips.app.data.model.OwmCurrentResponse
import com.weatherquips.app.data.model.OwmForecastResponse
import com.weatherquips.app.data.model.RainViewerMaps
import com.weatherquips.app.data.model.WeatherApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("minutely_15") minutely15: String = MINUTELY_15_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("forecast_minutely_15") forecastMinutely15: Int = NOWCAST_FORECAST_STEPS,
        @Query("past_minutely_15") pastMinutely15: Int = NOWCAST_PAST_STEPS,
    ): OpenMeteoResponse

    companion object {
        // Same field set the web server requested, plus sunrise/sunset which the
        // Android app uses as a day/night fallback when `is_day` is missing.
        const val CURRENT_FIELDS =
            "temperature_2m,weather_code,wind_speed_10m,apparent_temperature," +
                "relative_humidity_2m,surface_pressure,uv_index,is_day"
        const val DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                "uv_index_max,weather_code,sunrise,sunset"
        const val HOURLY_FIELDS = "temperature_2m,precipitation_probability,precipitation"

        /** Drives the widget's intensity graph; free and key-less like the rest. */
        const val MINUTELY_15_FIELDS = "precipitation"

        /** Two hours ahead in quarter-hour steps, counting the current bucket. */
        const val NOWCAST_FORECAST_STEPS = 9

        /**
         * Half an hour of history, so the graph can show a "now" line with
         * something behind it rather than starting at the left edge.
         */
        const val NOWCAST_PAST_STEPS = 2
    }
}

interface OpenWeatherMapApi {
    @GET("data/2.5/weather")
    suspend fun current(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
    ): OwmCurrentResponse

    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
    ): OwmForecastResponse
}

interface WeatherApiApi {
    @GET("v1/forecast.json")
    suspend fun forecast(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("days") days: Int = 7,
        @Query("aqi") aqi: String = "no",
    ): WeatherApiResponse
}

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("zoom") zoom: Int = 10,
    ): NominatimReverse
}

interface RainViewerApi {
    @GET("public/weather-maps.json")
    suspend fun weatherMaps(): RainViewerMaps
}
