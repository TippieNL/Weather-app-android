package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.domain.model.WeatherIconKey
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.weatherquips.app.text.QuipArrays
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * After dark the app used to insist the sun was out: at half past ten a clear
 * sky was still being "roasted" by it. These pin the night sets down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
            assertEquals("night quotes for $condition", 4, TestResources.quotes(condition, isDay = false).size)
            assertEquals("night subtitles for $condition", 4, TestResources.subtitles(condition, isDay = false).size)
        }
    }

    @Test
    fun `night lines never claim the sun is out`() {
        WeatherCondition.entries.forEach { condition ->
            val lines = TestResources.quotes(condition, isDay = false) +
                TestResources.subtitles(condition, isDay = false) +
                TestResources.pokemonQuotes(condition, isDay = false)
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
            val lines = TestResources.quotes(condition, isDay = true) +
                TestResources.subtitles(condition, isDay = true) +
                TestResources.pokemonQuotes(condition, isDay = true)
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
            val day = TestResources.quotes(condition, isDay = true).toSet()
            val night = TestResources.quotes(condition, isDay = false).toSet()
            assertTrue("$condition reuses daytime quotes at night", day.intersect(night).isEmpty())
        }
    }

    @Test
    fun `night quotes keep the one highlighted word`() {
        WeatherCondition.entries.forEach { condition ->
            TestResources.quotes(condition, isDay = false).forEach { quote ->
                val parsed = parseHighlightedQuote(quote)
                assertNotNull("no highlight in: $quote", parsed)
                assertTrue("empty highlight in: $quote", parsed!!.highlight.isNotBlank())
            }
        }
    }

    @Test
    fun `the hour picks the list`() {
        // A reference is resolved against day or night by the forecast's own
        // flag, so the same seed lands in different sets either side of sunset.
        WeatherCondition.entries.forEach { condition ->
            assertTrue(QuipArrays.quotes(condition, isDay = true) != QuipArrays.quotes(condition, isDay = false))
            assertTrue(QuipArrays.subtitles(condition, isDay = true) != QuipArrays.subtitles(condition, isDay = false))
        }
    }

    @Test
    fun `the daytime lines are still the originals`() {
        // The web app's content must survive the addition untouched.
        assertTrue(
            TestResources.quotes(WeatherCondition.CLEAR, isDay = true)
                .contains("The sun is absolutely **roasting** the sky right now"),
        )
        assertTrue(
            TestResources.quotes(WeatherCondition.CLOUDY, isDay = true)
                .contains("Clouds rolled in like they **own** the damn place"),
        )
    }

    @Test
    fun `pallet town has night lines too`() {
        WeatherCondition.entries.forEach { condition ->
            val night = TestResources.pokemonQuotes(condition, isDay = false)
            assertTrue("no night quotes for $condition", night.isNotEmpty())
            assertTrue(
                "$condition reuses daytime Pokemon quotes",
                night.intersect(TestResources.pokemonQuotes(condition, isDay = true).toSet()).isEmpty(),
            )
            assertTrue(TestResources.pokemonSubtitles(condition, isDay = false).single().isNotBlank())
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
