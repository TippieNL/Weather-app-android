package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.domain.model.WeatherIconKey
import com.weatherquips.app.domain.quotes.FunnyQuotes
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The quips are the product. These tests guard the content and the selection. */
class QuotesTest {

    @Test
    fun `every condition has four quotes and four subtitles`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals(
                "quotes for $condition",
                4,
                FunnyQuotes.quotesFor(condition).size,
            )
            assertEquals(
                "subtitles for $condition",
                4,
                FunnyQuotes.subtitlesFor(condition).size,
            )
        }
    }

    @Test
    fun `every quote carries exactly one highlighted word`() {
        WeatherCondition.entries.forEach { condition ->
            FunnyQuotes.quotesFor(condition).forEach { quote ->
                val parsed = parseHighlightedQuote(quote)
                assertNotNull("no highlight in: $quote", parsed)
                assertTrue("empty highlight in: $quote", parsed!!.highlight.isNotBlank())
                assertTrue("stray markers in: $quote", !parsed.after.contains("**"))
            }
        }
    }

    @Test
    fun `known quotes are preserved verbatim`() {
        assertTrue(
            FunnyQuotes.quotesFor(WeatherCondition.CLOUDY)
                .contains("Clouds rolled in like they **own** the damn place"),
        )
        assertTrue(
            FunnyQuotes.subtitlesFor(WeatherCondition.CLOUDY)
                .contains("Clouds everywhere. No escape."),
        )
        assertTrue(
            FunnyQuotes.quotesFor(WeatherCondition.STORMY)
                .contains("All hell is **breaking** loose out there right now"),
        )
    }

    @Test
    fun `random selection stays inside the condition's own lists`() {
        val random = Random(42)
        repeat(50) {
            WeatherCondition.entries.forEach { condition ->
                val quip = FunnyQuotes.random(condition, random = random)
                assertTrue(quip.quote in FunnyQuotes.quotesFor(condition))
                assertTrue(quip.subtitle in FunnyQuotes.subtitlesFor(condition))
            }
        }
    }

    @Test
    fun `random selection is reproducible for a given seed`() {
        val first = FunnyQuotes.random(WeatherCondition.RAINY, random = Random(7))
        val second = FunnyQuotes.random(WeatherCondition.RAINY, random = Random(7))
        assertEquals(first, second)
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
            val quip = PokemonQuips.quote(condition, random = Random(1))
            assertTrue(quip.quote.isNotBlank())
            assertTrue(quip.subtitle.isNotBlank())
            assertTrue(quip.quote in PokemonQuips.quotesFor(condition))
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
