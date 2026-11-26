package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Screen

fun HudParcel.toModel(): HudEvent {
    val onScreen = Screen.fromWire(screen) ?: Screen.TOP
    return when (kind) {
        "controller" -> HudEvent.Controller(ControllerTarget.fromWire(target) ?: ControllerTarget.FREE)
        "moved" -> HudEvent.Moved(packageName.orEmpty(), onScreen)
        "brightness" -> HudEvent.Brightness(onScreen, percent)
        "nothing" -> HudEvent.NothingToMove(onScreen)
        "game" -> HudEvent.Game(GameHudState.fromWire(gameState), packageName.orEmpty(), freedMb)
        else -> HudEvent.Text(text.orEmpty())
    }
}
