package com.weatherquips.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.location.LocationResult

/**
 * Periodic precipitation check.
 *
 * WorkManager is deliberately used instead of a long-lived service: the app only
 * needs an occasional check, the OS batches it with other work, and nothing runs
 * while the user has the feature switched off.
 */
class PrecipitationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WeatherQuipsApplication)?.container
            ?: return Result.success()

        val settings = container.settingsRepository.current()
        if (!settings.notificationsEnabled) return Result.success()
        if (!container.notificationHelper.hasPermission()) return Result.success()

        val coordinates = when (settings.locationMode) {
            LocationMode.MANUAL -> settings.manualCoords
            LocationMode.DEVICE -> when (val result = container.locationProvider.currentLocation()) {
                is LocationResult.Success -> result.coordinates
                else -> null
            }
        } ?: return Result.success()

        val data = try {
            container.weatherRepository.getWeather(
                coordinates = coordinates,
                service = settings.weatherService,
                apiKey = settings.weatherApiKey,
            )
        } catch (_: Throwable) {
            // Transient network trouble: let WorkManager back off and retry.
            return Result.retry()
        }

        if (!PrecipitationAlerts.shouldNotify(data)) return Result.success()
        if (!container.alertThrottle.shouldSend()) return Result.success()

        val posted = container.notificationHelper
            .notifyPrecipitation(PrecipitationAlerts.build(data), coordinates)
        if (posted) container.alertThrottle.markSent()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "precipitation_alerts"
    }
}
