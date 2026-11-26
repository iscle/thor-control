package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Stats

object Samples {
    val custom = FanProfile("custom-1", "Custom 1", listOf(FanPoint(40f, 10f), FanPoint(70f, 80f)))

    val leds = LedState(enabled = false, mode = LedMode.BREATHE, left = 0x112233, right = 0x445566, sync = false, brightness = 99, speed = 2f, offWhenAsleep = false)

    val state = DaemonState(
        fanProfile = custom.id,
        profiles = FanProfile.BUILTINS + custom,
        brightnessTop = 0.25f,
        brightnessBottom = 0.75f,
        controller = ControllerTarget.BOTTOM,
        leds = leds,
        bottomOn = false,
        topDisplayId = 0,
        bottomDisplayId = 3,
        fanKernelMode = false,
        fanOverride = "max",
        game = GameState(true, "com.game", true, 1234, GameOptions(closeApps = false, cpu = true, gpu = false, fan = "", bottomOff = false)),
        gamepad = GamepadState(true, GamepadTransport.BLUETOOTH, true, "Mac", false, true, ""),
    )

    val stats = Stats(0.5f, 2400, listOf(1800, 2400, 3000), 61f, 0.3f, 680, 55f, 7600, 3100, 81, "Discharging", 6.2f, 0f, 33f, 140, 58f)
}
