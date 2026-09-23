package com.weatherquips.app

import android.app.Application
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

        // Re-arm the periodic precipitation check if the user has it switched on.
        // WorkManager's KEEP policy makes this a no-op when it is already queued.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = container.settingsRepository.current()
            if (settings.notificationsEnabled) {
                container.precipitationScheduler.setEnabled(true)
            }

            // Same for the widget. Its schedule was only ever armed when a
            // widget was placed, so anything that clears WorkManager's queue —
            // a force stop, an OEM battery manager — left it off for good.
            val widgets = WidgetRefreshScheduler(this@WeatherQuipsApplication)
            if (widgets.hasWidgets()) widgets.setEnabled(true)
        }
    }
}
