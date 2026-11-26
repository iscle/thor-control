package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GameState

fun GameState.toParcel() = GameStateParcel().also {
    it.enabled = enabled
    it.packageName = packageName
    it.boosted = boosted
    it.freedMb = freedMb
    it.options = options.toParcel()
}
