package com.weatherquips.app.data.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A single OkHttp connection pool shared by every API, with conservative
 * timeouts so a stalled provider can never hang a screen.
 *
 * Nominatim's usage policy requires a descriptive User-Agent identifying the
 * application, so one is attached to every outgoing request.
 */
object NetworkModule {

    private const val USER_AGENT = "WeatherQuips-Android/1.0 (https://github.com/TippieNL/Weather-app-android)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build(),
        )
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val openMeteoApi: OpenMeteoApi by lazy {
        retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
    }

    val openWeatherMapApi: OpenWeatherMapApi by lazy {
        retrofit("https://api.openweathermap.org/").create(OpenWeatherMapApi::class.java)
    }

    val weatherApiApi: WeatherApiApi by lazy {
        retrofit("https://api.weatherapi.com/").create(WeatherApiApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        retrofit("https://nominatim.openstreetmap.org/").create(NominatimApi::class.java)
    }

    val rainViewerApi: RainViewerApi by lazy {
        retrofit("https://api.rainviewer.com/").create(RainViewerApi::class.java)
    }
}
