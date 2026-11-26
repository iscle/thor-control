package com.thorctl.protocol.model

import kotlin.math.roundToInt

/**
 * Fan speed in percent, as the curves and the app show it, against the fan's PWM (0..255). The
 * fan stalls below [MIN_SPIN_PWM], so 0 % is off and 1..100 % spread over the speeds it can
 * actually turn at: a curve's percentage is the speed the fan runs at, and the app shows the
 * same percentage back.
 */
object FanSpeed {
    const val MIN_SPIN_PWM = 40
    const val MAX_PWM = 255

    /** The PWM for [percent] (0..100): 0 is off, anything above starts at [MIN_SPIN_PWM]. */
    fun pwmFor(percent: Float): Int =
        if (percent <= 0f) 0 else (MIN_SPIN_PWM + (MAX_PWM - MIN_SPIN_PWM) * percent.coerceAtMost(100f) / 100f).roundToInt()

    /** The percentage a fan at [pwm] runs at; at least 1 % while it turns. */
    fun percentOf(pwm: Int): Int = when {
        pwm <= 0 -> 0
        else -> ((pwm - MIN_SPIN_PWM) * 100f / (MAX_PWM - MIN_SPIN_PWM)).roundToInt().coerceIn(1, 100)
    }
}
