package com.weatherquips.app.locale

import java.util.Locale

/**
 * The languages the app can be switched to.
 *
 * Adding one is two steps: a `res/values-<tag>/` folder with the translated
 * strings, and an entry here. The system's per-app language list is generated
 * from the resource folders, and a unit test fails if an entry here has no
 * folder behind it.
 */
enum class AppLanguage(val tag: String?) {
    /** Follow the phone. */
    SYSTEM(null),
    ENGLISH("en"),
    DUTCH("nl");

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    /**
     * The language's name in itself — "Nederlands", not "Dutch" — which is how
     * a language picker should read to someone who cannot read the current one.
     */
    fun endonym(): String? = locale?.let { locale ->
        locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val language = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
