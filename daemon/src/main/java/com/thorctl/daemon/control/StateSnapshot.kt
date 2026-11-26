package com.thorctl.daemon.control

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.FanController
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The full state the app shows, assembled from the config and the hardware. */
@Inject
@SingleIn(DaemonScope::class)
class StateSnapshot(
    private val config: ConfigStore,
    private val displays: DisplayController,
    private val fan: FanController,
    private val game: GameMode,
    private val gamepad: Gamepad,
) {
    fun current(): DaemonState = DaemonState(
        fanProfile = config.fanProfile,
        profiles = config.allProfiles(),
        brightnessTop = displays.getBrightness(Screen.TOP),
        brightnessBottom = displays.getBrightness(Screen.BOTTOM),
        controller = config.controller,
        leds = config.leds,
        bottomOn = displays.bottomOn,
        topDisplayId = displays.topId,
        bottomDisplayId = displays.bottomId,
        fanKernelMode = fan.kernelMode,
        fanOverride = game.fanOverride.orEmpty(),
        game = game.state(),
        gamepad = gamepad.state,
    )
}
