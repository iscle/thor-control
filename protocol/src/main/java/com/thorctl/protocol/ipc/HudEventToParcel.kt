package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.HudEvent

fun HudEvent.toParcel(displayId: Int) = HudParcel().also {
    it.displayId = displayId
    when (this) {
        is HudEvent.Controller -> {
            it.kind = "controller"
            it.target = target.wire
        }
        is HudEvent.Moved -> {
            it.kind = "moved"
            it.pkg = pkg
            it.screen = to.wire
        }
        is HudEvent.Brightness -> {
            it.kind = "brightness"
            it.screen = screen.wire
            it.percent = percent
        }
        is HudEvent.NothingToMove -> {
            it.kind = "nothing"
            it.screen = from.wire
        }
        is HudEvent.Game -> {
            it.kind = "game"
            it.gameState = state.wire
            it.pkg = pkg
            it.freedMb = freedMb
        }
        is HudEvent.Text -> {
            it.kind = "text"
            it.text = text
        }
    }
}
