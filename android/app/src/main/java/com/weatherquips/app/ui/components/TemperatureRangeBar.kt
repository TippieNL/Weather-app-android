package com.weatherquips.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.weatherquips.app.ui.theme.temperatureColorFor

/**
 * A day's low→high span drawn on a shared scale.
 *
 * Drawn on a Canvas rather than assembled from weighted spacers: the fractions
 * are exact (no minimum-weight fudging to keep a thin bar visible), the ends
 * stay properly rounded, and an optional marker can sit on top to show where
 * the current temperature falls inside the span.
 */
@Composable
fun TemperatureRangeBar(
    minCelsius: Double,
    maxCelsius: Double,
    scaleMinCelsius: Double,
    scaleMaxCelsius: Double,
    modifier: Modifier = Modifier,
    markerCelsius: Double? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    markerOutline: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val span = (scaleMaxCelsius - scaleMinCelsius).takeIf { it > 0.0 } ?: 1.0
    fun fraction(value: Double) = ((value - scaleMinCelsius) / span).coerceIn(0.0, 1.0).toFloat()

    val startFraction = fraction(minCelsius)
    val endFraction = fraction(maxCelsius)
    val startColor = temperatureColorFor(minCelsius)
    val endColor = temperatureColorFor(maxCelsius)
    val markerFraction = markerCelsius?.let { fraction(it) }

    Canvas(modifier = modifier.fillMaxWidth().height(BAR_HEIGHT)) {
        val height = size.height
        val radius = CornerRadius(height / 2f, height / 2f)

        drawRoundRect(color = trackColor, size = size, cornerRadius = radius)

        // Always leave the bar at least as wide as it is tall, so a day with no
        // spread still reads as a dot rather than vanishing.
        val rawLeft = startFraction * size.width
        val rawRight = endFraction * size.width
        val left = rawLeft.coerceAtMost(size.width - height)
        val right = rawRight.coerceAtLeast(left + height)

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(startColor, endColor),
                startX = left,
                endX = right,
            ),
            topLeft = Offset(left, 0f),
            size = Size(right - left, height),
            cornerRadius = radius,
        )

        if (markerFraction != null) {
            val markerRadius = height / 2f
            val centerX = (markerFraction * size.width).coerceIn(markerRadius, size.width - markerRadius)
            val center = Offset(centerX, height / 2f)
            drawCircle(color = markerOutline, radius = markerRadius, center = center)
            drawCircle(
                color = temperatureColorFor(markerCelsius),
                radius = markerRadius - MARKER_RING_WIDTH.toPx(),
                center = center,
            )
        }
    }
}

private val BAR_HEIGHT = 10.dp
private val MARKER_RING_WIDTH = 2.dp
