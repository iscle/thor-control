package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.FanProfile

fun DaemonStateParcel.toModel() = DaemonState(
    fanProfile = fanProfile ?: FanProfile.AUTO_ID,
    profiles = profiles.orEmpty().filterNotNull().map { it.toModel() },
    brightnessTop = brightnessTop,
    brightnessBottom = brightnessBottom,
    controller = ControllerTarget.fromWire(controller) ?: ControllerTarget.FREE,
    leds = leds.toModel(),
    bottomOn = bottomOn,
    topDisplayId = topDisplayId,
    bottomDisplayId = bottomDisplayId,
    fanKernelMode = fanKernelMode,
    fanOverride = fanOverride.orEmpty(),
    game = game.toModel(),
    gamepad = gamepad.toModel(),
)
