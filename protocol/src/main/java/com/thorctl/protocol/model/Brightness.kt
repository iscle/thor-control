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
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f
    private const val MIN = 0.004f

    fun toLinear(position: Float): Float {
        val v = position.coerceIn(0f, 1f)
        val ret = if (v <= R) (v / R) * (v / R) else exp((v - C) / A) + B
        return (ret / 12f).coerceIn(MIN, 1f)
    }

    fun toPosition(linear: Float): Float {
        val n = linear.coerceIn(0f, 1f) * 12f
        val ret = if (n <= 1f) R * sqrt(n) else A * ln(n - B) + C
        return ret.coerceIn(0f, 1f)
    }

    /**
     * One brightness shortcut step: 10 % along the slider's curve in [direction] (−1 or +1),
     * snapped to whole 5 % positions. Returns the new slider position, 0..1.
     */
    fun step(linear: Float, direction: Int): Float {
        val pos = toPosition(linear)
        return (((pos * 20f).roundToInt() + direction * 2).coerceIn(0, 20)) / 20f
    }
}
