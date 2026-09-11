package com.weatherquips.app.data.repository

import com.weatherquips.app.data.local.WeatherCache
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.quotes.FunnyQuotes
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.domain.repository.RadarNowcastRepository
import com.weatherquips.app.domain.repository.WeatherError
import com.weatherquips.app.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Picks the configured provider, attaches the place name and a fresh funny
 * quote, and keeps the last successful result for offline use.
 *
 * The UI never talks to an API directly: Compose → ViewModel → this → provider.
 */
class WeatherRepositoryImpl(
    private val providers: List<WeatherProvider>,
    private val geocodingRepository: GeocodingRepository,
    private val cache: WeatherCache,
    /**
     * Optional radar nowcast, used in place of the provider's own minute-level
     * figures where there is radar to use. Null everywhere it has no coverage.
     */
    private val radarNowcast: RadarNowcastRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
    /**
     * Called after a fresh result is cached. The home-screen widget renders
     * from that cache, so this is what keeps it in step with the app without
     * the repository knowing anything about widgets.
     */
    private val onCacheUpdated: suspend () -> Unit = {},
) : WeatherRepository {

    override suspend fun getWeather(
        coordinates: Coordinates,
        service: WeatherService,
        apiKey: String,
    ): WeatherData = withContext(dispatcher) {
        if (service.requiresApiKey && apiKey.isBlank()) throw WeatherError.MissingApiKey

        val provider = providers.firstOrNull { it.service == service }
            ?: providers.first { it.service == WeatherService.OPEN_METEO }

        val locationName = geocodingRepository.reverseGeocode(coordinates)

        val data = try {
            provider.fetch(coordinates, apiKey, locationName)
        } catch (error: Throwable) {
            throw error.toWeatherError()
        }

        // The web server regenerated the quote on every response — even cache
        // hits — so refreshing always produces a new quip. Same here, and the
        // set depends on whether the sun is up at the location.
        val quip = FunnyQuotes.random(data.condition, data.isDay, random)
        val withQuote = data.copy(funnyQuote = quip.quote, subtitle = quip.subtitle)

        // Radar beats the model for the next two hours, and it is what the
        // app's own map is showing. Best effort: no radar, or a radar that is
        // down, must never cost the user their forecast.
        val radar = radarNowcast
            ?.let { runCatching { it.nowcast(coordinates) }.getOrNull() }
        val withNowcast =
            if (radar.isNullOrEmpty()) withQuote else withQuote.copy(nowcast = radar)

        cache.write(
            CachedWeather(
                data = withNowcast,
                fetchedAtEpochMillis = System.currentTimeMillis(),
                coordinates = coordinates,
            ),
        )
        onCacheUpdated()
        withNowcast
    }

    override suspend fun getCachedWeather(): CachedWeather? =
        withContext(dispatcher) { cache.read() }

    override fun cachedWeather(): Flow<CachedWeather?> = cache.cached
}
