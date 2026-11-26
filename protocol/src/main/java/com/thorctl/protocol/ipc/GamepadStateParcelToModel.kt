package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport

fun GamepadStateParcel?.toModel(): GamepadState {
    val d = GamepadState()
    if (this == null) return d
    return GamepadState(
        on = on,
        transport = GamepadTransport.fromWire(transport) ?: d.transport,
        connected = connected,
        host = host.orEmpty(),
        screensOff = screensOff,
        dark = dark,
        error = error.orEmpty(),
    )
}
