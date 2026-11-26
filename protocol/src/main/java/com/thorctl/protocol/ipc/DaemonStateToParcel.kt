package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.DaemonState

fun DaemonState.toParcel() = DaemonStateParcel().also {
    it.fanProfile = fanProfile
    it.profiles = profiles.map { profile -> profile.toParcel() }.toTypedArray()
    it.brightnessTop = brightnessTop
    it.brightnessBottom = brightnessBottom
    it.controller = controller.wire
    it.leds = leds.toParcel()
    it.bottomOn = bottomOn
    it.topDisplayId = topDisplayId
    it.bottomDisplayId = bottomDisplayId
    it.fanKernelMode = fanKernelMode
    it.fanOverride = fanOverride
    it.game = game.toParcel()
    it.gamepad = gamepad.toParcel()
}
