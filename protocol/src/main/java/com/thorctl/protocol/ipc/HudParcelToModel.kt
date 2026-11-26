package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Screen

fun HudParcel.toModel(): HudEvent {
    val s = Screen.fromWire(screen) ?: Screen.TOP
    return when (kind) {
        "controller" -> HudEvent.Controller(ControllerTarget.fromWire(target) ?: ControllerTarget.FREE)
        "moved" -> HudEvent.Moved(pkg.orEmpty(), s)
        "brightness" -> HudEvent.Brightness(s, percent)
        "nothing" -> HudEvent.NothingToMove(s)
        "game" -> HudEvent.Game(GameHudState.fromWire(gameState), pkg.orEmpty(), freedMb)
        else -> HudEvent.Text(text.orEmpty())
    }
}
