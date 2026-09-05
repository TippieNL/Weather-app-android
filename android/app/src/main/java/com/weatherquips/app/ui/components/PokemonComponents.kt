package com.weatherquips.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.ui.theme.LocalAccents

/**
 * Poké Ball drawn with Compose Canvas, the same geometry as the inline SVG in
 * `pokeball-icon.tsx` — crisp at any size and no bitmap assets to load.
 */
private fun DrawScope.drawPokeball(side: Float, ink: Color, cardColor: Color, red: Color) {
    val scale = side / 100f
    fun s(value: Float) = value * scale
    val center = Offset(s(50f), s(50f))
    val radius = s(48f)

    // Bottom (card-coloured) half, then the red top half.
    drawArc(
        color = cardColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
    )
    drawArc(
        color = red,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
    )
    drawCircle(color = ink, radius = radius, center = center, style = Stroke(width = s(4f)))
    drawRect(
        color = ink,
        topLeft = Offset(s(2f), s(46f)),
        size = Size(s(96f), s(8f)),
    )
    drawCircle(color = ink, radius = s(16f), center = center)
    drawCircle(color = cardColor, radius = s(11f), center = center)
    drawCircle(color = ink, radius = s(11f), center = center, style = Stroke(width = s(2.5f)))
    drawCircle(color = cardColor, radius = s(5f), center = center)
    drawCircle(color = ink, radius = s(5f), center = center, style = Stroke(width = s(2.5f)))
}

/** Hero Poké Ball: gentle float, slow spin, twinkling sparkles. */
@Composable
fun PokeballIcon(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String? = "Poké Ball",
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "pokeball")
    val ink = MaterialTheme.colorScheme.onBackground
    val card = MaterialTheme.colorScheme.surfaceContainer
    val accents = LocalAccents.current

    val float by if (animationsEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -10f,
            animationSpec = infiniteRepeatable(
                tween(1_600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "float",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val spin by if (animationsEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
            label = "spin",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val sparkle by if (animationsEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "sparkle",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.9f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = float }
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size).rotate(spin)) {
            drawPokeball(this.size.minDimension, ink, card, accents.pokemonRed)
        }
        Canvas(modifier = Modifier.size(size)) {
            val side = this.size.minDimension
            SPARKLES.forEach { sparkleSpec ->
                val alpha = ((sparkle + sparkleSpec.phase) % 1f).let { phase ->
                    if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
                }
                drawSparkle(
                    center = Offset(side * sparkleSpec.x, side * sparkleSpec.y),
                    radius = side * 0.05f * sparkleSpec.scale,
                    color = accents.pokemonGold.copy(alpha = alpha.coerceIn(0f, 1f)),
                )
            }
        }
    }
}

/** Small static Poké Ball for inline placements. */
@Composable
fun PokeballGlyph(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val ink = MaterialTheme.colorScheme.onBackground
    val card = MaterialTheme.colorScheme.surfaceContainer
    val red = LocalAccents.current.pokemonRed
    Canvas(modifier = modifier.size(size)) {
        drawPokeball(this.size.minDimension, ink, card, red)
    }
}

private data class SparkleSpec(val x: Float, val y: Float, val scale: Float, val phase: Float)

private val SPARKLES = listOf(
    SparkleSpec(0.12f, 0.02f, 1f, 0f),
    SparkleSpec(0.86f, 0.10f, 0.7f, 0.33f),
    SparkleSpec(0.02f, 0.70f, 0.85f, 0.61f),
    SparkleSpec(0.78f, 0.82f, 0.6f, 0.17f),
    SparkleSpec(0.94f, 0.44f, 0.75f, 0.5f),
)

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.28f, center.y - radius * 0.28f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * 0.28f, center.y - radius * 0.28f)
        close()
    }
    drawPath(path, color)
}

/**
 * Original "mystery creature" silhouettes — a nod to the classic reveal without
 * copying any real character. Ported shape-for-shape from `pokemon-silhouette.tsx`.
 */
@Composable
fun PokemonSilhouette(
    silhouette: PokemonQuips.Silhouette,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val bob = remember { Animatable(0f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        while (true) {
            bob.animateTo(-6f, tween(1_750, easing = FastOutSlowInEasing))
            bob.animateTo(0f, tween(1_750, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = bob.value }
            .semantics { contentDescription = "Mystery Pokémon silhouette" },
    ) {
        val side = this.size.minDimension
        fun s(value: Float) = value * side / 100f
        fun point(x: Float, y: Float) = Offset(s(x), s(y))

        when (silhouette) {
            PokemonQuips.Silhouette.SPARK -> {
                drawPath(
                    Path().apply {
                        moveTo(s(22f), s(30f)); lineTo(s(30f), s(4f)); lineTo(s(40f), s(28f)); close()
                        moveTo(s(78f), s(30f)); lineTo(s(70f), s(4f)); lineTo(s(60f), s(28f)); close()
                        moveTo(s(84f), s(46f)); lineTo(s(98f), s(40f)); lineTo(s(88f), s(54f))
                        lineTo(s(100f), s(60f)); lineTo(s(80f), s(70f)); close()
                    },
                    color,
                )
                drawCircle(color, radius = s(34f), center = point(50f, 58f))
            }

            PokemonQuips.Silhouette.LEAF -> {
                drawPath(
                    Path().apply {
                        moveTo(s(50f), s(6f))
                        cubicTo(s(60f), s(10f), s(64f), s(20f), s(56f), s(30f))
                        cubicTo(s(46f), s(28f), s(44f), s(16f), s(50f), s(6f))
                        close()
                    },
                    color,
                )
                drawCircle(color, radius = s(32f), center = point(50f, 60f))
            }

            PokemonQuips.Silhouette.FLAME -> {
                drawCircle(color, radius = s(32f), center = point(46f, 60f))
                drawPath(
                    Path().apply {
                        moveTo(s(78f), s(30f))
                        cubicTo(s(88f), s(38f), s(92f), s(52f), s(82f), s(60f))
                        cubicTo(s(80f), s(54f), s(76f), s(52f), s(76f), s(52f))
                        cubicTo(s(78f), s(60f), s(74f), s(64f), s(70f), s(64f))
                        cubicTo(s(74f), s(52f), s(68f), s(42f), s(78f), s(30f))
                        close()
                    },
                    color,
                )
            }

            PokemonQuips.Silhouette.SPLASH -> {
                drawPath(
                    Path().apply {
                        moveTo(s(50f), s(8f))
                        cubicTo(s(58f), s(18f), s(64f), s(26f), s(64f), s(34f))
                        arcTo(
                            rect = Rect(s(36f), s(20f), s(64f), s(48f)),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false,
                        )
                        cubicTo(s(36f), s(26f), s(42f), s(18f), s(50f), s(8f))
                        close()
                    },
                    color,
                )
                drawCircle(color, radius = s(28f), center = point(50f, 64f))
            }
        }
    }
}
