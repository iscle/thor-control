package com.thorctl.testing

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.LedState

val sampleState = DaemonState(
    fanProfile = FanProfile.SMART_ID,
    profiles = FanProfile.BUILTINS,
    brightnessTop = 0.5f,
    brightnessBottom = 0.3f,
    controller = ControllerTarget.FREE,
    leds = LedState(),
    bottomOn = true,
    topDisplayId = 0,
    bottomDisplayId = 2,
    fanKernelMode = false,
)
