package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherIconKey
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.domain.quotes.FunnyQuotes
import com.weatherquips.app.domain.quotes.PokemonQuips
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * After dark the app used to insist the sun was out: at half past ten a clear
 * sky was still being "roasted" by it. These pin the night sets down.
 */
class NightQuotesTest {

    /** Phrases that claim the sun is up right now. */
    private val daytimeClaims = listOf(
        Regex("""\bsunshine\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsunny\b""", RegexOption.IGNORE_CASE),
        Regex("""the sun is\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdaylight\b""", RegexOption.IGNORE_CASE),
    )

    /** Phrases that only make sense after dark. */
    private val nighttimeClaims = listOf(
        Regex("""\btonight\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstars\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmoon\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbedtime\b""", RegexOption.IGNORE_CASE),
    )

    @Test
    fun `every condition has a full night set`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals("night quotes for $condition", 4, FunnyQuotes.quotesFor(condition, isDay = false).size)
            assertEquals("night subtitles for $condition", 4, FunnyQuotes.subtitlesFor(condition, isDay = false).size)
        }
    }

    @Test
    fun `night lines never claim the sun is out`() {
        WeatherCondition.entries.forEach { condition ->
            val lines = FunnyQuotes.quotesFor(condition, isDay = false) +
                FunnyQuotes.subtitlesFor(condition, isDay = false) +
                PokemonQuips.quotesFor(condition, isDay = false)
            lines.forEach { line ->
                daytimeClaims.forEach { claim ->
                    assertTrue("daytime wording at night ($condition): $line", !claim.containsMatchIn(line))
                }
            }
        }
    }

    @Test
    fun `day lines never talk about the night sky`() {
        WeatherCondition.entries.forEach { condition ->
            val lines = FunnyQuotes.quotesFor(condition, isDay = true) +
                FunnyQuotes.subtitlesFor(condition, isDay = true) +
                PokemonQuips.quotesFor(condition, isDay = true)
            lines.forEach { line ->
                nighttimeClaims.forEach { claim ->
                    assertTrue("night wording in daylight ($condition): $line", !claim.containsMatchIn(line))
                }
            }
        }
    }

    @Test
    fun `the two sets are actually different`() {
        WeatherCondition.entries.forEach { condition ->
            val day = FunnyQuotes.quotesFor(condition, isDay = true).toSet()
            val night = FunnyQuotes.quotesFor(condition, isDay = false).toSet()
            assertTrue("$condition reuses daytime quotes at night", day.intersect(night).isEmpty())
        }
    }

    @Test
    fun `night quotes keep the one highlighted word`() {
        WeatherCondition.entries.forEach { condition ->
            FunnyQuotes.quotesFor(condition, isDay = false).forEach { quote ->
                val parsed = parseHighlightedQuote(quote)
                assertNotNull("no highlight in: $quote", parsed)
                assertTrue("empty highlight in: $quote", parsed!!.highlight.isNotBlank())
            }
        }
    }

    @Test
    fun `picking a quote respects the hour`() {
        WeatherCondition.entries.forEach { condition ->
            repeat(10) {
                val night = FunnyQuotes.random(condition, isDay = false, random = Random(it))
                assertTrue(night.quote in FunnyQuotes.quotesFor(condition, isDay = false))
                assertTrue(night.subtitle in FunnyQuotes.subtitlesFor(condition, isDay = false))

                val day = FunnyQuotes.random(condition, isDay = true, random = Random(it))
                assertTrue(day.quote in FunnyQuotes.quotesFor(condition, isDay = true))
            }
        }
    }

    @Test
    fun `the daytime lines are still the originals`() {
        // The web app's content must survive the addition untouched.
        assertTrue(
            FunnyQuotes.quotesFor(WeatherCondition.CLEAR, isDay = true)
                .contains("The sun is absolutely **roasting** the sky right now"),
        )
        assertTrue(
            FunnyQuotes.quotesFor(WeatherCondition.CLOUDY, isDay = true)
                .contains("Clouds rolled in like they **own** the damn place"),
        )
    }

    @Test
    fun `pallet town has night lines too`() {
        WeatherCondition.entries.forEach { condition ->
            val night = PokemonQuips.quotesFor(condition, isDay = false)
            assertTrue("no night quotes for $condition", night.isNotEmpty())
            assertTrue(
                "$condition reuses daytime Pokemon quotes",
                night.intersect(PokemonQuips.quotesFor(condition, isDay = true).toSet()).isEmpty(),
            )
            val quip = PokemonQuips.quote(condition, isDay = false, random = Random(2))
            assertTrue(quip.quote in night)
            assertTrue(quip.subtitle.isNotBlank())
        }
    }

    @Test
    fun `storms and snow now get a moon after dark`() {
        assertEquals(WeatherIconKey.STORMY, weatherIconKey(WeatherCondition.STORMY, isDay = true))
        assertEquals(WeatherIconKey.STORMY_NIGHT, weatherIconKey(WeatherCondition.STORMY, isDay = false))
        assertEquals(WeatherIconKey.SNOWY, weatherIconKey(WeatherCondition.SNOWY, isDay = true))
        assertEquals(WeatherIconKey.SNOWY_NIGHT, weatherIconKey(WeatherCondition.SNOWY, isDay = false))
    }

    @Test
    fun `conditions with no sky in the picture look the same at any hour`() {
        // Fog replaces the sky; wind, heat and cold are about the air.
        listOf(
            WeatherCondition.FOGGY,
            WeatherCondition.WINDY,
            WeatherCondition.HOT,
            WeatherCondition.COLD,
        ).forEach { condition ->
            assertEquals(
                "$condition should not change with the sun",
                weatherIconKey(condition, isDay = true),
                weatherIconKey(condition, isDay = false),
            )
        }
    }

    @Test
    fun `every icon key resolves to a drawable`() {
        WeatherIconKey.entries.forEach { key ->
            assertTrue("no drawable for $key", com.weatherquips.app.ui.components.weatherIconRes(key) != 0)
        }
    }
}
