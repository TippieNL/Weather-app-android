package com.weatherquips.app

import com.weatherquips.app.data.api.NominatimApi
import com.weatherquips.app.data.model.NominatimAddress
import com.weatherquips.app.data.model.NominatimPlace
import com.weatherquips.app.data.model.NominatimReverse
import com.weatherquips.app.data.repository.GeocodingRepositoryImpl
import com.weatherquips.app.domain.model.Coordinates
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GeocodingRepositoryTest {

    private class FakeNominatim(
        private val places: List<NominatimPlace> = emptyList(),
        private val reverse: NominatimReverse? = null,
        private val failure: Throwable? = null,
    ) : NominatimApi {
        var searchCalls = 0
            private set

        override suspend fun search(query: String, format: String, limit: Int): List<NominatimPlace> {
            searchCalls++
            failure?.let { throw it }
            return places
        }

        override suspend fun reverse(
            latitude: Double,
            longitude: Double,
            format: String,
            zoom: Int,
        ): NominatimReverse {
            failure?.let { throw it }
            return reverse ?: NominatimReverse()
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `search returns every match with a short display name`() = runTest {
        val api = FakeNominatim(
            places = listOf(
                NominatimPlace("52.99", "6.56", "Assen, Drenthe, Netherlands"),
                NominatimPlace("41.0", "-74.0", "Assen, New Jersey, United States"),
            ),
        )

        val results = GeocodingRepositoryImpl(api, dispatcher).search("Assen")

        assertEquals(2, results.size)
        assertEquals("Assen", results[0].name)
        assertEquals(52.99, results[0].latitude, 0.001)
        assertEquals(6.56, results[0].longitude, 0.001)
    }

    @Test
    fun `no results is an empty list, not an error`() = runTest {
        val results = GeocodingRepositoryImpl(FakeNominatim(), dispatcher).search("Nowhereville")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `unparseable coordinates are skipped`() = runTest {
        val api = FakeNominatim(places = listOf(NominatimPlace("north", "west", "Broken, Place")))
        assertTrue(GeocodingRepositoryImpl(api, dispatcher).search("Broken").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty query is rejected before hitting the network`() = runTest {
        GeocodingRepositoryImpl(FakeNominatim(), dispatcher).search("   ")
    }

    @Test
    fun `repeated searches reuse the cached result`() = runTest {
        val api = FakeNominatim(places = listOf(NominatimPlace("1.0", "2.0", "Place, Country")))
        val repository = GeocodingRepositoryImpl(api, dispatcher)

        repository.search("Place")
        repository.search("place")

        assertEquals(1, api.searchCalls)
    }

    @Test
    fun `network errors propagate so the caller can show a message`() = runTest {
        val api = FakeNominatim(failure = IOException("offline"))
        val repository = GeocodingRepositoryImpl(api, dispatcher)
        val error = runCatching { repository.search("Assen") }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun `reverse geocoding prefers city then town then village`() = runTest {
        val city = GeocodingRepositoryImpl(
            FakeNominatim(reverse = NominatimReverse(NominatimAddress(city = "Assen", town = "T"))),
            dispatcher,
        ).reverseGeocode(Coordinates(52.99, 6.56))
        assertEquals("Assen", city)

        val village = GeocodingRepositoryImpl(
            FakeNominatim(reverse = NominatimReverse(NominatimAddress(village = "Rolde"))),
            dispatcher,
        ).reverseGeocode(Coordinates(53.0, 6.6))
        assertEquals("Rolde", village)
    }

    @Test
    fun `reverse geocoding never throws - it falls back to a friendly label`() = runTest {
        val name = GeocodingRepositoryImpl(FakeNominatim(failure = IOException()), dispatcher)
            .reverseGeocode(Coordinates(0.0, 0.0))
        assertEquals("Your location", name)
    }
}
