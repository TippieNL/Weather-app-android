package com.weatherquips.app.ui.precipitation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.annotation.ColorInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * The two points that matter on the radar: the place the forecast is for, and
 * where the user actually is.
 *
 * They are drawn by one overlay reading mutable fields rather than by adding
 * and removing overlays as fixes arrive — the device position turns up
 * asynchronously, and churning the overlay list while the map is drawing is a
 * good way to get a concurrent-modification crash.
 */
class MapMarkersOverlay(
    private val forecastPoint: GeoPoint,
    @ColorInt private val forecastColor: Int,
    @ColorInt private val userColor: Int,
    @ColorInt private val userOutlineColor: Int,
) : Overlay() {

    /** Device position, or null while unknown or not permitted. */
    var userPoint: GeoPoint? = null

    private val forecastFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = forecastColor
        alpha = 230
    }

    private val forecastRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = forecastColor
        alpha = 80
    }

    private val userFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = userColor
    }

    private val userOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = userOutlineColor
    }

    private val userHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = userColor
        alpha = 40
    }

    private val screenPoint = Point()

    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        if (shadow || mapView == null) return
        val density = mapView.resources.displayMetrics.density

        mapView.projection.toPixels(forecastPoint, screenPoint)
        val forecastX = screenPoint.x.toFloat()
        val forecastY = screenPoint.y.toFloat()
        forecastRing.strokeWidth = 1.5f * density
        canvas.drawCircle(forecastX, forecastY, 8f * density, forecastFill)
        canvas.drawCircle(forecastX, forecastY, 14f * density, forecastRing)

        // A conventional "you are here" dot: coloured centre, white collar and
        // a soft halo, so it never reads as another forecast pin.
        userPoint?.let { point ->
            mapView.projection.toPixels(point, screenPoint)
            val x = screenPoint.x.toFloat()
            val y = screenPoint.y.toFloat()
            canvas.drawCircle(x, y, 16f * density, userHalo)
            canvas.drawCircle(x, y, 8f * density, userOutline)
            canvas.drawCircle(x, y, 6f * density, userFill)
        }
    }
}
