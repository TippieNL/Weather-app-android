package com.weatherquips.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.location.LocationResult

/**
 * Refreshes the cached forecast and redraws the widget.
 *
 * A failed fetch is not an error worth showing: the widget keeps rendering the
 * last cache, which is the whole point of having one.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WeatherQuipsApplication)?.container
            ?: return Result.success()

        val settings = container.settingsRepository.current()
        val coordinates = when (settings.locationMode) {
            LocationMode.MANUAL -> settings.manualCoords
            LocationMode.DEVICE -> when (val result = container.locationProvider.currentLocation()) {
                is LocationResult.Success -> result.coordinates
                else -> null
            }
        }

        if (coordinates != null) {
            runCatching {
                container.weatherRepository.getWeather(
                    coordinates = coordinates,
                    service = settings.weatherService,
                    apiKey = settings.weatherApiKey,
                )
            }
        }

        // Redraw either way: a new fetch, or the cache that is already there.
        runCatching { PrecipitationWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_refresh"
    }
}
