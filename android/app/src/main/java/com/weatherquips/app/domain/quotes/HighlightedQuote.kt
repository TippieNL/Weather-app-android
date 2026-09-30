package com.weatherquips.app.domain.quotes

// The quips themselves live in res/values/quotes.xml (and its translations),
// one array per condition and time of day, looked up by seed when drawn.

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
