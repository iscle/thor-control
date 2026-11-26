package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.LedState

fun LedState.toParcel() = LedStateParcel().also {
    it.enabled = enabled
    it.mode = mode.wire
    it.left = left
    it.right = right
    it.sync = sync
    it.brightness = brightness
    it.speed = speed
    it.offWhenAsleep = offWhenAsleep
}
