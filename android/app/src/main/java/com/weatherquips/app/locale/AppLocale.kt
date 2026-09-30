package com.weatherquips.app.locale

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language, the way Android recommends it.
 *
 * On Android 13 and later the platform owns it: [LocaleManager] persists the
 * choice, applies it to every context in the process — widget and
 * notifications included — recreates the activity, and lists it under the
 * app's language in system settings, where the user can change it too.
 *
 * Below 13 there is no platform support, so the choice is kept here and
 * applied by wrapping the activity's base context. Text produced outside an
 * activity asks [localized] for a context in the right language.
 *
 * The copy below 13 lives in SharedPreferences rather than DataStore on
 * purpose: `attachBaseContext` runs before anything else exists and must read
 * it synchronously, which DataStore exists precisely to prevent.
 */
object AppLocale {

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    private val platformManaged: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The language the user picked, or [AppLanguage.SYSTEM]. */
    fun current(context: Context): AppLanguage {
        if (platformManaged) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null) {
                return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
            }
        }
        return stored(context)
    }

    /**
     * Persists and applies [language].
     *
     * @return true when the caller must recreate its activity to show it.
     *         On 13+ the platform does that itself.
     */
    fun set(context: Context, language: AppLanguage): Boolean {
        // Kept on every version: it is what text outside activities reads
        // below 13, and a fallback should the platform service be missing.
        // apply() updates the in-memory copy at once, which is what the
        // recreated activity reads; the disk write can follow in the background.
        prefs(context).edit().putString(KEY_TAG, language.tag).apply()
        if (platformManaged) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager != null) {
                manager.applicationLocales = language.tag
                    ?.let(LocaleList::forLanguageTags)
                    ?: LocaleList.getEmptyLocaleList()
                return false
            }
        }
        return true
    }

    /** For `attachBaseContext` below Android 13; returns [base] untouched above it. */
    fun wrap(base: Context): Context {
        if (platformManaged) return base
        val locale = stored(base).locale ?: return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(configuration)
    }

    /**
     * A context whose resources are in the app's language, for the widget,
     * notifications and anything else rendered without an activity.
     */
    fun localized(context: Context): Context = if (platformManaged) context else wrap(context)

    private fun stored(context: Context): AppLanguage =
        AppLanguage.fromTag(prefs(context).getString(KEY_TAG, null))

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What the settings screen needs from the language machinery. */
interface LanguageController {
    fun current(): AppLanguage

    /** @return true when the activity must be recreated to show the change. */
    fun set(language: AppLanguage): Boolean
}

class PlatformLanguageController(
    private val context: Context,
    /** Redraws what lives outside the activity: the widget, the notification channel. */
    private val onChanged: () -> Unit,
) : LanguageController {

    override fun current(): AppLanguage = AppLocale.current(context)

    override fun set(language: AppLanguage): Boolean {
        val mustRecreate = AppLocale.set(context, language)
        // On 13+ the application hears about it as a configuration change and
        // redraws from there, once the new locale is actually in effect.
        if (mustRecreate) onChanged()
        return mustRecreate
    }
}
