package com.weatherquips.app.data.repository

import com.weatherquips.app.data.local.WeatherCache
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.quotes.FunnyQuotes
import com.weatherquips.app.domain.repository.GeocodingRepository
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
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

        cache.write(
            CachedWeather(
                data = withQuote,
                fetchedAtEpochMillis = System.currentTimeMillis(),
                coordinates = coordinates,
            ),
        )
        withQuote
    }

    override suspend fun getCachedWeather(): CachedWeather? =
        withContext(dispatcher) { cache.read() }

    override fun cachedWeather(): Flow<CachedWeather?> = cache.cached
}
