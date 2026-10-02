package com.weatherquips.app.ui.precipitation

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.weatherquips.app.ui.theme.Motion

/**
 * What the radar is showing right now: two frames and how far the picture
 * has blended from one to the other.
 *
 * The data comes ten minutes apart, so between frames there is nothing true
 * to show — but stepping from one to the next reads as a slideshow. Blending
 * across most of each step lets the eye see the rain move. The view model
 * decides *which* frame is current; this decides how the picture gets there.
 */
@Stable
class RadarPlayhead(index: Int = 0) : RadarPicture {

    override var from by mutableIntStateOf(index)
        private set

    override var to by mutableIntStateOf(index)
        private set

    private val progress = Animatable(1f)

    override val blend: Float get() = progress.value

    /** The frame that dominates the picture, for the clock and the timeline. */
    val shown: Int get() = if (blend >= 0.5f) to else from

    /** How much of frame [index] is on screen, 0..1. */
    fun weight(index: Int): Float = when {
        from == to -> if (index == to) 1f else 0f
        index == to -> blend
        index == from -> 1f - blend
        else -> 0f
    }

    /** Holds the picture at a point of a blend, without animating. For tests. */
    @VisibleForTesting
    internal suspend fun showBlend(from: Int, to: Int, blend: Float) {
        this.from = from
        this.to = to
        progress.snapTo(blend)
    }

    /** Starts over at [index], with nothing mid-blend. */
    suspend fun reset(index: Int) {
        progress.snapTo(1f)
        from = index
        to = index
    }

    /**
     * Blends to [target]. A step to the next frame during playback takes
     * most of a step at an even pace, so the motion is continuous; anything
     * else — a tap on the timeline, the loop starting over — is a quick
     * dissolve. [onFrame] runs on every animation frame, to repaint the map.
     */
    suspend fun moveTo(target: Int, sequential: Boolean, onFrame: () -> Unit) {
        if (target == to && progress.value == 1f) return
        // Leave from whichever frame is mostly on screen, so an interrupted
        // blend carries on from what the eye sees rather than jumping back.
        from = if (progress.value >= 0.5f) to else from
        to = target
        progress.snapTo(0f)
        val (duration, easing) = if (sequential) STEP_BLEND_MILLIS to LinearEasing else JUMP_BLEND_MILLIS to JumpEasing
        progress.animateTo(1f, tween(duration, easing = easing)) { onFrame() }
        from = target
        onFrame()
    }

    companion object {
        /**
         * Most of a playback step: the picture is nearly always moving, with
         * a beat of rest on each real frame.
         */
        const val STEP_BLEND_MILLIS = 560
        const val JUMP_BLEND_MILLIS = Motion.SHORT + 40
        private val JumpEasing: Easing = Motion.Standard
    }
}

/**
 * Which frames the radar shows, and how far it has blended between them —
 * all the overlay needs to know, without it depending on Compose.
 */
interface RadarPicture {
    /** The frame being blended away from. */
    val from: Int

    /** The frame being blended towards; the current frame once [blend] is 1. */
    val to: Int

    /** 0 shows only [from], 1 only [to]. */
    val blend: Float
}

/**
 * How much of the outgoing and incoming frame to draw for one tile, given
 * which of them is loaded. A missing tile never leaves a hole: whatever is
 * there is shown in full.
 */
internal fun blendWeights(hasFrom: Boolean, hasTo: Boolean, blend: Float): Pair<Float, Float> {
    val t = blend.coerceIn(0f, 1f)
    return when {
        hasFrom && hasTo -> (1f - t) to t
        hasFrom -> 1f to 0f
        hasTo -> 0f to 1f
        else -> 0f to 0f
    }
}
