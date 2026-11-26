package com.thorctl.protocol.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Same perceptual mapping SystemUI uses for its brightness slider (BrightnessUtils, HLG curve):
 * slider position 0..1 <-> linear brightness 0..1.
 */
object BrightnessCurve {
    /** Where the curve turns from square-law to logarithmic (BT.2100 HLG, as SystemUI scales it). */
    private const val KNEE = 0.5f
    private const val HLG_A = 0.17883277f
    private const val HLG_B = 0.28466892f
    private const val HLG_C = 0.55991073f
    /** The darkest the panels go: the slider never turns a screen off. */
    private const val MIN_LINEAR = 0.004f

    fun toLinear(position: Float): Float {
        val clamped = position.coerceIn(0f, 1f)
        val curve = if (clamped <= KNEE) (clamped / KNEE) * (clamped / KNEE) else exp((clamped - HLG_C) / HLG_A) + HLG_B
        return (curve / 12f).coerceIn(MIN_LINEAR, 1f)
    }

    fun toPosition(linear: Float): Float {
        val scaled = linear.coerceIn(0f, 1f) * 12f
        val curve = if (scaled <= 1f) KNEE * sqrt(scaled) else HLG_A * ln(scaled - HLG_B) + HLG_C
        return curve.coerceIn(0f, 1f)
    }

    /**
     * One brightness shortcut step: 10 % along the slider's curve in [direction] (−1 or +1),
     * snapped to whole 5 % positions. Returns the new slider position, 0..1.
     */
    fun step(linear: Float, direction: Int): Float {
        val position = toPosition(linear)
        return (((position * 20f).roundToInt() + direction * 2).coerceIn(0, 20)) / 20f
    }
}
