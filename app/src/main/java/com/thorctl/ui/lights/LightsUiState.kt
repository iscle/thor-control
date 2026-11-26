package com.thorctl.ui.lights

import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState

data class LightsUiState(val leds: LedState = LedState(), val target: StickTarget = StickTarget.BOTH) {
    /** Rainbow picks its own colors. */
    val colorEditable: Boolean get() = leds.mode != LedMode.RAINBOW

    val speedEditable: Boolean get() = leds.enabled && leds.mode != LedMode.SOLID

    /** The color the swatches and the hue slider show. */
    val color: Int get() = if (target == StickTarget.RIGHT) leds.rightColor else leds.left
}
