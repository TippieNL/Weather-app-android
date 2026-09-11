package com.weatherquips.app.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherIconKey
import com.weatherquips.app.domain.model.weatherIconKey

/**
 * Icon rendering. The web app used one consistent SVG pack (lucide); the same
 * glyphs ship here as Android vector drawables, so the artwork is identical
 * rather than merely similar.
 */
@DrawableRes
fun weatherIconRes(key: WeatherIconKey): Int = when (key) {
    WeatherIconKey.CLEAR_DAY -> R.drawable.ic_weather_clear_day
    WeatherIconKey.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night
    WeatherIconKey.CLOUDY_DAY -> R.drawable.ic_weather_cloudy_day
    WeatherIconKey.CLOUDY_NIGHT -> R.drawable.ic_weather_cloudy_night
    WeatherIconKey.RAINY_DAY -> R.drawable.ic_weather_rainy_day
    WeatherIconKey.RAINY_NIGHT -> R.drawable.ic_weather_rainy_night
    WeatherIconKey.STORMY -> R.drawable.ic_weather_stormy
    WeatherIconKey.STORMY_NIGHT -> R.drawable.ic_weather_stormy_night
    WeatherIconKey.SNOWY -> R.drawable.ic_weather_snowy
    WeatherIconKey.SNOWY_NIGHT -> R.drawable.ic_weather_snowy_night
    WeatherIconKey.FOGGY -> R.drawable.ic_weather_foggy
    WeatherIconKey.WINDY -> R.drawable.ic_weather_windy
    WeatherIconKey.HOT -> R.drawable.ic_weather_hot
    WeatherIconKey.COLD -> R.drawable.ic_weather_cold
}

/** Small static glyph for inline placements (detail card, condition line). */
@Composable
fun WeatherGlyph(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(weatherIconRes(weatherIconKey(condition, isDay))),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = LocalContentColor.current,
    )
}

/**
 * True unless the user (or a test/preview) has animations switched off. This is
 * Android's counterpart to the CSS `prefers-reduced-motion` guard.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f
        }.getOrDefault(true)
    }
}

/** The per-condition motion from `index.css`, ported to Compose animations. */
private enum class IconMotion { SPIN, PULSE, DRIFT, BOUNCE, FLASH, FADE_PULSE, SWAY }

private fun motionFor(condition: WeatherCondition): IconMotion = when (condition) {
    WeatherCondition.CLEAR -> IconMotion.SPIN
    WeatherCondition.HOT -> IconMotion.PULSE
    WeatherCondition.CLOUDY -> IconMotion.DRIFT
    WeatherCondition.RAINY -> IconMotion.BOUNCE
    WeatherCondition.STORMY -> IconMotion.FLASH
    WeatherCondition.SNOWY -> IconMotion.DRIFT
    WeatherCondition.COLD -> IconMotion.SPIN
    WeatherCondition.FOGGY -> IconMotion.FADE_PULSE
    WeatherCondition.WINDY -> IconMotion.SWAY
}

@Immutable
private data class IconTransform(
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
)

private fun reversing(durationMillis: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse)

/**
 * Exactly one infinite animation runs at a time: the composable only subscribes
 * to the value its condition actually needs, so an idle screen animates a single
 * property instead of six.
 */
