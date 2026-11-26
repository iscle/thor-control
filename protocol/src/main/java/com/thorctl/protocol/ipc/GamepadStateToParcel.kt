package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GamepadState

fun GamepadState.toParcel() = GamepadStateParcel().also {
    it.on = on
    it.transport = transport.wire
    it.connected = connected
    it.host = host
    it.screensOff = screensOff
    it.dark = dark
    it.error = error
}
