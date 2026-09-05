package com.weatherquips.app.domain.quotes

import com.weatherquips.app.domain.model.WeatherCondition
import kotlin.random.Random

/** A quote plus its subtitle, exactly the pair the web API returned per request. */
data class Quip(val quote: String, val subtitle: String)

private data class QuipSet(val quotes: List<String>, val subtitles: List<String>)

/**
 * The Weather-Quips personality, ported verbatim from `server/routes.ts`.
 *
 * The web app regenerated a quote on *every* response (even cache hits), so the
 * same selection happens here on every successful load or refresh. `**word**`
 * markers are preserved and rendered as coloured text by the UI.
 */
object FunnyQuotes {

    private val quips: Map<WeatherCondition, QuipSet> = mapOf(
        WeatherCondition.CLEAR to QuipSet(
            quotes = listOf(
                "The sun is absolutely **roasting** the sky right now",
                "Not a single **cloud** dared to show up today",
                "The sky said **perfection** and really meant it",
                "Dangerously **gorgeous** out there, proceed with caution",
            ),
            subtitles = listOf(
                "The sun is trying too hard today.",
                "Perfect day to forget sunscreen and regret it.",
                "Nature's way of saying go outside, loser.",
                "Clear skies, unclear life choices.",
            ),
        ),
        WeatherCondition.CLOUDY to QuipSet(
            quotes = listOf(
                "The sky looks **depressed** and honestly, same",
                "Clouds rolled in like they **own** the damn place",
                "It's giving overcast **sadness** with no end in sight",
                "The sun called in **sick** and left us with this mess",
            ),
            subtitles = listOf(
                "Clouds everywhere. No escape.",
                "The sky needs therapy too.",
                "Nature's mood ring says 'blah'.",
                "Perfect weather for existential dread.",
            ),
        ),
        WeatherCondition.RAINY to QuipSet(
            quotes = listOf(
                "The sky is having a full **meltdown** on everyone",
                "It's pouring like the clouds are **heartbroken** again",
                "Everything outside is **soaked** beyond recognition",
                "Rain decided to make today absolutely **miserable**",
            ),
            subtitles = listOf(
                "Bring an umbrella or embrace the chaos.",
                "The sky is as emotional as you.",
                "Free shower from the clouds.",
                "Perfect excuse to cancel plans.",
            ),
        ),
        WeatherCondition.STORMY to QuipSet(
            quotes = listOf(
                "All hell is **breaking** loose out there right now",
                "Thunder is throwing a massive **tantrum** overhead",
                "The sky is in full **destruction** mode, stay inside",
                "Nature picked today to go completely **unhinged**",
            ),
            subtitles = listOf(
                "Maybe stay inside, just saying.",
                "The sky is having a breakdown.",
                "Thunder and drama everywhere.",
                "Nature's way of saying 'not today'.",
            ),
        ),
        WeatherCondition.SNOWY to QuipSet(
            quotes = listOf(
                "Everything is covered in **frozen** nonsense again",
                "Snow is falling like nature's **dandruff** everywhere",
                "The world turned into a **freezing** white nightmare",
                "Winter just showed up and chose **violence** today",
            ),
            subtitles = listOf(
                "Everything is cold and slippery.",
                "Time to pretend you like winter.",
                "Snowflakes like your excuses - everywhere.",
                "Bundle up or suffer.",
            ),
        ),
        WeatherCondition.FOGGY to QuipSet(
            quotes = listOf(
                "Can't see a damn **thing** in any direction",
                "The world is hiding behind a wall of **nothing**",
                "Visibility is absolutely **gone**, good luck out there",
                "Fog rolled in like it's auditioning for a **horror** movie",
            ),
            subtitles = listOf(
                "Vision? We don't know her.",
                "Silent Hill weather edition.",
                "The world is hiding from you.",
                "Perfect for dramatic walks.",
            ),
        ),
        WeatherCondition.WINDY to QuipSet(
            quotes = listOf(
                "The wind is personally **attacking** everyone outside",
                "Hair's getting absolutely **destroyed** the second you step out",
                "The air has gone completely **berserk** today",
                "Wind is blowing like it has a **vendetta** against you",
            ),
            subtitles = listOf(
                "Hold onto your stuff.",
                "Bad hair day guaranteed.",
                "The wind has personal beef with you.",
                "Nature's blow dryer on full blast.",
            ),
        ),
        WeatherCondition.HOT to QuipSet(
            quotes = listOf(
                "It's so hot the pavement is **melting** under your feet",
                "The sun is on a personal **warpath** against humanity",
                "Stepping outside feels like walking into an **inferno**",
                "Your skin will be **scorched** in approximately two minutes",
            ),
            subtitles = listOf(
                "Your AC is your best friend now.",
                "Humans were not designed for this.",
                "Walking outside is a mistake.",
                "Ice cream is a survival necessity.",
            ),
        ),
        WeatherCondition.COLD to QuipSet(
            quotes = listOf(
                "It's so cold your bones are **shivering** independently",
                "The air is **biting** through every layer you own",
                "Stepping outside feels like entering an **icebox** of regret",
                "Your face will be completely **numb** in about ten seconds",
            ),
            subtitles = listOf(
                "Layers on layers on layers.",
                "Your nose will be numb.",
                "Perfect weather to become a hermit.",
                "Even the cold is judging you.",
            ),
        ),
    )

    /** Every quote for a condition — used by tests and by the highlight parser. */
    fun quotesFor(condition: WeatherCondition): List<String> = quips.getValue(condition).quotes

    fun subtitlesFor(condition: WeatherCondition): List<String> = quips.getValue(condition).subtitles

    /** Random quote + subtitle, matching `getRandomQuote()` on the web server. */
    fun random(condition: WeatherCondition, random: Random = Random.Default): Quip {
        val set = quips.getValue(condition)
        return Quip(
            quote = set.quotes[random.nextInt(set.quotes.size)],
            subtitle = set.subtitles[random.nextInt(set.subtitles.size)],
        )
    }
}

/**
 * Splits a `**highlighted**` quote into its three parts. Returns `null` when the
 * quote has no marker, mirroring `renderQuoteWithHighlight()`'s early return.
 */
data class HighlightedQuote(val before: String, val highlight: String, val after: String)

private val HIGHLIGHT_REGEX = Regex("""^([\s\S]*?)\*\*([\s\S]+?)\*\*([\s\S]*)$""")

fun parseHighlightedQuote(quote: String): HighlightedQuote? {
    val match = HIGHLIGHT_REGEX.find(quote) ?: return null
    val (before, highlight, after) = match.destructured
    return HighlightedQuote(before, highlight, after)
}