@Composable
private fun rememberIconTransform(motion: IconMotion, driftPx: Float): IconTransform {
    val transition = rememberInfiniteTransition(label = "weather-icon-${motion.name}")
    return when (motion) {
        IconMotion.SPIN -> {
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
                label = "spin",
            )
            IconTransform(rotation = rotation)
        }

        IconMotion.SWAY -> {
            val rotation by transition.animateFloat(
                initialValue = -8f,
                targetValue = 8f,
                animationSpec = reversing(1_250),
                label = "sway",
            )
            IconTransform(rotation = rotation)
        }

        IconMotion.PULSE -> {
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = reversing(1_000),
                label = "pulse",
            )
            IconTransform(scale = 1f + 0.08f * progress, alpha = 0.85f + 0.15f * progress)
        }

        IconMotion.DRIFT -> {
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = driftPx,
                animationSpec = reversing(2_000),
                label = "drift",
            )
            IconTransform(translationX = offset)
        }

        IconMotion.BOUNCE -> {
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -driftPx * 0.75f,
                animationSpec = reversing(1_000),
                label = "bounce",
            )
            IconTransform(translationY = offset)
        }

        IconMotion.FADE_PULSE -> {
            val alpha by transition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = reversing(2_000),
                label = "fade-pulse",
            )
            IconTransform(alpha = alpha)
        }

        IconMotion.FLASH -> {
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2_000
                        1f at 0
                        0.3f at 200
                        1f at 240
                        0.4f at 280
                        1f at 400
                    },
                ),
                label = "flash",
            )
            IconTransform(alpha = alpha)
        }
    }
}

/**
 * The hero icon: a per-condition idle animation, a fade-and-scale entrance that
 * replays only when the icon truly changes, and the "explode" Easter egg.
 */
@Composable
fun AnimatedWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String? = null,
    exploding: Boolean = false,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val iconKey = remember(condition, isDay) { weatherIconKey(condition, isDay) }
    val driftPx = with(LocalDensity.current) { size.toPx() * DRIFT_FRACTION }

    if (exploding) {
        ExplodingWeatherIcon(
            size = size,
            modifier = modifier,
            animationsEnabled = animationsEnabled,
            contentDescription = contentDescription,
        )
        return
    }

    val transform = if (animationsEnabled) {
        rememberIconTransform(motionFor(condition), driftPx)
    } else {
        IconTransform()
    }

    val enter = remember(iconKey) { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(iconKey, animationsEnabled) {
        if (animationsEnabled) enter.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(weatherIconRes(iconKey)),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val entered = 0.8f + 0.2f * enter.value
                    rotationZ = transform.rotation
                    scaleX = transform.scale * entered
                    scaleY = transform.scale * entered
                    alpha = transform.alpha * enter.value
                    translationX = transform.translationX
                    translationY = transform.translationY
                },
            tint = LocalContentColor.current,
        )
    }
}

/** Six weather fragments flying apart — the five-rapid-taps Easter egg. */
@Composable
private fun ExplodingWeatherIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val scaleFactor = with(density) { size.toPx() } / with(density) { 160.dp.toPx() }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (animationsEnabled) progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        FRAGMENTS.forEach { fragment ->
            Icon(
                painter = painterResource(fragment.drawable),
                contentDescription = contentDescription.takeIf { fragment == FRAGMENTS.first() },
                modifier = Modifier
                    .size(size * FRAGMENT_SIZE_FRACTION)
                    .graphicsLayer {
                        val t = progress.value
                        translationX = with(density) { fragment.dx.dp.toPx() } * scaleFactor * t
                        translationY = with(density) { fragment.dy.dp.toPx() } * scaleFactor * t
                        rotationZ = fragment.rotation * t
                        scaleX = 1f - t
                        scaleY = 1f - t
                        alpha = 1f - t
                    },
                tint = LocalContentColor.current,
            )
        }
    }
}

private data class Fragment(
    @DrawableRes val drawable: Int,
    val dx: Float,
    val dy: Float,
    val rotation: Float,
)

// Offsets/rotations lifted from the `weather-frag-*` keyframes in index.css.
private val FRAGMENTS = listOf(
    Fragment(R.drawable.ic_cloud, -80f, -60f, -180f),
    Fragment(R.drawable.ic_weather_clear_day, 70f, -70f, 120f),
    Fragment(R.drawable.ic_weather_cold, 5f, -90f, 200f),
    Fragment(R.drawable.ic_zap, -70f, 55f, 160f),
    Fragment(R.drawable.ic_droplet, 80f, 50f, -140f),
    Fragment(R.drawable.ic_weather_windy, 15f, 85f, 240f),
)

private const val DRIFT_FRACTION = 0.025f
private const val FRAGMENT_SIZE_FRACTION = 0.3f
