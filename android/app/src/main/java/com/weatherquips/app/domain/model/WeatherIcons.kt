package com.weatherquips.app.domain.model

/**
 * Icon logic — pure and side-effect free, ported from `lib/weatherIcons.ts`.
 *
 * Only sky-based conditions change with day/night; temperature and air
 * conditions (hot, cold, windy) and dark-cloud ones (stormy, snowy, foggy) read
 * the same either way, so they deliberately ignore `isDay`.
 */
enum class WeatherIconKey(val id: String) {
    CLEAR_DAY("clear-day"),
    CLEAR_NIGHT("clear-night"),
    CLOUDY_DAY("cloudy-day"),
    CLOUDY_NIGHT("cloudy-night"),
    RAINY_DAY("rainy-day"),
    RAINY_NIGHT("rainy-night"),
    STORMY("stormy"),
    STORMY_NIGHT("stormy-night"),
    SNOWY("snowy"),
    SNOWY_NIGHT("snowy-night"),
    FOGGY("foggy"),
    WINDY("windy"),
    HOT("hot"),
    COLD("cold"),
}

/**
 * Conditions whose icon changes with the sun.
 *
 * The rule is whether the sky itself is part of the picture. Storms and snow
 * fall out of a sky you can still see, so they get a moon after dark. Fog
 * replaces the sky rather than sitting under it, and wind, heat and cold are
 * about the air rather than the sky at all — those look the same at any hour.
 */
private val DAY_NIGHT_AWARE = setOf(
    WeatherCondition.CLEAR,
    WeatherCondition.CLOUDY,
    WeatherCondition.RAINY,
    WeatherCondition.STORMY,
    WeatherCondition.SNOWY,
)

fun weatherIconKey(condition: WeatherCondition, isDay: Boolean): WeatherIconKey =
    if (condition in DAY_NIGHT_AWARE) {
        when (condition) {
            WeatherCondition.CLEAR ->
                if (isDay) WeatherIconKey.CLEAR_DAY else WeatherIconKey.CLEAR_NIGHT
            WeatherCondition.CLOUDY ->
                if (isDay) WeatherIconKey.CLOUDY_DAY else WeatherIconKey.CLOUDY_NIGHT
            WeatherCondition.STORMY ->
                if (isDay) WeatherIconKey.STORMY else WeatherIconKey.STORMY_NIGHT
            WeatherCondition.SNOWY ->
                if (isDay) WeatherIconKey.SNOWY else WeatherIconKey.SNOWY_NIGHT
            else ->
                if (isDay) WeatherIconKey.RAINY_DAY else WeatherIconKey.RAINY_NIGHT
        }
    } else {
        when (condition) {
            WeatherCondition.STORMY -> WeatherIconKey.STORMY
            WeatherCondition.SNOWY -> WeatherIconKey.SNOWY
            WeatherCondition.FOGGY -> WeatherIconKey.FOGGY
            WeatherCondition.WINDY -> WeatherIconKey.WINDY
            WeatherCondition.HOT -> WeatherIconKey.HOT
            else -> WeatherIconKey.COLD
        }
    }

/** Fallback day/night detection when a provider gives us nothing (06:00–18:00 = day). */
fun isDayFromHour(hour: Int): Boolean = hour in 6..17
