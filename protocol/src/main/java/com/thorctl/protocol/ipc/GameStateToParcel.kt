package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GameState

fun GameState.toParcel() = GameStateParcel().also {
    it.enabled = enabled
    it.pkg = pkg
    it.boosted = boosted
    it.freedMb = freedMb
    it.options = options.toParcel()
}
