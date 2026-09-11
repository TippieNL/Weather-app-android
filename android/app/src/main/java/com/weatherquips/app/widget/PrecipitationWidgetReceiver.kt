package com.weatherquips.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Hosts the widget and keeps its refresh schedule tied to its existence: work
 * starts when the first widget is placed and stops when the last one goes, so
 * a user who never adds one pays nothing.
 */
class PrecipitationWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PrecipitationWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler(context).setEnabled(true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler(context).setEnabled(false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // A widget restored from a backup arrives without the schedule that
        // created it, so make sure one exists whenever the host asks for an
        // update.
        if (intent.action == android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            WidgetRefreshScheduler(context).setEnabled(true)
        }
    }
}
