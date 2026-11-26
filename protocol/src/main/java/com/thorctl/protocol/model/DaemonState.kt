package com.thorctl.protocol.model

/** Full control state, pushed by the daemon whenever anything changes. */
data class DaemonState(
    val fanProfile: String,
    val profiles: List<FanProfile>,
    val brightnessTop: Float,
    val brightnessBottom: Float,
    val controller: ControllerTarget,
    val leds: LedState,
    val bottomOn: Boolean,
    val topDisplayId: Int,
    val bottomDisplayId: Int,
    val fanKernelMode: Boolean,
    /** Profile game mode is running instead of [fanProfile], or empty. */
    val fanOverride: String = "",
    val game: GameState = GameState(),
    val gamepad: GamepadState = GamepadState(),
) {
    val activeProfile: FanProfile
        get() = profiles.firstOrNull { it.id == fanProfile } ?: FanProfile.BUILTINS.first()

    /** The profile actually driving the fan right now. */
    val runningProfile: FanProfile
        get() = profiles.firstOrNull { it.id == fanOverride } ?: activeProfile

    fun brightness(screen: Screen): Float = if (screen == Screen.TOP) brightnessTop else brightnessBottom
}
