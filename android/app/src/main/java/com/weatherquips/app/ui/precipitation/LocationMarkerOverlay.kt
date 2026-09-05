package com.weatherquips.app.ui.precipitation

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * The selected location: a solid dot inside a faint ring, matching the two
 * `CircleMarker`s the web map drew. Drawn directly rather than as a Marker so
 * there is no drawable to allocate per frame.
 */
class LocationMarkerOverlay(
    private val point: GeoPoint,
    @ColorInt private val color: Int,
    @ColorInt private val haloColor: Int,
) : Overlay() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = this@LocationMarkerOverlay.color
        alpha = 230
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = haloColor
        alpha = 80
    }

    private val screenPoint = android.graphics.Point()

    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        if (shadow || mapView == null) return
        mapView.projection.toPixels(point, screenPoint)
        val density = mapView.resources.displayMetrics.density
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 8f * density, fillPaint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 14f * density, ringPaint)
    }
}
