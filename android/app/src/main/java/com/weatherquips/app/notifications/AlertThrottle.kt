package com.weatherquips.app.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * At most one precipitation alert per 30 minutes — the same window the web app
 * enforced with `sessionStorage`, but persisted so it survives process death.
 */
class AlertThrottle(
    private val dataStore: DataStore<Preferences>,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun shouldSend(): Boolean {
        val last = dataStore.data.first()[KEY_LAST_ALERT] ?: return true
        return now() - last > windowMillis
    }

    suspend fun markSent() {
        dataStore.edit { it[KEY_LAST_ALERT] = now() }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 30 * 60 * 1000L
        val KEY_LAST_ALERT = longPreferencesKey("last_precipitation_alert")
    }
}
