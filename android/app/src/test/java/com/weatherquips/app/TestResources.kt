package com.weatherquips.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.text.QuipArrays
import java.util.Locale

/**
 * The app's resources in a given language, for tests that check content.
 * Requires Robolectric.
 */
object TestResources {

    fun resources(locale: Locale = Locale.ENGLISH): Resources {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }

    fun quotes(condition: WeatherCondition, isDay: Boolean, locale: Locale = Locale.ENGLISH) =
        resources(locale).getStringArray(QuipArrays.quotes(condition, isDay)).toList()

    fun subtitles(condition: WeatherCondition, isDay: Boolean, locale: Locale = Locale.ENGLISH) =
        resources(locale).getStringArray(QuipArrays.subtitles(condition, isDay)).toList()

    fun pokemonQuotes(condition: WeatherCondition, isDay: Boolean, locale: Locale = Locale.ENGLISH) =
        resources(locale).getStringArray(QuipArrays.pokemonQuotes(condition, isDay)).toList()

    fun pokemonSubtitles(condition: WeatherCondition, isDay: Boolean, locale: Locale = Locale.ENGLISH) =
        resources(locale).getStringArray(QuipArrays.pokemonSubtitles(condition, isDay)).toList()
}
