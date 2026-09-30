package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import com.weatherquips.app.locale.AppLanguage
import com.weatherquips.app.text.QuipArrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language in the picker must be a whole translation: no key left in
 * English, no placeholder that would crash a format call, no quip list that
 * shrank on the way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizationTest {

    private val res = File("src/main/res")

    private val translations: List<AppLanguage> = AppLanguage.entries.filter { it.tag != null && it.tag != "en" }

    /** name → text for every translatable string, plural item and array item in a folder. */
    private fun entries(folder: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        File(res, folder).listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val node = strings.item(i) as org.w3c.dom.Element
                if (node.getAttribute("translatable") == "false") continue
                result["string/${node.getAttribute("name")}"] = node.textContent
            }
            listOf("string-array", "plurals").forEach { tag ->
                val groups = doc.getElementsByTagName(tag)
                for (i in 0 until groups.length) {
                    val group = groups.item(i) as org.w3c.dom.Element
                    val items = group.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as org.w3c.dom.Element
                        val key = item.getAttribute("quantity").ifEmpty { j.toString() }
                        result["$tag/${group.getAttribute("name")}/$key"] = item.textContent
                    }
                }
            }
        }
        return result
    }

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `every language in the picker has its own resources`() {
        translations.forEach { language ->
            assertTrue("no values-${language.tag} folder", File(res, "values-${language.tag}").isDirectory)
        }
    }

    @Test
    fun `translations cover every string, array item and plural`() {
        val english = entries("values")
        translations.forEach { language ->
            val translated = entries("values-${language.tag}")
            // Plurals legitimately differ by language; compare their names only.
            val englishKeys = english.keys.map { it.pluralName() }.toSet()
            val translatedKeys = translated.keys.map { it.pluralName() }.toSet()
            assertEquals("missing in ${language.tag}", emptySet<String>(), englishKeys - translatedKeys)
            assertEquals("extra in ${language.tag}", emptySet<String>(), translatedKeys - englishKeys)
        }
    }

    private fun String.pluralName() = if (startsWith("plurals/")) substringBeforeLast('/') else this

    @Test
    fun `placeholders survive translation, so no format call can crash`() {
        val english = entries("values")
        translations.forEach { language ->
            val translated = entries("values-${language.tag}")
            english.filterKeys { !it.startsWith("plurals/") }.forEach { (key, text) ->
                assertEquals(
                    "placeholders differ for $key in ${language.tag}",
                    placeholders(text),
                    placeholders(translated.getValue(key)),
                )
            }
        }
    }

    @Test
    fun `nothing is left untranslated by accident`() {
        val english = entries("values")
        translations.forEach { language ->
            val translated = entries("values-${language.tag}")
            // Short labels and pure formats may legitimately match ("wind",
            // "%1$d hPa"); a whole sentence matching is a missed translation.
            val same = english.filter { (key, text) ->
                text.split(' ').size >= 4 && translated[key] == text
            }
            assertTrue("left in English in ${language.tag}: ${same.keys}", same.isEmpty())
        }
    }

    @Test
    fun `dutch quips keep one highlighted word each`() {
        val dutch = TestResources.resources(Locale.forLanguageTag("nl"))
        WeatherCondition.entries.forEach { condition ->
            listOf(true, false).forEach { isDay ->
                dutch.getStringArray(QuipArrays.quotes(condition, isDay)).forEach { quote ->
                    val parsed = parseHighlightedQuote(quote)
                    assertNotNull("no highlight in: $quote", parsed)
                    assertTrue("stray markers in: $quote", !parsed!!.after.contains("**"))
                }
            }
        }
    }

    @Test
    fun `the resources actually switch with the locale`() {
        val english = TestResources.resources()
        val dutch = TestResources.resources(Locale.forLanguageTag("nl"))
        assertEquals("Settings", english.getString(R.string.settings))
        assertEquals("Instellingen", dutch.getString(R.string.settings))
        assertNotEquals(
            english.getStringArray(QuipArrays.quotes(WeatherCondition.RAINY, true)).first(),
            dutch.getStringArray(QuipArrays.quotes(WeatherCondition.RAINY, true)).first(),
        )
        // Brand and legal lines stay as they are.
        assertEquals(english.getString(R.string.app_name), dutch.getString(R.string.app_name))
        assertEquals(english.getString(R.string.map_attribution), dutch.getString(R.string.map_attribution))
    }

    @Test
    fun `the language list names itself in its own language`() {
        assertEquals("Nederlands", AppLanguage.DUTCH.endonym())
        assertEquals("English", AppLanguage.ENGLISH.endonym())
    }
}
