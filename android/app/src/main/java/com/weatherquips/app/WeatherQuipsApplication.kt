package com.weatherquips.app

import android.app.Application
import org.osmdroid.config.Configuration

class WeatherQuipsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannel()

        // osmdroid needs a User-Agent (OSM tile policy) and its own cache dir.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
            // Bound the on-disk tile cache so the map can't grow without limit.
            tileFileSystemCacheMaxBytes = 64L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 48L * 1024 * 1024
        }
    }
}
