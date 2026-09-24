package com.weatherquips.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.ui.theme.LocalAccents
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The weather around the hero icon: rain falling past it, snow drifting, stars
 * over a clear night, the sun's glow breathing, fog and wind moving through.
 *
 * The icon on its own says what the weather is; this says what it is *like*,
 * and it is the part of the home screen that makes a rainy morning look
 * different from a dry one at a glance across the room.
 *
 * Drawn in the icon's own box but deliberately not confined to it: the hero
 * has a large empty field above and beside the icon, and the atmosphere uses
 * it. Everything runs off a single looping clock, with each particle's period
 * a whole fraction of the loop so nothing jumps when it wraps, and the
 * positions come from a fixed seed so the scene is identical on every frame
 * it is asked to redraw.
 */
@Composable
fun WeatherAtmosphere(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val scene = remember(condition, isDay) { sceneFor(condition, isDay) }
    if (scene == Scene.NONE) return

    // The clock is read inside the draw lambda, not here: each frame then
    // only redraws the canvas instead of recomposing the hero around it.
    val clockState: State<Float>? = if (animationsEnabled) {
        rememberInfiniteTransition(label = "atmosphere").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing)),
            label = "atmosphere-clock",
        )
    } else {
        null
    }

    val particles = remember(scene) { particlesFor(scene) }
    val palette = atmospherePalette()
    val cloud = painterResource(R.drawable.ic_cloud)

    Canvas(modifier = modifier) {
        // Reduced motion still deserves the atmosphere, just holding still.
        val clock = clockState?.value ?: STILL_FRAME
        when (scene) {
            Scene.RAIN -> drawRain(particles, clock, palette.water, heavy = false)
            Scene.STORM -> {
                drawFlash(clock, palette.lightning)
                drawRain(particles, clock, palette.water, heavy = true)
            }
            Scene.SNOW -> drawSnow(particles, clock, palette.snow)
            Scene.FROST -> drawSparkles(particles, clock, palette.snow)
            Scene.SUN -> drawSun(clock, palette.sun, intense = false)
            Scene.HEAT -> drawSun(clock, palette.heat, intense = true)
            Scene.STARS -> drawStars(particles, clock, palette.star, dim = false)
            Scene.CLOUDY_NIGHT -> {
                drawStars(particles, clock, palette.star, dim = true)
                drawGhostClouds(cloud, clock, palette.cloud)
            }
            Scene.CLOUDS -> drawGhostClouds(cloud, clock, palette.cloud)
            Scene.FOG -> drawFog(particles, clock, palette.fog)
            Scene.WIND -> drawWind(particles, clock, palette.wind)
            Scene.NONE -> Unit
        }
    }
}

internal enum class Scene { RAIN, STORM, SNOW, FROST, SUN, HEAT, STARS, CLOUDY_NIGHT, CLOUDS, FOG, WIND, NONE }

internal fun sceneFor(condition: WeatherCondition, isDay: Boolean): Scene = when (condition) {
    WeatherCondition.RAINY -> Scene.RAIN
    WeatherCondition.STORMY -> Scene.STORM
    WeatherCondition.SNOWY -> Scene.SNOW
    WeatherCondition.COLD -> Scene.FROST
    WeatherCondition.CLEAR -> if (isDay) Scene.SUN else Scene.STARS
    WeatherCondition.HOT -> Scene.HEAT
    WeatherCondition.CLOUDY -> if (isDay) Scene.CLOUDS else Scene.CLOUDY_NIGHT
    WeatherCondition.FOGGY -> Scene.FOG
    WeatherCondition.WINDY -> Scene.WIND
}

@Immutable
private data class AtmospherePalette(
    val water: Color,
    val lightning: Color,
    val snow: Color,
    val sun: Color,
    val heat: Color,
    val star: Color,
    val cloud: Color,
    val fog: Color,
    val wind: Color,
)

/**
 * Colours that read on both themes. White snow on a white background is no
 * snow at all, so pale particles take the cold accent in the light theme.
 */
@Composable
private fun atmospherePalette(): AtmospherePalette {
    val scheme = MaterialTheme.colorScheme
    val accents = LocalAccents.current
    val dark = scheme.background.luminance() < 0.5f
    val neutral = scheme.onBackground
    return AtmospherePalette(
        water = accents.cold,
        lightning = if (dark) Color(0xFFBFD7FF) else accents.cold,
        snow = if (dark) neutral else accents.cold,
        sun = Color(0xFFF59E0B),
        heat = accents.hot,
        star = if (dark) neutral else accents.cold,
        cloud = neutral,
        fog = neutral,
        wind = neutral,
    )
}

/**
 * One particle, in units of the icon's size so the scene scales with it.
 * [x] and [y] are its home position; [speed] is how many times it completes
 * its motion per loop, always a whole number so the loop wraps cleanly.
 */
@Immutable
private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Int,
    val phase: Float,
    val alpha: Float,
)

