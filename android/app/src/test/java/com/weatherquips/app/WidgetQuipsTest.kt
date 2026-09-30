package com.weatherquips.app

import android.content.res.Resources
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.locale.AppLanguage
import com.weatherquips.app.notifications.PrecipitationKind
import com.weatherquips.app.widget.Outlook
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.PrecipitationWidget
import com.weatherquips.app.widget.WidgetCopy
import com.weatherquips.app.widget.WidgetCopyWriter
import com.weatherquips.app.widget.WidgetMood
import com.weatherquips.app.widget.WidgetQuips
import com.weatherquips.app.widget.WidgetTextLayout
import com.weatherquips.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget's jokes: that there are enough of them, that they suit the hour
 * and the weather, that they fit, and that they do not repeat on a loop.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetQuipsTest {

    /** 4 September 2025, 15:33 UTC. */
    private val NOW = 1_757_000_000_000L
    private val HOUR = 3_600_000L

    private val languages: List<Pair<AppLanguage, Resources>> = AppLanguage.entries
        .filter { it.locale != null }
        .map { it to TestResources.resources(it.locale!!) }

    private fun Resources.pool(mood: WidgetMood, isDay: Boolean) =
        getStringArray(WidgetQuips.array(mood, isDay)).toList()

    // --- the pools --------------------------------------------------------

    @Test
    fun `every mood has a day and a night pool in every language`() {
        languages.forEach { (language, resources) ->
            WidgetMood.entries.forEach { mood ->
                listOf(true, false).forEach { isDay ->
                    val lines = resources.pool(mood, isDay)
                    assertTrue("$language $mood day=$isDay has only ${lines.size}", lines.size >= 4)
                    assertTrue("$language $mood day=$isDay has a blank", lines.all { it.isNotBlank() })
                    assertEquals("$language $mood day=$isDay repeats itself", lines.size, lines.toSet().size)
                }
            }
        }
    }

    @Test
    fun `day and night pools are actually different`() {
        languages.forEach { (language, resources) ->
            WidgetMood.entries.forEach { mood ->
                val shared = resources.pool(mood, true).intersect(resources.pool(mood, false).toSet())
                assertTrue("$language $mood shares $shared", shared.isEmpty())
            }
        }
    }

    @Test
    fun `dutch is a translation, not a copy of the english`() {
        val english = TestResources.resources()
        val dutch = languages.single { it.first == AppLanguage.DUTCH }.second
        WidgetMood.entries.forEach { mood ->
            val same = english.pool(mood, true).intersect(dutch.pool(mood, true).toSet())
            assertTrue("$mood left untranslated: $same", same.isEmpty())
        }
    }

    @Test
    fun `night lines never claim the sun is out, day lines never mention the dark`() {
        val english = TestResources.resources()
        val sunny = Regex("""\b(sunshine|sunny|the sun is|daylight|today)\b""", RegexOption.IGNORE_CASE)
        val dark = Regex("""\b(tonight|stars?|moon|overnight|bedtime)\b""", RegexOption.IGNORE_CASE)
        WidgetMood.entries.forEach { mood ->
            english.pool(mood, isDay = false).forEach {
                assertFalse("daytime wording at night ($mood): $it", sunny.containsMatchIn(it))
            }
            english.pool(mood, isDay = true).forEach {
                assertFalse("night wording in daylight ($mood): $it", dark.containsMatchIn(it))
            }
        }
    }

    @Test
    fun `wet pools do not joke about dry weather and vice versa`() {
        val english = TestResources.resources()
        val wetWords = Regex("""\b(rain\w*|umbrella|wet|pour\w*|drizzle|snow\w*|storm\w*)\b""", RegexOption.IGNORE_CASE)
        listOf(WidgetMood.CLEAR, WidgetMood.HOT).forEach { mood ->
            listOf(true, false).forEach { isDay ->
                english.pool(mood, isDay).forEach {
                    assertFalse("$mood talks about rain: $it", wetWords.containsMatchIn(it))
                }
            }
        }
    }

    @Test
    fun `the user's own examples made it in`() {
        val english = TestResources.resources()
        assertTrue("Don't forget your umbrella ☔" in english.pool(WidgetMood.RAIN_SOON, isDay = true))
        assertTrue("Perfect weather for doing absolutely nothing." in english.pool(WidgetMood.CLEAR, isDay = true))
        assertTrue("The sun is showing off today." in english.pool(WidgetMood.CLEAR, isDay = true))
    }

    // --- fitting on the widget --------------------------------------------

    @Test
    fun `every line fits in two lines, beside whatever the widget puts next to it`() {
        val narrow = PrecipitationWidget.SMALL_SIZE.width.value - 28f
        val wide = PrecipitationWidget.WIDE_SIZE.width.value - 28f
        languages.forEach { (language, resources) ->
            // The worst the meta line gets: a long place, a big rate, a day-old cache.
            val place = "'s-hertogenbos…"
            val rate = resources.getString(R.string.rate_mm_per_hour, "12")
            val age = resources.getString(R.string.widget_age_hours, 23)
            val layouts = listOf(
                narrow to listOf(null),
                narrow to listOf(age),
                wide to listOf(resources.getString(R.string.widget_meta, place, rate), rate, null),
                wide to listOf(resources.getString(R.string.widget_meta, place, age), age),
            )
            WidgetMood.entries.forEach { mood ->
                listOf(true, false).forEach { isDay ->
                    resources.pool(mood, isDay).forEach { line ->
                        layouts.forEach { (width, metas) ->
                            // As the writer hands it over: this line first, the pool behind it.
                            val pool = resources.pool(mood, isDay)
                            val copy = WidgetCopy("", line, pool - line)
                            val (quip, meta, _) = WidgetTextLayout.arrange(copy, metas, width, fontScale = 1f)
                            val room = width - (meta?.let { WidgetTextLayout.metaWidth(it, 1f) + 8f } ?: 0f)
                            assertTrue(
                                "$language '$quip' is cut off at ${width}dp beside '$meta'",
                                WidgetTextLayout.fullLines(quip, room, 1f) <= WidgetTextLayout.MAX_QUIP_LINES,
                            )
                            // Without a meta line in the way, every line gets shown as written.
                            if (metas == listOf(null)) assertEquals(line, quip)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the place gives way before the quip is cut, the age never does`() {
        val long = "Perfect weather for doing absolutely nothing."
        val wide = PrecipitationWidget.WIDE_SIZE.width.value - 28f
        val wet = WidgetTextLayout.arrange(
            WidgetCopy("", long), listOf("'s-hertogenbos… · 12 mm/h", "12 mm/h", null), wide, 1f,
        )
        assertEquals(long, wet.quip)
        assertEquals("12 mm/h", wet.meta)
        // Short quips keep the full line.
        val short = WidgetTextLayout.arrange(
            WidgetCopy("", "Walk fast."), listOf("assen · 1.8 mm/h", "1.8 mm/h", null), wide, 1f,
        )
        assertEquals("assen · 1.8 mm/h", short.meta)
        assertEquals(1, short.quipLines)
        // With no room at all the last choice stands, and for stale data that is the age.
        val stale = WidgetTextLayout.arrange(
            WidgetCopy("", long.repeat(4)), listOf("assen · 3h ago", "3h ago"), wide, 1f,
        )
        assertEquals("3h ago", stale.meta)
    }

    @Test
    fun `a joke too long for the room left is swapped, not cut`() {
        val narrow = PrecipitationWidget.SMALL_SIZE.width.value - 28f
        val copy = WidgetCopy(
            headline = "",
            quip = "Perfect weather for doing absolutely nothing.",
            understudies = listOf("Go outside. That's an order.", "Sunglasses. Now."),
        )
        val arranged = WidgetTextLayout.arrange(copy, listOf("23h ago"), narrow, 1f)
        assertEquals("23h ago", arranged.meta)
        assertNotEquals(copy.quip, arranged.quip)
        assertTrue(arranged.quip in copy.understudies)
    }

    @Test
    fun `short lines take one line, so the graph keeps its height`() {
        assertEquals(1, WidgetTextLayout.quipLines("Walk fast.", 152f, 1f))
        assertEquals(2, WidgetTextLayout.quipLines("Perfect weather for doing absolutely nothing.", 152f, 1f))
        // A larger system font needs the second line sooner.
        assertEquals(2, WidgetTextLayout.quipLines("The sun is showing off today.", 152f, 1.6f))
    }

    // --- choosing the pool ------------------------------------------------

    @Test
    fun `the mood follows what the headline says`() {
        assertEquals(WidgetMood.RAIN_NOW, WidgetMood.of(Outlook.FallingNow(PrecipitationKind.RAIN), WeatherCondition.RAINY))
        assertEquals(WidgetMood.SNOW_NOW, WidgetMood.of(Outlook.FallingNow(PrecipitationKind.SNOW), WeatherCondition.SNOWY))
        assertEquals(WidgetMood.STORM_NOW, WidgetMood.of(Outlook.FallingNow(PrecipitationKind.STORM), WeatherCondition.STORMY))
        assertEquals(
            WidgetMood.RAIN_SOON,
            WidgetMood.of(Outlook.StartsIn(PrecipitationKind.RAIN, "16:00", 30), WeatherCondition.CLOUDY),
        )
        assertEquals(
            WidgetMood.SNOW_SOON,
            WidgetMood.of(Outlook.StartsAt(PrecipitationKind.SNOW, "16:00", 70), WeatherCondition.COLD),
        )
        // Storms on the way still call for an umbrella.
        assertEquals(
            WidgetMood.RAIN_SOON,
            WidgetMood.of(Outlook.StartsIn(PrecipitationKind.STORM, "16:00", 30), WeatherCondition.HOT),
        )
    }

    @Test
    fun `a dry outlook jokes about the sky it actually has`() {
        mapOf(
            WeatherCondition.CLEAR to WidgetMood.CLEAR,
            WeatherCondition.CLOUDY to WidgetMood.CLOUDY,
            WeatherCondition.FOGGY to WidgetMood.FOG,
            WeatherCondition.WINDY to WidgetMood.WIND,
            WeatherCondition.HOT to WidgetMood.HOT,
            WeatherCondition.COLD to WidgetMood.COLD,
        ).forEach { (condition, mood) ->
            assertEquals(mood, WidgetMood.of(Outlook.Dry, condition))
        }
    }

    @Test
    fun `the writer takes day lines by day and night lines by night`() {
        val english = TestResources.resources()
        val writer = WidgetCopyWriter(english)
        val dry = PrecipitationOutlooks.from(clear(isDay = true), nowMillis = NOW)
        repeat(24) { hour ->
            val at = NOW + hour * HOUR
            assertTrue(writer.write(dry, at).quip in english.pool(WidgetMood.CLEAR, isDay = true))
        }
        val night = PrecipitationOutlooks.from(clear(isDay = false), nowMillis = NOW)
        repeat(24) { hour ->
            val at = NOW + hour * HOUR
            assertTrue(writer.write(night, at).quip in english.pool(WidgetMood.CLEAR, isDay = false))
        }
    }

    @Test
    fun `a cache that sat through sunset stops telling daytime jokes`() {
        // Fetched at 15:33 UTC in daylight, read five hours later in the dark
        // because the background refresh never ran.
        val cached = clear(isDay = true)
        assertTrue(PrecipitationOutlooks.from(cached, nowMillis = NOW + 30 * 60_000L).isDay)
        assertFalse(PrecipitationOutlooks.from(cached, nowMillis = NOW + 5 * HOUR).isDay)
        // And the other way round: a night-time fetch read the next morning.
        val overnight = clear(isDay = false)
        assertTrue(PrecipitationOutlooks.from(overnight, nowMillis = NOW + 17 * HOUR).isDay)
    }

    private fun clear(isDay: Boolean) = CachedWeather(
        data = TestWeather.sample(condition = WeatherCondition.CLEAR).copy(
            isDay = isDay,
            utcOffsetSeconds = 0,
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    // --- rotation ---------------------------------------------------------

    @Test
    fun `the quip holds for the hour, whatever the refresh schedule`() {
        val english = TestResources.resources()
        val topOfHour = WidgetQuips.hourOf(NOW) * HOUR
        val sameHour = (0 until 60 step 5).map { minute ->
            WidgetQuips.pick(english, WidgetMood.CLOUDY, isDay = true, nowMillis = topOfHour + minute * 60_000L)
        }
        assertEquals("changed mid-hour: $sameHour", 1, sameHour.toSet().size)
    }

    @Test
    fun `never the same line two hours running`() {
        (3..9).forEach { size ->
            (0 until 22).forEach { salt ->
                val start = WidgetQuips.hourOf(NOW)
                val picks = (start until start + 24 * 60).map { WidgetQuips.index(size, it, salt) }
                picks.zipWithNext().forEach { (a, b) ->
                    assertNotEquals("size $size salt $salt repeated $a", a, b)
                }
            }
        }
    }

    @Test
    fun `every line gets its turn before any line comes back`() {
        val size = 6
        val start = WidgetQuips.hourOf(NOW)
        // Rounds start at multiples of the pool size.
        val firstRound = Math.floorDiv(start, size.toLong()) * size + size
        repeat(20) { round ->
            val from = firstRound + round * size
            val dealt = (from until from + size).map { WidgetQuips.index(size, it, salt = 3) }
            assertEquals("round $round dealt $dealt", (0 until size).toSet(), dealt.toSet())
        }
    }

    @Test
    fun `the same hour is not the same joke every day`() {
        val size = 6
        val sameHour = (0 until 14).map { day ->
            WidgetQuips.index(size, WidgetQuips.hourOf(NOW) + day * 24L, salt = 5)
        }
        assertTrue("same line every day: $sameHour", sameHour.toSet().size >= 3)
    }

    @Test
    fun `tiny pools still work`() {
        assertEquals(0, WidgetQuips.index(1, 12345L, salt = 0))
        assertEquals(0, WidgetQuips.index(0, 12345L, salt = 0))
        val two = (0L until 10L).map { WidgetQuips.index(2, it, salt = 0) }
        two.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }
}
