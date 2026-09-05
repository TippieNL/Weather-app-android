package com.weatherquips.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.weatherquips.app.domain.model.CachedWeather
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Offline support: the last successful weather payload is stored as JSON so the
 * app opens with real content (clearly labelled as saved) without a network.
 *
 * A single entry is enough — the app only ever shows one location at a time, so
 * a full Room database would be weight without a purpose.
 */
class WeatherCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    val cached: Flow<CachedWeather?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_WEATHER]?.let { stored ->
            runCatching { json.decodeFromString(CachedWeather.serializer(), stored) }.getOrNull()
        }
    }

    suspend fun read(): CachedWeather? = cached.first()

    suspend fun write(weather: CachedWeather) {
        val encoded = runCatching { json.encodeToString(CachedWeather.serializer(), weather) }.getOrNull() ?: return
        dataStore.edit { it[KEY_LAST_WEATHER] = encoded }
    }

    private companion object {
        val KEY_LAST_WEATHER = stringPreferencesKey("last_weather_json")
    }
}
