package com.weatherquips.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.weatherquips.app.ui.about.AboutScreen
import com.weatherquips.app.ui.about.Credits
import com.weatherquips.app.ui.about.LicenceScreen
import com.weatherquips.app.ui.about.LicenceText
import com.weatherquips.app.ui.about.TAG_ABOUT_LIBRARY_PREFIX
import com.weatherquips.app.ui.about.TAG_ABOUT_SERVICE_PREFIX
import com.weatherquips.app.ui.about.TAG_ABOUT_VERSION
import com.weatherquips.app.ui.about.TAG_LICENCE_TEXT
import com.weatherquips.app.ui.theme.WeatherQuipsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URI

/**
 * The About page has to tell the truth: these hold it to the network code and
 * the build file, so adding a service or a library without crediting it — or
 * crediting one the app no longer uses — fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class AboutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Unit tests run with the module directory as the working directory.
    private val sources = File("src/main/java")
    private val assets = File("src/main/assets")

    // --- the credits against the code -------------------------------------

    /** Every host the app's own code sends requests to. */
    private fun contactedHosts(): Set<String> {
        val literal = Regex(""""https://([a-z0-9.-]+)[/"$]""")
        val fromCode = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The About page lists the providers' websites, which it links to
            // rather than calls; everything else is a request the app makes.
            .filterNot { it.invariantSeparatorsPath.contains("/ui/about/") }
            .flatMap { file ->
                file.readLines()
                    .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                    // The User-Agent names the repository; nothing fetches it.
                    .filterNot { it.contains("USER_AGENT") }
                    .flatMap { line -> literal.findAll(line).map { it.groupValues[1] } }
            }
            .toSet()
        // The base map comes from inside osmdroid, so ask it where.
        val baseMap = URI(TileSourceFactory.MAPNIK.baseUrl).host
        return fromCode + baseMap
    }

    @Test
    fun `every host the app contacts is credited`() {
        val credited = Credits.services.flatMap { it.hosts }.toSet()
        val hosts = contactedHosts()
        assertTrue("found no hosts at all — the scan is broken", hosts.size >= 5)
        val uncredited = hosts - credited
        assertTrue("contacted but not credited: $uncredited", uncredited.isEmpty())
    }

    @Test
    fun `nothing is credited that the app does not use`() {
        val stale = Credits.services.flatMap { it.hosts }.toSet() - contactedHosts()
        assertTrue("credited but never contacted: $stale", stale.isEmpty())
    }

    @Test
    fun `every library in the build is credited`() {
        val catalogue = File("../gradle/libs.versions.toml").readLines()
            .mapNotNull { line ->
                Regex("""^([a-z0-9-]+)\s*=\s*\{\s*group\s*=\s*"([^"]+)"""").find(line)
                    ?.let { it.groupValues[1].replace('-', '.') to it.groupValues[2] }
            }
            .toMap()
        val used = File("build.gradle.kts").readLines()
            .filter { it.trimStart().startsWith("implementation(") }
            .mapNotNull { Regex("""libs\.([a-zA-Z0-9.]+)""").find(it)?.groupValues?.get(1) }
        assertTrue("found no dependencies — the scan is broken", used.size >= 10)

        val covered = Credits.libraries.flatMap { it.groups }
        used.forEach { alias ->
            val group = catalogue[alias] ?: error("$alias is not in the version catalogue")
            assertTrue(
                "$group ($alias) is in the build but not on the About page",
                covered.any { group == it || group.startsWith("$it.") },
            )
        }
    }

    @Test
    fun `every licence the page offers ships in full`() {
        LicenceText.entries.forEach { licence ->
            val file = File(assets, licence.asset)
            assertTrue("${licence.asset} is missing", file.isFile)
            assertTrue("${licence.asset} is empty", file.length() > 500)
        }
        assertTrue(File(assets, LicenceText.APACHE_2.asset).readText().contains("Version 2.0, January 2004"))
        assertTrue(File(assets, LicenceText.LUCIDE.asset).readText().contains("ISC License"))
        // Some of the arrows and chevrons are Feather's, which is MIT: the
        // notice for those has to travel with them.
        assertTrue(File(assets, LicenceText.LUCIDE.asset).readText().contains("Cole Bemis"))
        assertTrue(File(assets, LicenceText.OFL_DM_SANS.asset).readText().contains("SIL OPEN FONT LICENSE"))
    }

    @Test
    fun `the fonts on the page are the fonts in the app`() {
        val fonts = File("src/main/res/font").list().orEmpty().toSet()
        assertEquals(setOf("dm_sans_variable.ttf", "space_grotesk_variable.ttf"), fonts)
        val credited = Credits.fontsAndIcons.map { it.name }
        assertTrue("DM Sans" in credited && "Space Grotesk" in credited)
    }

    // --- the screens ------------------------------------------------------

    private var opened: LicenceText? = null

    private fun renderAbout() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) {
                AboutScreen(
                    onBack = {},
                    onOpenLicence = { opened = it },
                    versionName = "9.9",
                    versionCode = 42,
                )
            }
        }
    }

    @Test
    fun `the version comes from the build`() {
        renderAbout()
        composeRule.onNodeWithTag(TAG_ABOUT_VERSION).assertExists()
        composeRule.onNodeWithText("Version 9.9 (42)").assertExists()
    }

    @Test
    fun `the real build version is what the page shows by default`() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) { AboutScreen(onBack = {}, onOpenLicence = {}) }
        }
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .assertExists()
    }

    @Test
    fun `every service and library is listed`() {
        renderAbout()
        Credits.services.forEach {
            composeRule.onNodeWithTag("$TAG_ABOUT_SERVICE_PREFIX${it.name}").performScrollTo().assertExists()
        }
        (Credits.libraries + Credits.fontsAndIcons).forEach {
            composeRule.onNodeWithTag("$TAG_ABOUT_LIBRARY_PREFIX${it.name}").performScrollTo().assertExists()
        }
        composeRule.onNodeWithText("Weather data by Open-Meteo.com · CC BY 4.0").performScrollTo().assertExists()
        composeRule.onNodeWithText(Credits.DEVELOPER).assertExists()
    }

    @Test
    fun `tapping a library opens its licence`() {
        renderAbout()
        composeRule.onNodeWithTag("${TAG_ABOUT_LIBRARY_PREFIX}Lucide").performScrollTo().performClick()
        assertEquals(LicenceText.LUCIDE, opened)
        composeRule.onNodeWithTag("${TAG_ABOUT_LIBRARY_PREFIX}Retrofit").performScrollTo().performClick()
        assertEquals(LicenceText.APACHE_2, opened)
    }

    @Test
    fun `the licence page shows the bundled text`() {
        composeRule.setContent {
            WeatherQuipsTheme(darkTheme = false) { LicenceScreen(licence = LicenceText.APACHE_2, onBack = {}) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(TAG_LICENCE_TEXT))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TAG_LICENCE_TEXT)
            .assert(androidx.compose.ui.test.hasText("Version 2.0, January 2004", substring = true))
    }
}
