package com.weatherquips.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weatherquips.app.R
import com.weatherquips.app.ui.theme.LocalAccents

/** The app's card shell: flat, hairline border, generous radius. */
@Composable
fun QuipCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val border = CardDefaults.outlinedCardBorder().copy(
        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant),
    )
    if (onClick == null) {
        Card(modifier = modifier, colors = colors, border = border, shape = CardShape) {
            content()
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            border = border,
            shape = CardShape,
        ) { content() }
    }
}

private val CardShape = RoundedCornerShape(14.dp)

/** Refresh control that spins while a request is in flight. */
@Composable
fun RefreshButton(
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    // The transition is only created while refreshing, so an idle screen runs no
    // animation at all.
    val rotation = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "refresh")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "refresh-rotation",
        )
        animated
    } else {
        0f
    }

    IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = stringResource(R.string.refresh),
            modifier = Modifier
                .size(iconSize)
                .rotate(rotation),
        )
    }
}

/**
 * Cold → hot gradient bar. [startFraction]/[endFraction] place the coloured
 * span inside the track, which is how the weekly forecast draws each day's
 * min→max range.
 */
@Composable
fun TemperatureGradientBar(
    modifier: Modifier = Modifier,
    startFraction: Float = 0f,
    endFraction: Float = 1f,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val accents = LocalAccents.current
    val start = startFraction.coerceIn(0f, 1f)
    val end = endFraction.coerceIn(start, 1f)

    Row(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor),
    ) {
        // Weights can't be zero, so unused space collapses to a hairline instead.
        Spacer(Modifier.weight(start.coerceAtLeast(MIN_WEIGHT)))
        Box(
            modifier = Modifier
                .weight((end - start).coerceAtLeast(MIN_BAR_WEIGHT))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(accents.cold, accents.hot))),
        )
        Spacer(Modifier.weight((1f - end).coerceAtLeast(MIN_WEIGHT)))
    }
}

private const val MIN_WEIGHT = 0.0001f
private const val MIN_BAR_WEIGHT = 0.04f

/** Label + value row used throughout the stats grid. */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
