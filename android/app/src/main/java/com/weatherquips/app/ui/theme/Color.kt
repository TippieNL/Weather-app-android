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

/**
 * Colour for an actual temperature, on a fixed scale.
 *
 * The web app coloured each forecast relative to the min and max currently on
 * screen, which made a 1 °C spread look dramatic: 16° came out cold-blue and
 * 17° hot-red in the same row. Anchoring the scale means a temperature always
 * has the same colour — across the hourly strip, the week and the range bars —
 * so colour carries information instead of noise.
 *
 * A straight blue→red interpolation is useless here, because everything
 * between about 8 °C and 22 °C — which is most weather, most of the time —
 * lands on the same muddy purple. These stops keep that band legible while
 * still ending at the app's cold blue and hot red.
 */
fun temperatureColorFor(celsius: Double): Color {
    val stops = TEMPERATURE_STOPS
    if (celsius <= stops.first().first) return stops.first().second
    if (celsius >= stops.last().first) return stops.last().second

    val upperIndex = stops.indexOfFirst { celsius <= it.first }.coerceAtLeast(1)
    val (lowTemp, lowColor) = stops[upperIndex - 1]
    val (highTemp, highColor) = stops[upperIndex]
    val t = ((celsius - lowTemp) / (highTemp - lowTemp)).toFloat().coerceIn(0f, 1f)

    return Color(
        red = lowColor.red + (highColor.red - lowColor.red) * t,
        green = lowColor.green + (highColor.green - lowColor.green) * t,
        blue = lowColor.blue + (highColor.blue - lowColor.blue) * t,
    )
}

/**
 * Temperature → colour anchors, in °C.
 *
 * Blue at the cold end and red at the hot end are the app's own accents. The
 * middle deliberately passes through a neutral rather than a third hue: mild
 * weather then reads as monochrome, which suits the design, and colour only
 * appears when the temperature is genuinely notable.
 */
private val TEMPERATURE_STOPS = listOf(
    -10.0 to Color(0xFF1D4ED8), // deep freeze
    0.0 to Color(0xFF3B82F6), // the app's cold accent
    8.0 to Color(0xFF60A5FA), // chilly
    15.0 to Color(0xFFAEB4BB), // mild — near-neutral on purpose
    21.0 to Color(0xFFFB923C), // warm
    27.0 to Color(0xFFEF4444), // the app's hot accent
    35.0 to Color(0xFFB91C1C), // scorching
)
