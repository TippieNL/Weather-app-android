package com.weatherquips.app.text

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherCondition

/**
 * A quip, by reference: which list it comes from and a seed that picks the line.
 *
 * The text itself is only looked up when it is drawn. That is what lets a
 * language switch re-render the same joke in the new language on the spot,
 * including one loaded from the offline cache, and what lets a translation
 * have more or fewer lines than the original without breaking anything: the
 * seed is simply taken modulo whatever the list holds.
 */
@Immutable
data class QuipRef(@ArrayRes val array: Int, val seed: Int) {
    companion object {
        val NONE = QuipRef(array = 0, seed = 0)
    }
}

/** A line from [ref] in the current language; empty when there is nothing to show. */
@Composable
fun quipText(ref: QuipRef): String {
    if (ref.array == 0) return ""
    return stringArrayResource(ref.array).pick(ref.seed)
}

/** The same lookup outside composition, for notifications and the widget. */
fun Resources.quipText(ref: QuipRef): String =
    if (ref.array == 0) "" else getStringArray(ref.array).pick(ref.seed)

fun Resources.pick(@ArrayRes array: Int, seed: Int): String = getStringArray(array).pick(seed)

fun Array<String>.pick(seed: Int): String = if (isEmpty()) "" else this[Math.floorMod(seed, size)]

/**
 * Where each list lives. Explicit on purpose: a missing array is a compile
 * error here rather than a blank line on screen, and nothing is looked up by
 * name at runtime for the shrinker to strip.
 */
object QuipArrays {

    @ArrayRes
    fun quotes(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.array.quip_quotes_clear_day else R.array.quip_quotes_clear_night
        WeatherCondition.CLOUDY -> if (isDay) R.array.quip_quotes_cloudy_day else R.array.quip_quotes_cloudy_night
        WeatherCondition.RAINY -> if (isDay) R.array.quip_quotes_rainy_day else R.array.quip_quotes_rainy_night
        WeatherCondition.STORMY -> if (isDay) R.array.quip_quotes_stormy_day else R.array.quip_quotes_stormy_night
        WeatherCondition.SNOWY -> if (isDay) R.array.quip_quotes_snowy_day else R.array.quip_quotes_snowy_night
        WeatherCondition.FOGGY -> if (isDay) R.array.quip_quotes_foggy_day else R.array.quip_quotes_foggy_night
        WeatherCondition.WINDY -> if (isDay) R.array.quip_quotes_windy_day else R.array.quip_quotes_windy_night
        WeatherCondition.HOT -> if (isDay) R.array.quip_quotes_hot_day else R.array.quip_quotes_hot_night
        WeatherCondition.COLD -> if (isDay) R.array.quip_quotes_cold_day else R.array.quip_quotes_cold_night
    }

    @ArrayRes
    fun subtitles(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.array.quip_subtitles_clear_day else R.array.quip_subtitles_clear_night
        WeatherCondition.CLOUDY -> if (isDay) R.array.quip_subtitles_cloudy_day else R.array.quip_subtitles_cloudy_night
        WeatherCondition.RAINY -> if (isDay) R.array.quip_subtitles_rainy_day else R.array.quip_subtitles_rainy_night
        WeatherCondition.STORMY -> if (isDay) R.array.quip_subtitles_stormy_day else R.array.quip_subtitles_stormy_night
        WeatherCondition.SNOWY -> if (isDay) R.array.quip_subtitles_snowy_day else R.array.quip_subtitles_snowy_night
        WeatherCondition.FOGGY -> if (isDay) R.array.quip_subtitles_foggy_day else R.array.quip_subtitles_foggy_night
        WeatherCondition.WINDY -> if (isDay) R.array.quip_subtitles_windy_day else R.array.quip_subtitles_windy_night
        WeatherCondition.HOT -> if (isDay) R.array.quip_subtitles_hot_day else R.array.quip_subtitles_hot_night
        WeatherCondition.COLD -> if (isDay) R.array.quip_subtitles_cold_day else R.array.quip_subtitles_cold_night
    }

    @ArrayRes
    fun pokemonQuotes(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.array.pokemon_quotes_clear_day else R.array.pokemon_quotes_clear_night
        WeatherCondition.CLOUDY -> if (isDay) R.array.pokemon_quotes_cloudy_day else R.array.pokemon_quotes_cloudy_night
        WeatherCondition.RAINY -> if (isDay) R.array.pokemon_quotes_rainy_day else R.array.pokemon_quotes_rainy_night
        WeatherCondition.STORMY -> if (isDay) R.array.pokemon_quotes_stormy_day else R.array.pokemon_quotes_stormy_night
        WeatherCondition.SNOWY -> if (isDay) R.array.pokemon_quotes_snowy_day else R.array.pokemon_quotes_snowy_night
        WeatherCondition.FOGGY -> if (isDay) R.array.pokemon_quotes_foggy_day else R.array.pokemon_quotes_foggy_night
        WeatherCondition.WINDY -> if (isDay) R.array.pokemon_quotes_windy_day else R.array.pokemon_quotes_windy_night
        WeatherCondition.HOT -> if (isDay) R.array.pokemon_quotes_hot_day else R.array.pokemon_quotes_hot_night
        WeatherCondition.COLD -> if (isDay) R.array.pokemon_quotes_cold_day else R.array.pokemon_quotes_cold_night
    }

    @ArrayRes
    fun pokemonSubtitles(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.array.pokemon_subtitles_clear_day else R.array.pokemon_subtitles_clear_night
        WeatherCondition.CLOUDY -> if (isDay) R.array.pokemon_subtitles_cloudy_day else R.array.pokemon_subtitles_cloudy_night
        WeatherCondition.RAINY -> if (isDay) R.array.pokemon_subtitles_rainy_day else R.array.pokemon_subtitles_rainy_night
        WeatherCondition.STORMY -> if (isDay) R.array.pokemon_subtitles_stormy_day else R.array.pokemon_subtitles_stormy_night
        WeatherCondition.SNOWY -> if (isDay) R.array.pokemon_subtitles_snowy_day else R.array.pokemon_subtitles_snowy_night
        WeatherCondition.FOGGY -> if (isDay) R.array.pokemon_subtitles_foggy_day else R.array.pokemon_subtitles_foggy_night
        WeatherCondition.WINDY -> if (isDay) R.array.pokemon_subtitles_windy_day else R.array.pokemon_subtitles_windy_night
        WeatherCondition.HOT -> if (isDay) R.array.pokemon_subtitles_hot_day else R.array.pokemon_subtitles_hot_night
        WeatherCondition.COLD -> if (isDay) R.array.pokemon_subtitles_cold_day else R.array.pokemon_subtitles_cold_night
    }
}