/** The field the atmosphere may use, in icon sizes, relative to the icon's box. */
private const val FIELD_LEFT = -0.1f
private const val FIELD_RIGHT = 2.15f
private const val FIELD_TOP = -1.15f
private const val FIELD_BOTTOM = 1.02f

private fun particlesFor(scene: Scene): List<Particle> {
    val random = Random(scene.ordinal * 7919 + 17)
    fun range(from: Float, to: Float) = from + random.nextFloat() * (to - from)

    val count = when (scene) {
        Scene.RAIN -> 22
        Scene.STORM -> 30
        Scene.SNOW -> 26
        Scene.FROST -> 12
        Scene.STARS -> 16
        Scene.CLOUDY_NIGHT -> 8
        Scene.FOG -> 6
        Scene.WIND -> 10
        else -> 0
    }
    return List(count) {
        when (scene) {
            Scene.RAIN, Scene.STORM -> Particle(
                x = range(FIELD_LEFT, FIELD_RIGHT),
                y = 0f,
                size = range(0.07f, 0.13f),
                speed = if (scene == Scene.STORM) random.nextInt(30, 38) else random.nextInt(22, 30),
                phase = random.nextFloat(),
                alpha = range(0.22f, 0.5f),
            )
            Scene.SNOW -> Particle(
                x = range(FIELD_LEFT, FIELD_RIGHT),
                y = 0f,
                size = range(0.010f, 0.022f),
                speed = random.nextInt(3, 5),
                phase = random.nextFloat(),
                alpha = range(0.35f, 0.75f),
            )
            Scene.FOG -> Particle(
                x = range(0f, 1.2f),
                y = range(-0.7f, 0.95f),
                size = range(0.8f, 1.4f),
                speed = random.nextInt(1, 3),
                phase = random.nextFloat(),
                alpha = range(0.08f, 0.17f),
            )
            Scene.WIND -> Particle(
                x = 0f,
                y = range(-0.95f, 0.9f),
                size = range(0.45f, 0.85f),
                speed = random.nextInt(10, 15),
                phase = random.nextFloat(),
                alpha = range(0.30f, 0.55f),
            )
            else -> Particle( // stars and frost glints: fixed points that twinkle
                x = range(FIELD_LEFT, FIELD_RIGHT),
                y = range(FIELD_TOP, 0.15f),
                size = range(0.008f, 0.018f),
                speed = random.nextInt(6, 12),
                phase = random.nextFloat(),
                alpha = range(0.5f, 1f),
            )
        }
    }.filterNot { it.overlapsIcon() }
}

/** Keeps stars and glints out from behind the glyph, where they would clutter it. */
private fun Particle.overlapsIcon(): Boolean =
    size < 0.03f && y in -0.05f..1.0f && x in -0.05f..1.05f && y != 0f

/** Where in its own cycle a particle is, 0..1. */
private fun Particle.cycle(clock: Float): Float {
    val p = clock * speed + phase
    return p - floor(p)
}

/** Soft in, soft out: 0 at both ends of a cycle, 1 through the middle. */
private fun envelope(p: Float, fade: Float = 0.15f): Float = when {
    p < fade -> p / fade
    p > 1f - fade -> (1f - p) / fade
    else -> 1f
}

private fun DrawScope.u(value: Float) = value * size.width

