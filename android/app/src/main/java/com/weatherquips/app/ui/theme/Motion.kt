package com.weatherquips.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion vocabulary.
 *
 * Every animation draws its timing from here so the app moves with one voice:
 * things arriving decelerate into place, things leaving accelerate away, and
 * anything a finger was holding is finished by a spring so it keeps the
 * momentum the finger gave it. The curves are Material 3's emphasized set.
 */
object Motion {

    /** For things arriving: fast out of the gate, a long gentle landing. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For things leaving: a slow start that commits and gets out of the way. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** For things moving within the screen. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 200
    const val MEDIUM = 350
    const val LONG = 500

    /** The gap between one element of a staggered entrance and the next. */
    const val STAGGER = 90

    /**
     * Finishing a gesture. Critically damped enough not to wobble a whole
     * screen, soft enough that a release mid-swipe glides rather than snaps.
     */
    val gestureSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset(1, 1),
    )

    /** A small pop for things that deserve a moment of attention. */
    fun <T> pop() = spring<T>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Returning something the finger let go of. */
    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
