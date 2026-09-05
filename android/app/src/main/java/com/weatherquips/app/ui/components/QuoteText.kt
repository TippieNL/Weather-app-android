package com.weatherquips.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.weatherquips.app.domain.quotes.parseHighlightedQuote
import com.weatherquips.app.ui.theme.LocalAccents

/**
 * Renders a quote with its `**highlighted**` word coloured, the native
 * equivalent of `renderQuoteWithHighlight()`.
 *
 * The highlight colour follows the temperature exactly as on the web: blue at
 * 15 °C or below, red above it. The word is also emphasised for screen readers,
 * so the meaning does not rely on colour alone.
 */
@Composable
fun QuoteText(
    quote: String,
    temperatureCelsius: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val accents = LocalAccents.current
    val highlightColor = if (temperatureCelsius <= 15) accents.cold else accents.hot

    val annotated: AnnotatedString = remember(quote, highlightColor) {
        val parsed = parseHighlightedQuote(quote)
        if (parsed == null) {
            AnnotatedString(quote)
        } else {
            buildAnnotatedString {
                append(parsed.before)
                withStyle(SpanStyle(color = highlightColor)) { append(parsed.highlight) }
                append(parsed.after)
            }
        }
    }

    Text(text = annotated, style = style, modifier = modifier)
}

/** Plain text of a quote with the markers stripped — used for accessibility labels. */
fun plainQuote(quote: String): String =
    parseHighlightedQuote(quote)?.let { it.before + it.highlight + it.after } ?: quote
