package com.weatherquips.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Weather-Quips palette, converted from the HSL variables in
 * `client/src/index.css`. It is deliberately near-monochrome: all the colour in
 * the app comes from the temperature accents below.
 */
object WeatherQuipsColors {
    // Light — "Minimalist Black & White"
    val LightBackground = Color(0xFFFAFAFA)
    val LightForeground = Color(0xFF171717)
    val LightCard = Color(0xFFFFFFFF)
    val LightCardBorder = Color(0xFFEBEBEB)
    val LightBorder = Color(0xFFE5E5E5)
    val LightSecondary = Color(0xFFF0F0F0)
    val LightSecondaryForeground = Color(0xFF333333)
    val LightMutedForeground = Color(0xFF737373)
    val LightDestructive = Color(0xFFDC2626)
    val LightOutline = Color(0xFFD9D9D9)

    // Dark
    val DarkBackground = Color(0xFF0D0D0D)
    val DarkForeground = Color(0xFFF2F2F2)
    val DarkCard = Color(0xFF141414)
    val DarkCardBorder = Color(0xFF262626)
    val DarkBorder = Color(0xFF2E2E2E)
    val DarkSecondary = Color(0xFF262626)
    val DarkSecondaryForeground = Color(0xFFD9D9D9)
    val DarkMutedForeground = Color(0xFF8C8C8C)
    val DarkDestructive = Color(0xFFB92929)
    val DarkOutline = Color(0xFF383838)

    /** Temperature accents, identical in both themes (COLD_COLOR / HOT_COLOR). */
    val Cold = Color(0xFF3B82F6)
    val Hot = Color(0xFFEF4444)

    /** Easter-egg accents. */
    val PokemonRed = Color(0xFFEE1515)
    val PokemonGold = Color(0xFFFBBF24)
}

/**
 * Blend between the cold and hot accents. Ported from `tempColor()` in
 * `home.tsx`, which interpolates rgb(59,130,246) → rgb(239,68,68).
 */
fun temperatureColor(normalized: Float): Color {
    val t = normalized.coerceIn(0f, 1f)
    val r = 59 + (239 - 59) * t
    val g = 130 + (68 - 130) * t
    val b = 246 + (68 - 246) * t
    return Color(r / 255f, g / 255f, b / 255f)
}
