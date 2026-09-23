package com.weatherquips.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
        // The periodic schedule does not fire immediately, and a widget that
        // sits blank until the first interval elapses looks broken.
        refreshNow()
        true
    }.getOrDefault(false)

    /**
     * One catch-up fetch, now.
     *
     * Periodic work is a floor, not a promise: in Doze, or under an OEM
     * battery manager, fifteen minutes becomes hours. So the widget also asks
     * for a refresh whenever it is drawn with stale data, which turns any
     * glance at the home screen into a repair. KEEP means a run of redraws
     * queues one fetch, not twenty.
     */
    fun refreshNow(): Boolean = runCatching {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WidgetRefreshWorker.CATCH_UP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build(),
        )
        true
    }.getOrDefault(false)

    /** Whether any widget is actually on a home screen. */
    fun hasWidgets(): Boolean = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PrecipitationWidgetReceiver::class.java))
            .isNotEmpty()
    }.getOrDefault(false)

    private companion object {
        /**
         * WorkManager's floor, and the right end of it for this widget: the
         * graph covers two hours at five-minute resolution, so refreshing
         * hourly would leave the "now" line up to an hour behind and miss a
         * shower that arrived since.
         */
        const val REFRESH_INTERVAL_MINUTES = 15L

        const val BACKOFF_MINUTES = 5L
    }
}
