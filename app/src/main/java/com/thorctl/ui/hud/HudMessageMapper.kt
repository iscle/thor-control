package com.thorctl.ui.hud

import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Screen
import com.thorctl.ui.format.Format
import dev.zacsweers.metro.Inject

/** Words and icon for an AYN shortcut's confirmation pill. */
@Inject
class HudMessageMapper(private val apps: AppInfoRepository) {
    fun map(event: HudEvent, displayId: Int): HudMessage = when (event) {
        is HudEvent.Controller -> HudMessage(
            HudIcon.CONTROLLER,
            if (event.target == ControllerTarget.FREE) "Controller follows focus" else "Controller → ${screen(event.target == ControllerTarget.BOTTOM)}",
            displayId,
        )
        is HudEvent.Moved -> HudMessage(
            if (event.to == Screen.BOTTOM) HudIcon.MOVE_DOWN else HudIcon.MOVE_UP,
            "${label(event.packageName)} → ${screen(event.to == Screen.BOTTOM)}",
            displayId,
        )
        is HudEvent.Brightness -> HudMessage(
            HudIcon.BRIGHTNESS,
            if (event.screen == Screen.BOTTOM) "Bottom screen brightness" else "Top screen brightness",
            displayId,
            level = event.percent / 100f,
        )
        is HudEvent.NothingToMove -> HudMessage(HudIcon.INFO, "No app on the ${screen(event.from == Screen.BOTTOM)}", displayId)
        is HudEvent.Game -> HudMessage(
            HudIcon.GAME,
            when (event.state) {
                GameHudState.ON -> "Game mode on"
                GameHudState.OFF -> "Game mode off"
                GameHudState.BOTTOM_OFF -> "Bottom screen off · AYN turns it on"
                GameHudState.STARTED -> "Game mode · ${label(event.packageName)}" +
                    if (event.freedMb >= 100) " · ${Format.gigabytes(event.freedMb)} freed" else ""
            },
            displayId,
        )
        is HudEvent.Text -> HudMessage(HudIcon.INFO, event.text, displayId)
    }

    private fun screen(bottom: Boolean) = if (bottom) "bottom screen" else "top screen"

    private fun label(packageName: String) = apps.label(packageName).ifBlank { "App" }
}
