package com.weatherquips.app.data.repository

import com.weatherquips.app.data.api.NominatimApi
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.repository.GeocodingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nominatim geocoding, with the same 24h-style result reuse the web server had
 * (an in-memory cache here, since a city's coordinates don't move).
 */
class GeocodingRepositoryImpl(
    private val api: NominatimApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GeocodingRepository {

    private val searchCache = LruCache<String, List<GeocodeResult>>(maxEntries = 64)
    private val reverseCache = LruCache<String, String>(maxEntries = 64)

    override suspend fun search(query: String): List<GeocodeResult> = withContext(dispatcher) {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query must not be empty" }
        // Cap the length so absurd input never reaches the upstream service.
        val bounded = trimmed.take(MAX_QUERY_LENGTH)
        val cacheKey = bounded.lowercase()

        searchCache.get(cacheKey)?.let { return@withContext it }

        val results = api.search(bounded).mapNotNull { place ->
            val latitude = place.lat.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = place.lon.toDoubleOrNull() ?: return@mapNotNull null
            GeocodeResult(
                latitude = latitude,
                longitude = longitude,
                // Same display rule as the web server: the leading component.
                name = place.displayName.substringBefore(",").trim(),
            )
        }
        if (results.isNotEmpty()) searchCache.put(cacheKey, results)
        results
    }

    override suspend fun reverseGeocode(coordinates: Coordinates): String = withContext(dispatcher) {
        val key = "%.3f,%.3f".format(coordinates.latitude, coordinates.longitude)
        reverseCache.get(key)?.let { return@withContext it }

        // Never fatal: a missing place name must not block the weather itself.
        val name = runCatching {
            val response = api.reverse(coordinates.latitude, coordinates.longitude)
            val address = response.address
            address?.city
                ?: address?.town
                ?: address?.village
                ?: address?.county
                ?: "Unknown location"
        }.getOrDefault("Your location")

        if (name != "Your location") reverseCache.put(key, name)
        name
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
    }
}

/** Tiny insertion-ordered LRU; enough for a couple of dozen place lookups. */
internal class LruCache<K, V>(private val maxEntries: Int) {
    private val entries = LinkedHashMap<K, V>()

    @Synchronized
    fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }
}
