package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport

fun GamepadStateParcel?.toModel(): GamepadState {
    val defaults = GamepadState()
    if (this == null) return defaults
    return GamepadState(
        on = on,
        transport = GamepadTransport.fromWire(transport) ?: defaults.transport,
        connected = connected,
        host = host.orEmpty(),
        screensOff = screensOff,
        dark = dark,
        error = error.orEmpty(),
    )
}
