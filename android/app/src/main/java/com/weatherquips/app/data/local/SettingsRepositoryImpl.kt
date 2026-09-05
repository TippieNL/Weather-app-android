package com.weatherquips.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Preferences persistence on DataStore. Survives process death and rotation,
 * and replaces the web app's `localStorage` settings context.
 *
 * The provider API key is the one value that is not stored in the clear: it is
 * encrypted with a Keystore-backed AES/GCM key before it ever touches disk.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val crypto: SecretCipher = ApiKeyCrypto(),
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toAppSettings())
            prefs[Keys.TEMPERATURE_UNIT] = updated.temperatureUnit.id
            prefs[Keys.TIME_FORMAT] = updated.timeFormat.id
            prefs[Keys.DATE_FORMAT] = updated.dateFormat.id
            prefs[Keys.LOCATION_MODE] = updated.locationMode.id
            prefs[Keys.MANUAL_LOCATION] = updated.manualLocation
            prefs[Keys.WEATHER_SERVICE] = updated.weatherService.id
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.POKEMON_MODE] = updated.pokemonMode

            val coords = updated.manualCoords
            if (coords == null) {
                prefs.remove(Keys.MANUAL_LATITUDE)
                prefs.remove(Keys.MANUAL_LONGITUDE)
            } else {
                prefs[Keys.MANUAL_LATITUDE] = coords.latitude
                prefs[Keys.MANUAL_LONGITUDE] = coords.longitude
            }

            if (updated.weatherApiKey.isEmpty()) {
                prefs.remove(Keys.API_KEY)
            } else {
                // Keep the previous ciphertext when the value is unchanged so we
                // don't re-encrypt (and rewrite) on every unrelated settings edit.
                val storedPlain = prefs[Keys.API_KEY]?.let { crypto.decrypt(it) }
                if (storedPlain != updated.weatherApiKey) {
                    crypto.encrypt(updated.weatherApiKey)?.let { prefs[Keys.API_KEY] = it }
                }
            }
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val latitude = this[Keys.MANUAL_LATITUDE]
        val longitude = this[Keys.MANUAL_LONGITUDE]
        return AppSettings(
            temperatureUnit = TemperatureUnit.fromId(this[Keys.TEMPERATURE_UNIT]),
            timeFormat = TimeFormat.fromId(this[Keys.TIME_FORMAT]),
            dateFormat = DateFormat.fromId(this[Keys.DATE_FORMAT]),
            locationMode = LocationMode.fromId(this[Keys.LOCATION_MODE]),
            manualLocation = this[Keys.MANUAL_LOCATION].orEmpty(),
            manualCoords = if (latitude != null && longitude != null) {
                Coordinates(latitude, longitude)
            } else {
                null
            },
            weatherService = WeatherService.fromId(this[Keys.WEATHER_SERVICE]),
            weatherApiKey = this[Keys.API_KEY]?.let { crypto.decrypt(it) }.orEmpty(),
            notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: false,
            pokemonMode = this[Keys.POKEMON_MODE] ?: false,
        )
    }

    private object Keys {
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val MANUAL_LOCATION = stringPreferencesKey("manual_location")
        val MANUAL_LATITUDE = doublePreferencesKey("manual_latitude")
        val MANUAL_LONGITUDE = doublePreferencesKey("manual_longitude")
        val WEATHER_SERVICE = stringPreferencesKey("weather_service")
        val API_KEY = stringPreferencesKey("weather_api_key_encrypted")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val POKEMON_MODE = booleanPreferencesKey("pokemon_mode")
    }
}
