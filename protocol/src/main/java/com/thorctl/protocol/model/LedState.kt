package com.thorctl.protocol.model

/** Joystick ring lights. Colors are 0xRRGGBB. */
data class LedState(
    val enabled: Boolean = true,
    val mode: LedMode = LedMode.SOLID,
    val left: Int = 0x4FC3F7,
    val right: Int = 0x4FC3F7,
    /** When true the right stick mirrors the left stick's color. */
    val sync: Boolean = true,
    /** 0..255 */
    val brightness: Int = 160,
    /** Animation speed multiplier, [MIN_SPEED]..[MAX_SPEED] */
    val speed: Float = 1f,
    /** Turn the lights off while the device is asleep. */
    val offWhenAsleep: Boolean = true,
) {
    val rightColor: Int get() = if (sync) left else right

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
    }
}
