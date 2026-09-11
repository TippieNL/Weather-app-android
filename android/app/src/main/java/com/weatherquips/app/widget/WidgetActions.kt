package com.weatherquips.app.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.weatherquips.app.MainActivity
import com.weatherquips.app.notifications.NotificationHelper

/**
 * Tapping the widget opens the radar on the place it is reporting — it is a
 * precipitation widget, and the radar is the precipitation screen. Without a
 * cached location there is nothing to centre on, so it just opens the app.
 */
@Composable
internal fun openRadarAction(outlook: PrecipitationOutlook?): Action {
    val context = androidx.glance.LocalContext.current
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val coordinates = outlook?.coordinates
        if (coordinates != null) {
            putExtra(NotificationHelper.EXTRA_DESTINATION, NotificationHelper.DESTINATION_RADAR)
            putExtra(NotificationHelper.EXTRA_LATITUDE, coordinates.latitude)
            putExtra(NotificationHelper.EXTRA_LONGITUDE, coordinates.longitude)
        }
    }
    return actionStartActivity(intent)
}
