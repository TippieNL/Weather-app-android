package com.weatherquips.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.weatherquips.app.ui.theme.Motion

/**
 * The detail panel's opening flourish, as progress from 0 to 1.
 *
 * A lambda rather than a value so every reader defers the read to layout or
 * drawing: the panel then animates without recomposing. Outside the home
 * screen — tests, previews — nothing provides it and everything is simply
 * fully drawn.
 */
val LocalPanelReveal = staticCompositionLocalOf<() -> Float> { { 1f } }

/** Long enough for the last row of the week to finish drawing. */
const val PANEL_REVEAL_MILLIS = 1_000

/**
 * One element's share of the reveal: it waits [delayMillis], then eases in
 * over [durationMillis].
 */
fun panelStage(reveal: Float, delayMillis: Int, durationMillis: Int = Motion.LONG - 80): Float {
    val elapsed = reveal * PANEL_REVEAL_MILLIS - delayMillis
    val linear = (elapsed / durationMillis).coerceIn(0f, 1f)
    return Motion.EmphasizedDecelerate.transform(linear)
}
