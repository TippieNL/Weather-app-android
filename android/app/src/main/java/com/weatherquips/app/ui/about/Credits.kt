package com.weatherquips.app.ui.about

import androidx.annotation.StringRes
import com.weatherquips.app.R

/**
 * Everything the app leans on, for the About page.
 *
 * Kept as data rather than written into the screen so tests can hold it to
 * the truth: every host the app contacts must be credited here, and every
 * library in the build must be listed with its licence. Names, attribution
 * lines and licence titles are proper nouns or legal text and stay as the
 * owners wrote them; what the app *uses* each one for is translated.
 */
data class ServiceCredit(
    val name: String,
    @StringRes val purpose: Int,
    /** The attribution line the provider asks for, verbatim. */
    val attribution: String,
    /** The data licence, where the provider publishes one. */
    val dataLicence: String? = null,
    val url: String,
    /** Hosts the app actually talks to for this service. */
    val hosts: List<String>,
    val needsApiKey: Boolean = false,
)

data class LibraryCredit(
    val name: String,
    val copyright: String,
    val licence: LicenceText,
    val url: String,
    /** Maven groups this entry covers, matched against the build's dependencies. */
    val groups: List<String> = emptyList(),
)

/** A licence the app ships in full, in `src/main/assets`. */
enum class LicenceText(val title: String, val asset: String) {
    APACHE_2("Apache License 2.0", "LICENSE-Apache-2.0.txt"),
    OFL_SPACE_GROTESK("SIL Open Font License 1.1", "OFL-SpaceGrotesk.txt"),
    OFL_DM_SANS("SIL Open Font License 1.1", "OFL-DMSans.txt"),
    LUCIDE("ISC License (with MIT for Feather-derived icons)", "LICENSE-Lucide.txt"),
    ;

    companion object {
        fun forAsset(asset: String): LicenceText? = entries.firstOrNull { it.asset == asset }
    }
}

object Credits {

    const val DEVELOPER = "TippieNL"
    const val SOURCE_URL = "https://github.com/TippieNL/Weather-app-android"

    val services: List<ServiceCredit> = listOf(
        ServiceCredit(
            name = "Open-Meteo",
            purpose = R.string.about_service_open_meteo,
            attribution = "Weather data by Open-Meteo.com",
            dataLicence = "CC BY 4.0",
            url = "https://open-meteo.com/",
            hosts = listOf("api.open-meteo.com"),
        ),
        ServiceCredit(
            name = "OpenWeatherMap",
            purpose = R.string.about_service_openweathermap,
            attribution = "Weather data provided by OpenWeather",
            url = "https://openweathermap.org/",
            hosts = listOf("api.openweathermap.org"),
            needsApiKey = true,
        ),
        ServiceCredit(
            name = "WeatherAPI.com",
            purpose = R.string.about_service_weatherapi,
            attribution = "Powered by WeatherAPI.com",
            url = "https://www.weatherapi.com/",
            hosts = listOf("api.weatherapi.com"),
            needsApiKey = true,
        ),
        ServiceCredit(
            name = "Bright Sky",
            purpose = R.string.about_service_brightsky,
            attribution = "Source: Deutscher Wetterdienst (DWD), via Bright Sky",
            url = "https://brightsky.dev/",
            hosts = listOf("api.brightsky.dev"),
        ),
        ServiceCredit(
            name = "RainViewer",
            purpose = R.string.about_service_rainviewer,
            attribution = "Radar © RainViewer",
            url = "https://www.rainviewer.com/api.html",
            hosts = listOf("api.rainviewer.com", "tilecache.rainviewer.com"),
        ),
        ServiceCredit(
            name = "OpenStreetMap",
            purpose = R.string.about_service_osm,
            attribution = "© OpenStreetMap contributors",
            dataLicence = "ODbL",
            url = "https://www.openstreetmap.org/copyright",
            hosts = listOf("tile.openstreetmap.org"),
        ),
        ServiceCredit(
            name = "Nominatim",
            purpose = R.string.about_service_nominatim,
            attribution = "© OpenStreetMap contributors",
            dataLicence = "ODbL",
            url = "https://nominatim.org/",
            hosts = listOf("nominatim.openstreetmap.org"),
        ),
    )

    val libraries: List<LibraryCredit> = listOf(
        LibraryCredit(
            name = "Android Jetpack",
            copyright = "© The Android Open Source Project",
            licence = LicenceText.APACHE_2,
            url = "https://developer.android.com/jetpack",
            groups = listOf(
                "androidx.core", "androidx.activity", "androidx.lifecycle", "androidx.navigation",
                "androidx.datastore", "androidx.work",
            ),
        ),
        LibraryCredit(
            name = "Jetpack Compose & Material 3",
            copyright = "© The Android Open Source Project",
            licence = LicenceText.APACHE_2,
            url = "https://developer.android.com/compose",
            groups = listOf("androidx.compose", "androidx.compose.ui", "androidx.compose.material3"),
        ),
        LibraryCredit(
            name = "Jetpack Glance",
            copyright = "© The Android Open Source Project",
            licence = LicenceText.APACHE_2,
            url = "https://developer.android.com/develop/ui/compose/glance",
            groups = listOf("androidx.glance"),
        ),
        LibraryCredit(
            name = "Kotlin, kotlinx.coroutines & kotlinx.serialization",
            copyright = "© JetBrains s.r.o. and Kotlin contributors",
            licence = LicenceText.APACHE_2,
            url = "https://kotlinlang.org/",
            groups = listOf("org.jetbrains.kotlinx", "org.jetbrains.kotlin"),
        ),
        LibraryCredit(
            name = "Retrofit",
            copyright = "© Square, Inc.",
            licence = LicenceText.APACHE_2,
            url = "https://square.github.io/retrofit/",
            groups = listOf("com.squareup.retrofit2"),
        ),
        LibraryCredit(
            name = "OkHttp",
            copyright = "© Square, Inc.",
            licence = LicenceText.APACHE_2,
            url = "https://square.github.io/okhttp/",
            groups = listOf("com.squareup.okhttp3"),
        ),
        LibraryCredit(
            name = "osmdroid",
            copyright = "© osmdroid contributors",
            licence = LicenceText.APACHE_2,
            url = "https://github.com/osmdroid/osmdroid",
            groups = listOf("org.osmdroid"),
        ),
    )

    val fontsAndIcons: List<LibraryCredit> = listOf(
        LibraryCredit(
            name = "Space Grotesk",
            copyright = "© 2020 The Space Grotesk Project Authors",
            licence = LicenceText.OFL_SPACE_GROTESK,
            url = "https://github.com/floriankarsten/space-grotesk",
        ),
        LibraryCredit(
            name = "DM Sans",
            copyright = "© 2014 The DM Sans Project Authors",
            licence = LicenceText.OFL_DM_SANS,
            url = "https://github.com/googlefonts/dm-fonts",
        ),
        LibraryCredit(
            name = "Lucide",
            copyright = "© Lucide Icons and Contributors; Feather © Cole Bemis",
            licence = LicenceText.LUCIDE,
            url = "https://lucide.dev/",
        ),
    )
}
