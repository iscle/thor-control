package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState

fun LedStateParcel?.toModel(): LedState {
    val d = LedState()
    if (this == null) return d
    return LedState(
        enabled = enabled,
        mode = LedMode.fromWire(mode) ?: d.mode,
        left = left and 0xFFFFFF,
        right = right and 0xFFFFFF,
        sync = sync,
        brightness = brightness.coerceIn(0, 255),
        speed = speed.coerceIn(LedState.MIN_SPEED, LedState.MAX_SPEED),
        offWhenAsleep = offWhenAsleep,
    )
}
