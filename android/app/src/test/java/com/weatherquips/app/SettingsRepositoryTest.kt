package com.weatherquips.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.weatherquips.app.data.local.SecretCipher
import com.weatherquips.app.data.local.SettingsRepositoryImpl
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.domain.model.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Settings persistence. The store is a real DataStore backed by a temp file, so
 * this covers the actual serialization path a process restart would take.
 */
class SettingsRepositoryTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    /** Stand-in for the Keystore cipher, which needs a device. */
    private class ReversibleCipher : SecretCipher {
        override fun encrypt(plainText: String): String =
            Base64.getEncoder().encodeToString(plainText.toByteArray())

        override fun decrypt(cipherText: String): String = runCatching {
            String(Base64.getDecoder().decode(cipherText))
        }.getOrDefault("")
    }

    @Before
    fun setUp() {
        file = File.createTempFile("settings", ".preferences_pb").apply { delete() }
        // DataStore keeps its own scope; using real IO threads here avoids
        // clashing with runTest's virtual-time scheduler.
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private fun repository() = SettingsRepositoryImpl(dataStore, ReversibleCipher())

    @Test
    fun `defaults match the web app`() = runTest {
        val settings = repository().current()

        assertEquals(TemperatureUnit.CELSIUS, settings.temperatureUnit)
        assertEquals(TimeFormat.H24, settings.timeFormat)
        assertEquals(DateFormat.DD_MM, settings.dateFormat)
        assertEquals(LocationMode.DEVICE, settings.locationMode)
        assertEquals(WeatherService.OPEN_METEO, settings.weatherService)
        assertEquals("", settings.weatherApiKey)
        assertEquals(false, settings.notificationsEnabled)
        assertEquals(false, settings.pokemonMode)
        assertNull(settings.manualCoords)
    }

    @Test
    fun `values survive being read back through a new repository instance`() = runTest {
        repository().update {
            it.copy(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                timeFormat = TimeFormat.H12,
                dateFormat = DateFormat.ISO,
                locationMode = LocationMode.MANUAL,
                manualLocation = "Assen",
                manualCoords = Coordinates(52.99, 6.56),
                weatherService = WeatherService.WEATHER_API,
                notificationsEnabled = true,
            )
        }

        // A brand-new instance is what a process restart would build.
        val restored = repository().current()

        assertEquals(TemperatureUnit.FAHRENHEIT, restored.temperatureUnit)
        assertEquals(TimeFormat.H12, restored.timeFormat)
        assertEquals(DateFormat.ISO, restored.dateFormat)
        assertEquals(LocationMode.MANUAL, restored.locationMode)
        assertEquals("Assen", restored.manualLocation)
        assertEquals(Coordinates(52.99, 6.56), restored.manualCoords)
        assertEquals(WeatherService.WEATHER_API, restored.weatherService)
        assertTrue(restored.notificationsEnabled)
    }

    @Test
    fun `api keys are never written in the clear`() = runTest {
        repository().update { it.copy(weatherApiKey = "super-secret-key") }

        assertEquals("super-secret-key", repository().current().weatherApiKey)
        val onDisk = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("plaintext key found on disk", !onDisk.contains("super-secret-key"))
    }

    @Test
    fun `clearing the manual location removes its coordinates`() = runTest {
        val repo = repository()
        repo.update { it.copy(manualCoords = Coordinates(1.0, 2.0)) }
        assertEquals(Coordinates(1.0, 2.0), repo.current().manualCoords)

        repo.update { it.copy(manualCoords = null) }
        assertNull(repo.current().manualCoords)
    }

    @Test
    fun `pokemon mode is only active in manual location mode`() = runTest {
        val repo = repository()
        repo.update { it.copy(pokemonMode = true, locationMode = LocationMode.DEVICE) }
        assertTrue(!repo.current().isPokemonModeActive)

        repo.update { it.copy(locationMode = LocationMode.MANUAL) }
        assertTrue(repo.current().isPokemonModeActive)
    }
}
