package com.weatherquips.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accents that Material 3's colour scheme has no slot for. They stay identical
 * across light and dark on purpose — the temperature scale must mean the same
 * thing in both themes.
 */
@Immutable
data class WeatherQuipsAccents(
    val cold: Color,
    val hot: Color,
    val pokemonRed: Color,
    val pokemonGold: Color,
)

val LocalAccents = staticCompositionLocalOf {
    WeatherQuipsAccents(
        cold = WeatherQuipsColors.Cold,
        hot = WeatherQuipsColors.Hot,
        pokemonRed = WeatherQuipsColors.PokemonRed,
        pokemonGold = WeatherQuipsColors.PokemonGold,
    )
}

private val LightColors = lightColorScheme(
    primary = WeatherQuipsColors.LightForeground,
    onPrimary = WeatherQuipsColors.LightBackground,
    secondary = WeatherQuipsColors.LightSecondary,
    onSecondary = WeatherQuipsColors.LightSecondaryForeground,
    secondaryContainer = WeatherQuipsColors.LightSecondary,
    onSecondaryContainer = WeatherQuipsColors.LightForeground,
    background = WeatherQuipsColors.LightBackground,
    onBackground = WeatherQuipsColors.LightForeground,
    surface = WeatherQuipsColors.LightBackground,
    onSurface = WeatherQuipsColors.LightForeground,
    surfaceVariant = WeatherQuipsColors.LightSecondary,
    onSurfaceVariant = WeatherQuipsColors.LightMutedForeground,
    surfaceContainer = WeatherQuipsColors.LightCard,
    surfaceContainerHigh = WeatherQuipsColors.LightCard,
    surfaceContainerHighest = WeatherQuipsColors.LightCard,
    outline = WeatherQuipsColors.LightOutline,
    outlineVariant = WeatherQuipsColors.LightCardBorder,
    error = WeatherQuipsColors.LightDestructive,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = WeatherQuipsColors.DarkForeground,
    onPrimary = WeatherQuipsColors.DarkBackground,
    secondary = WeatherQuipsColors.DarkSecondary,
    onSecondary = WeatherQuipsColors.DarkSecondaryForeground,
    secondaryContainer = WeatherQuipsColors.DarkSecondary,
    onSecondaryContainer = WeatherQuipsColors.DarkForeground,
    background = WeatherQuipsColors.DarkBackground,
    onBackground = WeatherQuipsColors.DarkForeground,
    surface = WeatherQuipsColors.DarkBackground,
    onSurface = WeatherQuipsColors.DarkForeground,
    surfaceVariant = WeatherQuipsColors.DarkSecondary,
    onSurfaceVariant = WeatherQuipsColors.DarkMutedForeground,
    surfaceContainer = WeatherQuipsColors.DarkCard,
    surfaceContainerHigh = WeatherQuipsColors.DarkCard,
    surfaceContainerHighest = WeatherQuipsColors.DarkCard,
    outline = WeatherQuipsColors.DarkOutline,
    outlineVariant = WeatherQuipsColors.DarkCardBorder,
    error = WeatherQuipsColors.DarkDestructive,
    onError = Color.White,
)

/**
 * Dynamic colour is intentionally not used: the near-monochrome palette *is* the
 * product's identity, and Material You tinting would wash it out.
 */
@Composable
fun WeatherQuipsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalAccents provides LocalAccents.current) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WeatherQuipsTypography,
            content = content,
        )
    }
}
