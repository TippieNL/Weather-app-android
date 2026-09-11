package com.weatherquips.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.weatherquips.app.data.api.NetworkModule
import com.weatherquips.app.data.local.SettingsRepositoryImpl
import com.weatherquips.app.data.local.WeatherCache
import com.weatherquips.app.data.repository.GeocodingRepositoryImpl
import com.weatherquips.app.data.repository.OpenMeteoProvider
import com.weatherquips.app.data.repository.OpenWeatherMapProvider
import com.weatherquips.app.data.repository.RadarRepositoryImpl
import com.weatherquips.app.data.repository.WeatherApiProvider
import com.weatherquips.app.data.repository.WeatherRepositoryImpl
import com.weatherquips.app.domain.repository.GeocodingRepository
import com.weatherquips.app.domain.repository.RadarRepository
import com.weatherquips.app.domain.repository.SettingsRepository
import com.weatherquips.app.domain.repository.WeatherRepository
import com.weatherquips.app.location.LocationProvider
import com.weatherquips.app.notifications.AlertThrottle
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationScheduler
import com.weatherquips.app.widget.PrecipitationWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container.
 *
 * The graph is small and entirely singleton-scoped, so a DI framework would add
 * build time and indirection without buying anything here. Everything is
 * created lazily, so nothing but DataStore touches disk at startup.
 */
class AppContainer(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = applicationScope) {
            context.preferencesDataStoreFile(SETTINGS_STORE)
        }
    }

    private val cacheDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = applicationScope) {
            context.preferencesDataStoreFile(CACHE_STORE)
        }
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(settingsDataStore)
    }

    val geocodingRepository: GeocodingRepository by lazy {
        GeocodingRepositoryImpl(NetworkModule.nominatimApi)
    }

    private val weatherCache: WeatherCache by lazy {
        WeatherCache(cacheDataStore, NetworkModule.json)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepositoryImpl(
            providers = listOf(
                OpenMeteoProvider(NetworkModule.openMeteoApi),
                OpenWeatherMapProvider(NetworkModule.openWeatherMapApi),
                WeatherApiProvider(NetworkModule.weatherApiApi),
            ),
            geocodingRepository = geocodingRepository,
            cache = weatherCache,
            onCacheUpdated = {
                // Best effort: a widget that fails to redraw must never break a
                // weather fetch, and there may be no widget placed at all.
                runCatching { PrecipitationWidget().updateAll(context) }
            },
        )
    }

    val radarRepository: RadarRepository by lazy {
        RadarRepositoryImpl(NetworkModule.rainViewerApi)
    }

    val locationProvider: LocationProvider by lazy { LocationProvider(context) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(context) }

    val precipitationScheduler: PrecipitationScheduler by lazy { PrecipitationScheduler(context) }

    val alertThrottle: AlertThrottle by lazy { AlertThrottle(cacheDataStore) }

    private companion object {
        const val SETTINGS_STORE = "weather_quips_settings"
        const val CACHE_STORE = "weather_quips_cache"
    }
}
