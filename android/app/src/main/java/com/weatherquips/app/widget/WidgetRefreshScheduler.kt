package com.weatherquips.app.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Starts and stops the widget's periodic refresh. */
class WidgetRefreshScheduler(private val context: Context) {

    /** @return true when the schedule was changed; scheduling is best effort. */
    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WidgetRefreshWorker.WORK_NAME)
            return@runCatching true
        }

        workManager.enqueueUniquePeriodicWork(
            WidgetRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build(),
        )
        // The periodic schedule does not fire immediately, and a widget that
        // sits blank until the first interval elapses looks broken.
        workManager.enqueue(OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
        true
    }.getOrDefault(false)

    private companion object {
        /**
         * WorkManager's floor, and the right end of it for this widget: the
         * graph covers two hours at five-minute resolution, so refreshing
         * hourly would leave the "now" line up to an hour behind and miss a
         * shower that arrived since.
         */
        const val REFRESH_INTERVAL_MINUTES = 15L
    }
}
