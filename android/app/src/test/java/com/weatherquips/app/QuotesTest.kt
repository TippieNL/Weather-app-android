package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.domain.model.WeatherIconKey
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.weatherquips.app.text.QuipArrays
import com.weatherquips.app.text.QuipRef
import com.weatherquips.app.text.pick
import com.weatherquips.app.text.quipText
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The quips are the product. These tests guard the content and the selection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuotesTest {

    @Test
    fun `every condition has four quotes and four subtitles`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals("quotes for $condition", 4, TestResources.quotes(condition, isDay = true).size)
            assertEquals("subtitles for $condition", 4, TestResources.subtitles(condition, isDay = true).size)
        }
    }

    @Test
    fun `every quote carries exactly one highlighted word`() {
        WeatherCondition.entries.forEach { condition ->
            TestResources.quotes(condition, isDay = true).forEach { quote ->
                val parsed = parseHighlightedQuote(quote)
                assertNotNull("no highlight in: $quote", parsed)
                assertTrue("empty highlight in: $quote", parsed!!.highlight.isNotBlank())
                assertTrue("stray markers in: $quote", !parsed.after.contains("**"))
            }
        }
    }

    @Test
    fun `known quotes are preserved verbatim`() {
        // Moving the lines out of Kotlin must not have changed a character.
        assertTrue(
            TestResources.quotes(WeatherCondition.CLOUDY, isDay = true)
                .contains("Clouds rolled in like they **own** the damn place"),
        )
        assertTrue(
            TestResources.subtitles(WeatherCondition.CLOUDY, isDay = true)
                .contains("Clouds everywhere. No escape."),
        )
        assertTrue(
            TestResources.quotes(WeatherCondition.STORMY, isDay = true)
                .contains("All hell is **breaking** loose out there right now"),
        )
        // Apostrophes survive the XML escaping.
        assertTrue(
            TestResources.quotes(WeatherCondition.CLOUDY, isDay = true)
                .contains("It's giving overcast **sadness** with no end in sight"),
        )
    }

    @Test
    fun `a seed always lands inside the list, and every line is reachable`() {
        val lines = arrayOf("a", "b", "c", "d")
        assertEquals("a", lines.pick(0))
        assertEquals("b", lines.pick(5))
        // Negative seeds must not index out of bounds.
        assertEquals("d", lines.pick(-1))
        assertEquals(lines.toSet(), (0 until 40).map { lines.pick(it) }.toSet())
        assertEquals("", emptyArray<String>().pick(3))
    }

    @Test
    fun `a quip reference resolves to its own list`() {
        val resources = TestResources.resources()
        WeatherCondition.entries.forEach { condition ->
            val ref = QuipRef(QuipArrays.quotes(condition, isDay = true), seed = 3)
            assertTrue(resources.quipText(ref) in TestResources.quotes(condition, isDay = true))
        }
        assertEquals("", resources.quipText(QuipRef.NONE))
    }

    @Test
    fun `quotes without markers render unchanged`() {
        assertNull(parseHighlightedQuote("No markers here"))
    }

    @Test
    fun `highlight parsing splits before, word and after`() {
        val parsed = parseHighlightedQuote("The sky is **angry** today")
        assertEquals("The sky is ", parsed?.before)
        assertEquals("angry", parsed?.highlight)
        assertEquals(" today", parsed?.after)
    }

    @Test
    fun `pallet town detection is case and whitespace insensitive`() {
        assertTrue(PokemonQuips.isPalletTown("Pallet Town"))
        assertTrue(PokemonQuips.isPalletTown("  pallet town  "))
        assertTrue(PokemonQuips.isPalletTown("PALLET TOWN"))
        assertTrue(!PokemonQuips.isPalletTown("Pallet"))
        assertTrue(!PokemonQuips.isPalletTown("Amsterdam"))
    }

    @Test
    fun `pokemon mode has a quote for every condition`() {
        WeatherCondition.entries.forEach { condition ->
            listOf(true, false).forEach { isDay ->
                assertTrue(TestResources.pokemonQuotes(condition, isDay).all { it.isNotBlank() })
                assertTrue(TestResources.pokemonQuotes(condition, isDay).isNotEmpty())
                assertTrue(TestResources.pokemonSubtitles(condition, isDay).single().isNotBlank())
            }
        }
    }

    @Test
    fun `icon keys follow day and night only where the sky changes`() {
        assertEquals(WeatherIconKey.CLEAR_DAY, weatherIconKey(WeatherCondition.CLEAR, true))
        assertEquals(WeatherIconKey.CLEAR_NIGHT, weatherIconKey(WeatherCondition.CLEAR, false))
        assertEquals(WeatherIconKey.CLOUDY_NIGHT, weatherIconKey(WeatherCondition.CLOUDY, false))
        assertEquals(WeatherIconKey.RAINY_DAY, weatherIconKey(WeatherCondition.RAINY, true))
        // Temperature/air conditions read the same regardless of the sun.
        assertEquals(WeatherIconKey.HOT, weatherIconKey(WeatherCondition.HOT, false))
        assertEquals(WeatherIconKey.COLD, weatherIconKey(WeatherCondition.COLD, true))
        assertEquals(WeatherIconKey.STORMY, weatherIconKey(WeatherCondition.STORMY, true))
        assertEquals(WeatherIconKey.WINDY, weatherIconKey(WeatherCondition.WINDY, false))
    }
}