private fun DrawScope.drawRain(particles: List<Particle>, clock: Float, colour: Color, heavy: Boolean) {
    val slant = if (heavy) 0.28f else 0.16f
    val stroke = u(if (heavy) 0.012f else 0.010f)
    particles.forEach { drop ->
        val p = drop.cycle(clock)
        val y = u(FIELD_TOP + (FIELD_BOTTOM - FIELD_TOP) * p)
        val x = u(drop.x) + (y - u(FIELD_TOP)) * slant * 0.15f
        val length = u(drop.size)
        drawLine(
            color = colour.copy(alpha = drop.alpha * envelope(p)),
            start = Offset(x, y),
            end = Offset(x - length * slant, y - length),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A lightning glow behind the icon, timed to the icon's own flicker. */
private fun DrawScope.drawFlash(clock: Float, colour: Color) {
    // The icon flickers on a two-second cycle; twelve of them make the loop.
    val ms = (clock * LOOP_MILLIS) % 2_000f
    val intensity = when {
        ms < 160f -> 0f
        ms < 200f -> (ms - 160f) / 40f
        ms < 240f -> 1f - (ms - 200f) / 40f * 0.6f
        ms < 280f -> 0.4f + (ms - 240f) / 40f * 0.6f
        ms < 480f -> 1f - (ms - 280f) / 200f
        else -> 0f
    }
    if (intensity <= 0f) return
    val centre = Offset(u(0.5f), u(0.45f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colour.copy(alpha = 0.55f * intensity), Color.Transparent),
            center = centre,
            radius = u(1.25f),
        ),
        radius = u(1.25f),
        center = centre,
    )
}

private fun DrawScope.drawSnow(particles: List<Particle>, clock: Float, colour: Color) {
    particles.forEach { flake ->
        val p = flake.cycle(clock)
        val y = u(FIELD_TOP + (FIELD_BOTTOM - FIELD_TOP) * p)
        val sway = sin((p * 2f + flake.phase) * 2f * PI.toFloat()) * u(0.06f)
        drawCircle(
            color = colour.copy(alpha = flake.alpha * envelope(p, fade = 0.2f)),
            radius = u(flake.size),
            center = Offset(u(flake.x) + sway, y),
        )
    }
}

private fun DrawScope.drawStars(particles: List<Particle>, clock: Float, colour: Color, dim: Boolean) {
    particles.forEach { star ->
        val twinkle = 0.5f + 0.5f * sin(star.cycle(clock) * 2f * PI.toFloat())
        val alpha = star.alpha * (0.2f + 0.8f * twinkle) * if (dim) 0.55f else 1f
        val centre = Offset(u(star.x), u(star.y))
        val radius = u(star.size)
        drawCircle(colour.copy(alpha = alpha), radius, centre)
        // The brighter ones catch a four-point glint as they peak.
        if (star.size > 0.014f && twinkle > 0.6f) {
            val arm = radius * 3.2f * (twinkle - 0.6f) / 0.4f
            val glint = colour.copy(alpha = alpha * 0.7f)
            drawLine(glint, centre - Offset(arm, 0f), centre + Offset(arm, 0f), radius * 0.45f, StrokeCap.Round)
            drawLine(glint, centre - Offset(0f, arm), centre + Offset(0f, arm), radius * 0.45f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSparkles(particles: List<Particle>, clock: Float, colour: Color) {
    drawStars(particles, clock, colour, dim = false)
}

/** A breathing glow, with a ring of light rolling outward every few seconds. */
private fun DrawScope.drawSun(clock: Float, colour: Color, intense: Boolean) {
    val centre = Offset(u(0.5f), u(0.5f))
    val breath = 0.5f + 0.5f * sin(clock * 6f * 2f * PI.toFloat())
    val glowRadius = u(0.95f + 0.1f * breath)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colour.copy(alpha = (if (intense) 0.42f else 0.30f) + 0.12f * breath),
                colour.copy(alpha = 0f),
            ),
            center = centre,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = centre,
    )

    val rings = if (intense) 2 else 1
    repeat(rings) { index ->
        val p = (clock * 8f + index / rings.toFloat()).let { it - floor(it) }
        drawCircle(
            color = colour.copy(alpha = 0.35f * (1f - p)),
            radius = u(0.55f + 0.7f * p),
            center = centre,
            style = Stroke(width = u(0.012f) * (1f - p) + 1f),
        )
    }
}

/** Two faint clouds crossing the sky behind the icon, at different speeds. */
private fun DrawScope.drawGhostClouds(cloud: Painter, clock: Float, colour: Color) {
    listOf(
        // Cloudy is the most common sky there is, so it gets real presence:
        // a large slow cloud high up and a smaller quicker one lower down.
        Triple(0.95f, -1.05f, 0.20f) to 1,
        Triple(0.58f, -0.35f, 0.15f) to 2,
    ).forEachIndexed { index, (shape, laps) ->
        val (scale, row, alpha) = shape
        val p = (clock * laps + index * 0.45f).let { it - floor(it) }
        val width = u(scale)
        val x = u(-0.5f) + (u(FIELD_RIGHT + 0.5f) - u(-0.5f)) * p
        translate(left = x, top = u(row)) {
            with(cloud) {
                draw(
                    size = Size(width, width),
                    alpha = alpha * envelope(p, fade = 0.12f),
                    colorFilter = ColorFilter.tint(colour),
                )
            }
        }
    }
}

private fun DrawScope.drawFog(particles: List<Particle>, clock: Float, colour: Color) {
    particles.forEach { band ->
        val drift = sin((band.cycle(clock) + band.phase) * 2f * PI.toFloat()) * u(0.35f)
        val start = Offset(u(band.x) + drift, u(band.y))
        drawLine(
            color = colour.copy(alpha = band.alpha),
            start = start,
            end = start + Offset(u(band.size), 0f),
            strokeWidth = u(0.07f),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawWind(particles: List<Particle>, clock: Float, colour: Color) {
    particles.forEach { gust ->
        val p = gust.cycle(clock)
        val head = u(FIELD_LEFT - 0.6f) + (u(FIELD_RIGHT + 0.6f) - u(FIELD_LEFT - 0.6f)) * p
        val length = u(gust.size)
        val y = u(gust.y) + sin(p * PI.toFloat()) * u(-0.05f)
        rotate(degrees = -4f, pivot = Offset(head, y)) {
            drawLine(
                color = colour.copy(alpha = gust.alpha * sin(p * PI.toFloat())),
                start = Offset(head - length, y),
                end = Offset(head, y),
                strokeWidth = u(0.016f),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Long enough that the slowest things (a cloud crossing the sky) take their
 * time; every particle speed is a whole multiple of it.
 */
private const val LOOP_MILLIS = 24_000

/** A mid-motion frame for reduced motion and previews. */
private const val STILL_FRAME = 0.37f
