package com.weatherquips.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.ui.theme.LocalAccents

/**
 * Poké Ball drawn with Compose Canvas, so it stays crisp at any size and needs
 * no bitmap assets.
 *
 * Everything is laid out on a 100×100 grid and scaled, which keeps the
 * proportions fixed however large it is drawn.
 */
private fun DrawScope.drawPokeball(
    side: Float,
    ink: Color,
    shellColor: Color,
    red: Color,
    tiltDegrees: Float,
) {
    val scale = side / 100f
    fun s(value: Float) = value * scale
    val center = Offset(s(50f), s(50f))
    val radius = s(41f)
    val outline = s(4.5f)

    // The band is part of the ball, so the whole thing tilts together. A full
    // spin was worse than no motion at all: the band ends up vertical and it
    // stops reading as a Poké Ball.
    rotate(degrees = tiltDegrees, pivot = center) {
        drawCircle(color = shellColor, radius = radius, center = center)

        // Top half in red, clipped to the shell by the arc itself.
        drawArc(
            color = red,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
        )

        // A soft gloss across the upper left, the way a moulded shell catches light.
        drawArc(
            color = Color.White.copy(alpha = 0.22f),
            startAngle = 200f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
            size = Size(radius * 1.44f, radius * 1.44f),
            style = Stroke(width = s(7f), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )

        // Equator band, drawn as a rectangle clipped by the shell outline.
        drawRect(
            color = ink,
            topLeft = Offset(center.x - radius, center.y - s(5f)),
            size = Size(radius * 2, s(10f)),
        )

        drawCircle(color = ink, radius = radius, center = center, style = Stroke(width = outline))

        // Button: dark collar, pale face, dark ring, pale core.
        drawCircle(color = ink, radius = s(17f), center = center)
        drawCircle(color = shellColor, radius = s(12.5f), center = center)
        drawCircle(color = ink, radius = s(12.5f), center = center, style = Stroke(width = s(2.5f)))
        drawCircle(color = shellColor, radius = s(6f), center = center)
        drawCircle(color = ink, radius = s(6f), center = center, style = Stroke(width = s(2.5f)))
    }
}

/** Hero Poké Ball: a gentle float, a slow wobble, and sparkles around it. */
@Composable
fun PokeballIcon(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String? = "Poké Ball",
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "pokeball")
    val ink = MaterialTheme.colorScheme.onBackground
    val shell = MaterialTheme.colorScheme.surfaceContainer
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
        remember { mutableFloatStateOf(0f) }
    }

    val tilt by if (animationsEnabled) {
        transition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                tween(2_200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "tilt",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
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
        remember { mutableFloatStateOf(0.9f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = float }
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawPokeball(this.size.minDimension, ink, shell, accents.pokemonRed, tilt)
            val side = this.size.minDimension
            SPARKLES.forEach { spec ->
                val phase = (sparkle + spec.phase) % 1f
                val alpha = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
                drawSparkle(
                    center = Offset(side * spec.x, side * spec.y),
                    radius = side * 0.055f * spec.scale,
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
    val shell = MaterialTheme.colorScheme.surfaceContainer
    val red = LocalAccents.current.pokemonRed
    Canvas(modifier = modifier.size(size)) {
        drawPokeball(this.size.minDimension, ink, shell, red, tiltDegrees = 0f)
    }
}

private data class SparkleSpec(val x: Float, val y: Float, val scale: Float, val phase: Float)

// Kept outside the shell, which spans 0.09–0.91 of the canvas.
private val SPARKLES = listOf(
    SparkleSpec(0.06f, 0.10f, 1f, 0f),
    SparkleSpec(0.92f, 0.14f, 0.7f, 0.33f),
    SparkleSpec(0.04f, 0.82f, 0.85f, 0.61f),
    SparkleSpec(0.90f, 0.88f, 0.6f, 0.17f),
    SparkleSpec(0.97f, 0.50f, 0.75f, 0.5f),
)

/** Four-pointed twinkle. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.22f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
        quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/**
 * Original "mystery creature" silhouettes — a nod to the classic reveal without
 * copying any real character.
 *
 * They share one body so the set reads as a family: a round head over an oval
 * body with two feet, and a single distinguishing feature each. The earlier
 * versions were a bare circle with two spikes, which read as a smudge rather
 * than a creature.
 */
@Composable
fun PokemonSilhouette(
    silhouette: PokemonQuips.Silhouette,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
) {
    val animationsEnabled = rememberAnimationsEnabled()
    // An infinite transition rather than a `while (true)` animate loop: the
    // loop is a coroutine that never completes, which leaves Compose
    // permanently non-idle and hangs anything that waits for it.
    val transition = rememberInfiniteTransition(label = "silhouette")
    val bob by if (animationsEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                tween(1_750, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "bob",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = bob }
            .semantics { contentDescription = "Mystery Pokémon silhouette" },
    ) {
        drawCreature(silhouette, this.size.minDimension, color)
    }
}

private fun DrawScope.drawCreature(
    silhouette: PokemonQuips.Silhouette,
    side: Float,
    color: Color,
) {
    fun s(value: Float) = value * side / 100f

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Path().apply {
        addOval(Rect(s(cx - rx), s(cy - ry), s(cx + rx), s(cy + ry)))
    }

    fun polygon(vararg points: Float) = Path().apply {
        moveTo(s(points[0]), s(points[1]))
        for (i in 2 until points.size step 2) lineTo(s(points[i]), s(points[i + 1]))
        close()
    }

    val feature: List<Path> = when (silhouette) {
        PokemonQuips.Silhouette.SPARK -> listOf(
            // Lightning-bolt tail.
            polygon(70f, 62f, 92f, 42f, 82f, 58f, 99f, 56f, 74f, 80f, 82f, 64f),
            polygon(30f, 32f, 34f, 4f, 47f, 28f),
            polygon(70f, 32f, 66f, 4f, 53f, 28f),
        )

        PokemonQuips.Silhouette.LEAF -> listOf(
            Path().apply {
                moveTo(s(50f), s(32f))
                cubicTo(s(44f), s(14f), s(28f), s(10f), s(21f), s(15f))
                cubicTo(s(26f), s(30f), s(41f), s(34f), s(50f), s(32f))
                close()
            },
            Path().apply {
                moveTo(s(50f), s(30f))
                cubicTo(s(56f), s(10f), s(71f), s(5f), s(79f), s(10f))
                cubicTo(s(75f), s(27f), s(60f), s(33f), s(50f), s(30f))
                close()
            },
            oval(50f, 30f, 5f, 8f),
        )

        PokemonQuips.Silhouette.FLAME -> listOf(
            Path().apply {
                moveTo(s(72f), s(72f))
                cubicTo(s(97f), s(62f), s(95f), s(34f), s(80f), s(20f))
                cubicTo(s(85f), s(38f), s(74f), s(41f), s(76f), s(27f))
                cubicTo(s(60f), s(41f), s(64f), s(60f), s(72f), s(72f))
                close()
            },
            polygon(32f, 30f, 29f, 6f, 45f, 24f),
            polygon(68f, 30f, 71f, 6f, 55f, 24f),
        )

        PokemonQuips.Silhouette.SPLASH -> listOf(
            Path().apply {
                moveTo(s(50f), s(3f))
                cubicTo(s(63f), s(18f), s(67f), s(27f), s(63f), s(34f))
                cubicTo(s(57f), s(42f), s(43f), s(42f), s(37f), s(34f))
                cubicTo(s(33f), s(27f), s(37f), s(18f), s(50f), s(3f))
                close()
            },
            Path().apply {
                moveTo(s(72f), s(76f))
                cubicTo(s(93f), s(76f), s(98f), s(52f), s(84f), s(45f))
                cubicTo(s(91f), s(59f), s(82f), s(67f), s(72f), s(63f))
                close()
            },
        )
    }

    val parts = feature + listOf(
        oval(50f, 46f, 23f, 21f), // head
        oval(50f, 72f, 26f, 22f), // body
        oval(36f, 92f, 10f, 6f), // feet
        oval(64f, 92f, 10f, 6f),
    )

    // Unioned into a single shape. Drawing the parts separately let the
    // translucent fill stack where they overlapped, so every joint showed as a
    // darker seam — the opposite of a silhouette.
    val creature = parts.reduce { accumulated, part ->
        Path().apply { op(accumulated, part, PathOperation.Union) }
    }
    drawPath(creature, color)
}
