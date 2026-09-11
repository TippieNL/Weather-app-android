package com.weatherquips.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Colours for the graph.
 *
 * One palette for both themes rather than two. A Glance composable cannot read
 * the resolved theme colour without a context, and every colour here has to
 * survive being drawn on either a near-white or a near-black widget: the blue
 * does, and the greys are picked to sit between the two backgrounds instead of
 * matching one of them.
 */
data class GraphPalette(
    val line: Int,
    val fillTop: Int,
    val fillBottom: Int,
    val grid: Int,
    val bandLabel: Int,
    val axisLabel: Int,
    val nowLine: Int,
)

/**
 * The widget's precipitation graph, drawn to a bitmap.
 *
 * Glance renders through RemoteViews, which has no canvas and no path support,
 * so anything beyond boxes and text has to arrive as an image. Drawing it here
 * rather than assembling it out of Glance boxes also means the whole thing is
 * one testable function that produces a PNG you can look at.
 *
 * The vertical axis is [IntensityScale], not millimetres: equal bands for
 * light, moderate, heavy and violent, so drizzle is still a visible shape and
 * a cloudburst does not flatten everything below it.
 */
object PrecipitationGraph {

    /**
     * Pixels per dp. Fixed rather than read from the device so the bitmap is
     * identical in tests and on a phone; Glance scales it to fit, and the
     * aspect ratio always matches, so nothing is distorted.
     */
    const val RENDER_SCALE = 2.5f

    /**
     * Ceiling on the bitmap, so a widget the user has stretched across the
     * whole home screen cannot push the RemoteViews payload somewhere the
     * launcher refuses it. Scaled down whole, so nothing is cropped and the
     * aspect ratio still matches the space it is drawn into.
     */
    private const val MAX_PIXELS = 400_000

    /** Room above the curve for the "now" label. */
    private const val TOP_PADDING_DP = 8f

    /** Strip along the bottom for the clock labels. */
    private const val AXIS_HEIGHT_DP = 11f

    private const val LINE_WIDTH_DP = 1.6f
    private const val GRID_WIDTH_DP = 0.8f
    private const val BAND_TEXT_DP = 7f
    private const val AXIS_TEXT_DP = 8f
    private const val LABEL_INSET_DP = 1f

    /**
     * Below this much history the left edge already *is* now, and a marker on
     * top of it only collides with the axis.
     */
    private const val NOW_MARKER_MIN_HISTORY_MINUTES = 5

    /** Clear space demanded between two clock labels before both are drawn. */
    private const val LABEL_GAP_DP = 6f

    fun render(
        chart: PrecipitationChart,
        widthDp: Float,
        heightDp: Float,
        palette: GraphPalette,
    ): Bitmap? {
        if (chart.isEmpty || widthDp <= 0f || heightDp <= 0f) return null

        val requested = widthDp * heightDp * RENDER_SCALE * RENDER_SCALE
        val scale = if (requested > MAX_PIXELS) {
            RENDER_SCALE * sqrt(MAX_PIXELS / requested)
        } else {
            RENDER_SCALE
        }
        return Renderer(scale, palette).draw(
            chart = chart,
            width = (widthDp * scale).roundToInt().coerceAtLeast(1),
            height = (heightDp * scale).roundToInt().coerceAtLeast(1),
        )
    }

    /**
     * The drawing itself, tied to one pixel scale.
     *
     * Every dimension below is in dp and converted through [px], so a bitmap
     * that had to be scaled down to fit the payload budget keeps its
     * proportions instead of ending up with oversized type.
     */
    private class Renderer(private val scale: Float, private val palette: GraphPalette) {

        fun draw(chart: PrecipitationChart, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val top = TOP_PADDING_DP.px
            val baseline = height - AXIS_HEIGHT_DP.px
            if (baseline <= top) return bitmap

            val span = max(1, chart.endMinutes - chart.startMinutes)
            val x = { minutes: Int -> (minutes - chart.startMinutes).toFloat() / span * width }
            val y = { fraction: Float ->
                baseline - fraction.coerceIn(0f, 1f) * (baseline - top)
            }

            drawGrid(canvas, width.toFloat(), y)
            drawArea(canvas, chart, top, baseline, x, y)
            drawBandLabels(canvas, y)
            drawNowLine(canvas, chart, top, baseline, x)
            drawTimeLabels(canvas, chart, width.toFloat(), height.toFloat())

            return bitmap
        }

        private fun drawGrid(canvas: Canvas, width: Float, y: (Float) -> Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.grid
                strokeWidth = GRID_WIDTH_DP.px
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(3f.px, 3f.px), 0f)
            }
            IntensityScale.gridBands.forEach { band ->
                val lineY = y(IntensityScale.fractionOf(band))
                canvas.drawLine(0f, lineY, width, lineY, paint)
            }

