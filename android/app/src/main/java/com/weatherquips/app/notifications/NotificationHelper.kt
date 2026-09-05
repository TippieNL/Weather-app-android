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

    /** @return false when the notification could not be posted (no permission). */
    fun notifyPrecipitation(alert: PrecipitationAlert): Boolean {
        if (!hasPermission()) return false
        ensureChannel()

        return try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_PRECIPITATION, buildNotification(alert))
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildNotification(alert: PrecipitationAlert): Notification {
        // Tapping the alert reopens the app on the home screen (singleTop, so no
        // duplicate activity stacks up).
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_PRECIPITATION)
            .setSmallIcon(R.drawable.ic_notification_precipitation)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_PRECIPITATION = "precipitation_alerts"
        const val NOTIFICATION_ID_PRECIPITATION = 1001
    }
}
