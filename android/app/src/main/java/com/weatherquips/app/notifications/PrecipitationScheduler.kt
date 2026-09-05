package com.weatherquips.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Starts and stops the background precipitation check with the settings toggle. */
class PrecipitationScheduler(private val context: Context) {

    /**
     * @return true when the schedule was actually changed. Scheduling is best
     *         effort: WorkManager may be unavailable (a host without its
     *         initializer, for instance) and that must never crash the app —
     *         the user simply gets no background checks.
     */
    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PrecipitationWorker.WORK_NAME)
            return@runCatching true
        }

        val request = PeriodicWorkRequestBuilder<PrecipitationWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PrecipitationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        true
    }.getOrDefault(false)

    private companion object {
        const val REPEAT_INTERVAL_HOURS = 2L
    }
}