            // The ground line is solid: it is the zero, not a threshold.
            canvas.drawLine(
                0f,
                y(0f),
                width,
                y(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.grid
                    strokeWidth = GRID_WIDTH_DP.px
                    style = Paint.Style.STROKE
                },
            )
        }

        private fun drawArea(
            canvas: Canvas,
            chart: PrecipitationChart,
            top: Float,
            baseline: Float,
            x: (Int) -> Float,
            y: (Float) -> Float,
        ) {
            val curve = curveThrough(chart, x, y)

            val fill = Path(curve).apply {
                lineTo(x(chart.endMinutes), baseline)
                lineTo(x(chart.startMinutes), baseline)
                close()
            }
            canvas.drawPath(
                fill,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = LinearGradient(
                        0f, top, 0f, baseline,
                        palette.fillTop, palette.fillBottom,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawPath(
                curve,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.line
                    style = Paint.Style.STROKE
                    strokeWidth = LINE_WIDTH_DP.px
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                },
            )
        }

        /**
         * A quadratic through the midpoints of the samples.
         *
         * Smoother than joining the dots, and — unlike a spline through the
         * points themselves — it cannot overshoot below zero and paint rain
         * into a dry quarter of an hour.
         */
        private fun curveThrough(
            chart: PrecipitationChart,
            x: (Int) -> Float,
            y: (Float) -> Float,
        ): Path {
            val points = chart.points.map {
                x(it.minutesFromNow) to y(IntensityScale.fraction(it.millimetresPerHour))
            }
            return Path().apply {
                moveTo(points.first().first, points.first().second)
                for (i in 1 until points.size) {
                    val (px, py) = points[i - 1]
                    val (cx, cy) = points[i]
                    quadTo(px, py, (px + cx) / 2f, (py + cy) / 2f)
                }
                lineTo(points.last().first, points.last().second)
            }
        }

        private fun drawBandLabels(canvas: Canvas, y: (Float) -> Float) {
            val paint = textPaint(palette.bandLabel, BAND_TEXT_DP.px)
            IntensityScale.gridBands.forEach { band ->
                canvas.drawText(
                    band.label,
                    LABEL_INSET_DP.px,
                    y(IntensityScale.fractionOf(band)) - 1.5f.px,
                    paint,
                )
            }
        }

        private fun drawNowLine(
            canvas: Canvas,
            chart: PrecipitationChart,
            top: Float,
            baseline: Float,
            x: (Int) -> Float,
        ) {
            if (chart.endMinutes < 0) return
            if (chart.startMinutes > -NOW_MARKER_MIN_HISTORY_MINUTES) return

            val nowX = x(0)
            canvas.drawLine(
                nowX, top, nowX, baseline,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.nowLine
                    strokeWidth = LINE_WIDTH_DP.px
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(3f.px, 2.5f.px), 0f)
                },
            )

            val paint = textPaint(palette.nowLine, BAND_TEXT_DP.px, bold = true)
            val label = "now"
            val labelWidth = paint.measureText(label)
            // Flip the label left of the line when it would run off the edge.
            val labelX = if (nowX + 2f.px + labelWidth > canvas.width) {
                nowX - 2f.px - labelWidth
            } else {
                nowX + 2f.px
            }
            canvas.drawText(label, labelX, top - 1f.px, paint)
        }

        /**
         * Clock labels, as many as fit.
         *
         * Whole hours are placed first and half hours only fill what is left,
         * so a narrow widget loses the 18:30s rather than ending up with a
         * ragged mix of both.
         */
        private fun drawTimeLabels(
            canvas: Canvas,
            chart: PrecipitationChart,
            width: Float,
            height: Float,
        ) {
            if (chart.ticks.isEmpty()) return
            val paint = textPaint(palette.axisLabel, AXIS_TEXT_DP.px)
            val span = max(1, chart.endMinutes - chart.startMinutes)
            val gap = LABEL_GAP_DP.px
            val placed = mutableListOf<Pair<Float, Float>>()

            chart.ticks
                .sortedWith(compareBy({ !it.isMajor }, { it.minutesFromNow }))
                .forEach { tick ->
                    val centre = (tick.minutesFromNow - chart.startMinutes).toFloat() / span * width
                    val labelWidth = paint.measureText(tick.label)
                    val left = (centre - labelWidth / 2f)
                        .coerceIn(0f, (width - labelWidth).coerceAtLeast(0f))
                    val right = left + labelWidth
                    if (placed.any { left < it.second + gap && right + gap > it.first }) {
                        return@forEach
                    }
                    canvas.drawText(tick.label, left, height - 2f.px, paint)
                    placed += left to right
                }
        }

        private fun textPaint(colour: Int, size: Float, bold: Boolean = false) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colour
                textSize = size
                typeface = Typeface.create(
                    if (bold) "sans-serif-medium" else "sans-serif",
                    Typeface.NORMAL,
                )
            }

        private val Float.px: Float get() = this * scale
    }
}
