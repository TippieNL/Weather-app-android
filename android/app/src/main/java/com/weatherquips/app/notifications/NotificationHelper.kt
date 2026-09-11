package com.weatherquips.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weatherquips.app.MainActivity
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.Coordinates

/**
 * Android notifications replacing the web app's Service Worker + Web Push.
 * Everything funnels through here so the channel is guaranteed to exist and the
 * POST_NOTIFICATIONS runtime permission is always checked first.
 */
class NotificationHelper(private val context: Context) {

    fun ensureChannel() {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_PRECIPITATION) != null) return

        val channel = NotificationChannel(
            CHANNEL_PRECIPITATION,
            context.getString(R.string.notification_channel_precipitation),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_precipitation_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * @param coordinates the place the alert is about, so the radar action can
     *                    open on it. Omitted, the action is left off.
     * @return false when the notification could not be posted (no permission).
     */
    fun notifyPrecipitation(alert: PrecipitationAlert, coordinates: Coordinates? = null): Boolean {
        if (!hasPermission()) return false
        ensureChannel()

        return try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_PRECIPITATION, buildNotification(alert, coordinates))
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildNotification(alert: PrecipitationAlert, coordinates: Coordinates?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PRECIPITATION)
            .setSmallIcon(smallIconFor(alert.kind))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(alert.headline)
            // The collapsed line carries the figures, so the alert is useful
            // without being expanded and even if the joke lands badly.
            .setContentText(alert.summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(alert.detail)
                    .setBigContentTitle(alert.headline)
                    .setSummaryText(alert.location),
            )
            .setSubText(alert.location)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(destination = null, coordinates = null))

        if (coordinates != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_map,
                    context.getString(R.string.notification_action_radar),
                    openApp(destination = DESTINATION_RADAR, coordinates = coordinates),
                ).build(),
            )
        }

        return builder.build()
    }

    /**
     * Reopens the app, optionally on a specific screen. MainActivity is
     * singleTop, so this never stacks a second copy; a distinct request code
     * per destination keeps the two PendingIntents from overwriting each other.
     */
    private fun openApp(destination: String?, coordinates: Coordinates?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (destination != null) putExtra(EXTRA_DESTINATION, destination)
            if (coordinates != null) {
                putExtra(EXTRA_LATITUDE, coordinates.latitude)
                putExtra(EXTRA_LONGITUDE, coordinates.longitude)
            }
        }
        return PendingIntent.getActivity(
            context,
            if (destination == null) REQUEST_OPEN_APP else REQUEST_OPEN_RADAR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun smallIconFor(kind: PrecipitationKind): Int = when (kind) {
        PrecipitationKind.RAIN -> R.drawable.ic_notification_precipitation
        PrecipitationKind.SNOW -> R.drawable.ic_notification_snow
        PrecipitationKind.STORM -> R.drawable.ic_notification_storm
    }

    companion object {
        const val CHANNEL_PRECIPITATION = "precipitation_alerts"
        const val NOTIFICATION_ID_PRECIPITATION = 1001

        /** Intent extras MainActivity reads to open a screen from a tap. */
        const val EXTRA_DESTINATION = "com.weatherquips.app.extra.DESTINATION"
        const val EXTRA_LATITUDE = "com.weatherquips.app.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "com.weatherquips.app.extra.LONGITUDE"
        const val DESTINATION_RADAR = "radar"

        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_OPEN_RADAR = 1
    }
}
