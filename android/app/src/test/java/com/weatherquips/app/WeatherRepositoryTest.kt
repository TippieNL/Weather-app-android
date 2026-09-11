package com.weatherquips.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.weatherquips.app.data.local.WeatherCache
import com.weatherquips.app.data.repository.WeatherProvider
import com.weatherquips.app.data.repository.WeatherRepositoryImpl
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.GeocodeResult
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.quotes.FunnyQuotes
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.domain.repository.RadarNowcastRepository
import com.weatherquips.app.domain.repository.WeatherError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Provider selection, quote attachment, caching and error translation. */
class WeatherRepositoryTest {

    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cache: WeatherCache

    private val dispatcher = UnconfinedTestDispatcher()
    private val coordinates = Coordinates(52.99, 6.56)

    @Before
    fun setUp() {
        file = File.createTempFile("weather-cache", ".preferences_pb").apply { delete() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        cache = WeatherCache(dataStore, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private class FakeProvider(
        override val service: WeatherService,
        private val result: Result<WeatherData> = Result.success(TestWeather.sample()),
    ) : WeatherProvider {
        var calls = 0
            private set
        var lastLocationName: String? = null
            private set

        override suspend fun fetch(
            coordinates: Coordinates,
            apiKey: String,
            locationName: String,
        ): WeatherData {
            calls++
            lastLocationName = locationName
            return result.getOrThrow()
        }
    }

    private class FakeGeocoding(private val name: String = "Assen") : GeocodingRepository {
        override suspend fun search(query: String): List<GeocodeResult> = emptyList()
        override suspend fun reverseGeocode(coordinates: Coordinates): String = name
    }

    private class FakeRadar(
        private val result: Result<List<NowcastPoint>?>,
    ) : RadarNowcastRepository {
        var calls = 0
            private set

        override suspend fun nowcast(coordinates: Coordinates): List<NowcastPoint>? {
            calls++
            return result.getOrThrow()
        }
    }

    private fun repository(
        vararg providers: WeatherProvider,
        radar: RadarNowcastRepository? = null,
    ) = WeatherRepositoryImpl(
        providers = providers.toList(),
        geocodingRepository = FakeGeocoding(),
        cache = cache,
        radarNowcast = radar,
        dispatcher = dispatcher,
    )

    private val radarSeries = listOf(
        NowcastPoint("18:00", 0, 0.4),
        NowcastPoint("18:05", 5, 1.2),
        NowcastPoint("18:10", 10, 0.8),
    )

    @Test
    fun `radar replaces the provider's own minute-level figures`() = runTest {
        val provider = FakeProvider(
            WeatherService.OPEN_METEO,
            Result.success(
                TestWeather.sample().copy(
                    nowcast = listOf(NowcastPoint("18:00", 0, 0.0)),
                ),
            ),
        )

        val weather = repository(provider, radar = FakeRadar(Result.success(radarSeries)))
            .getWeather(coordinates, WeatherService.OPEN_METEO, "")

        assertEquals(radarSeries, weather.nowcast)
        // And it is the radar series that gets cached for the widget.
        assertEquals(radarSeries, cache.read()?.data?.nowcast)
    }

    @Test
    fun `no radar coverage leaves the provider's forecast alone`() = runTest {
        val model = listOf(NowcastPoint("18:00", 0, 0.7))
        val provider = FakeProvider(
            WeatherService.OPEN_METEO,
            Result.success(TestWeather.sample().copy(nowcast = model)),
        )

        val weather = repository(provider, radar = FakeRadar(Result.success(null)))
            .getWeather(coordinates, WeatherService.OPEN_METEO, "")

        assertEquals(model, weather.nowcast)
    }

    @Test
    fun `a radar outage never costs the user their forecast`() = runTest {
        val provider = FakeProvider(WeatherService.OPEN_METEO)
        val radar = FakeRadar(Result.failure(IOException("radar down")))

        val weather = repository(provider, radar = radar)
            .getWeather(coordinates, WeatherService.OPEN_METEO, "")

        assertEquals(1, radar.calls)
        assertNotNull(weather.location)
        assertNotNull(cache.read())
    }

    @Test
    fun `the configured provider is the one that runs`() = runTest {
        val openMeteo = FakeProvider(WeatherService.OPEN_METEO)
        val weatherApi = FakeProvider(WeatherService.WEATHER_API)

        repository(openMeteo, weatherApi)
            .getWeather(coordinates, WeatherService.WEATHER_API, "a-key")

        assertEquals(0, openMeteo.calls)
        assertEquals(1, weatherApi.calls)
    }

    @Test
    fun `a fresh quote is attached to every result`() = runTest {
        val provider = FakeProvider(
            WeatherService.OPEN_METEO,
            Result.success(TestWeather.sample(quote = "", subtitle = "")),
        )

        val weather = repository(provider).getWeather(coordinates, WeatherService.OPEN_METEO, "")

        assertTrue(weather.funnyQuote in FunnyQuotes.quotesFor(WeatherCondition.CLOUDY, isDay = false))
        assertTrue(weather.subtitle in FunnyQuotes.subtitlesFor(WeatherCondition.CLOUDY, isDay = false))
    }

    @Test
    fun `the quote follows the sun at the location`() = runTest {
        // TestWeather.sample() is a night reading (isDay = false).
        val night = repository(FakeProvider(WeatherService.OPEN_METEO))
            .getWeather(coordinates, WeatherService.OPEN_METEO, "")
        assertTrue(
            "used a daytime quote after dark: ${night.funnyQuote}",
            night.funnyQuote in FunnyQuotes.quotesFor(WeatherCondition.CLOUDY, isDay = false),
        )

        val dayProvider = FakeProvider(
            WeatherService.OPEN_METEO,
            Result.success(TestWeather.sample().copy(isDay = true)),
        )
        val day = repository(dayProvider).getWeather(coordinates, WeatherService.OPEN_METEO, "")
        assertTrue(
            "used a night quote in daylight: ${day.funnyQuote}",
            day.funnyQuote in FunnyQuotes.quotesFor(WeatherCondition.CLOUDY, isDay = true),
        )
    }

    @Test
    fun `the place name is resolved once and handed to the provider`() = runTest {
        val provider = FakeProvider(WeatherService.OPEN_METEO)
        repository(provider).getWeather(coordinates, WeatherService.OPEN_METEO, "")
        assertEquals("Assen", provider.lastLocationName)
    }

    @Test
    fun `a successful fetch is cached for offline use`() = runTest {
        val repository = repository(FakeProvider(WeatherService.OPEN_METEO))
        assertNull(repository.getCachedWeather())

        val weather = repository.getWeather(coordinates, WeatherService.OPEN_METEO, "")

        val cached = repository.getCachedWeather()
        assertNotNull(cached)
        assertEquals(weather, cached!!.data)
        assertEquals(coordinates, cached.coordinates)
        assertTrue(cached.fetchedAtEpochMillis > 0)
    }

    @Test
    fun `providers that need a key are not called without one`() = runTest {
        val provider = FakeProvider(WeatherService.OPEN_WEATHER_MAP)
        val error = runCatching {
            repository(provider).getWeather(coordinates, WeatherService.OPEN_WEATHER_MAP, "  ")
        }.exceptionOrNull()

        assertTrue(error is WeatherError.MissingApiKey)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `transport failures become typed errors, never raw exceptions`() = runTest {
        suspend fun errorFor(cause: Throwable): Throwable? = runCatching {
            repository(FakeProvider(WeatherService.OPEN_METEO, Result.failure(cause)))
                .getWeather(coordinates, WeatherService.OPEN_METEO, "")
        }.exceptionOrNull()

        assertTrue(errorFor(UnknownHostException("dns")) is WeatherError.NoInternet)
        assertTrue(errorFor(SocketTimeoutException()) is WeatherError.Timeout)
        assertTrue(errorFor(IOException("socket closed")) is WeatherError.NoInternet)
        assertTrue(errorFor(IllegalStateException("boom")) is WeatherError.Unknown)
    }

    @Test
    fun `a failed fetch leaves the previous cache intact`() = runTest {
        val good = repository(FakeProvider(WeatherService.OPEN_METEO))
        good.getWeather(coordinates, WeatherService.OPEN_METEO, "")
        val before = good.getCachedWeather()

        val bad = repository(
            FakeProvider(WeatherService.OPEN_METEO, Result.failure(UnknownHostException())),
        )
        runCatching { bad.getWeather(coordinates, WeatherService.OPEN_METEO, "") }

        assertEquals(before, bad.getCachedWeather())
    }

    @Test
    fun `an unknown provider falls back to the key-free default`() = runTest {
        val openMeteo = FakeProvider(WeatherService.OPEN_METEO)
        repository(openMeteo).getWeather(coordinates, WeatherService.WEATHER_API, "key")
        assertEquals(1, openMeteo.calls)
    }
}
