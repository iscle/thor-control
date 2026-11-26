package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GameState

fun GameStateParcel?.toModel(): GameState {
    if (this == null) return GameState()
    return GameState(enabled = enabled, pkg = pkg.orEmpty(), boosted = boosted, freedMb = freedMb, options = options.toModel())
}
