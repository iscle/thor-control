package com.thorctl.ui

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Screen
import com.thorctl.testing.FakeAppInfo
import com.thorctl.ui.hud.HudIcon
import com.thorctl.ui.hud.HudMessageMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class HudMessageMapperTest {
    private val mapper = HudMessageMapper(FakeAppInfo(mapOf("com.game" to "Space Game", "com.blank" to "")))

    @Test
    fun shortcutConfirmations() {
        assertEquals("Controller → bottom screen", mapper.map(HudEvent.Controller(ControllerTarget.BOTTOM), 1).text)
        assertEquals("Controller follows focus", mapper.map(HudEvent.Controller(ControllerTarget.FREE), 0).text)
        val moved = mapper.map(HudEvent.Moved("com.game", Screen.TOP), 0)
        assertEquals(HudIcon.MOVE_UP, moved.icon)
        assertEquals("Space Game → top screen", moved.text)
        assertEquals("App → bottom screen", mapper.map(HudEvent.Moved("com.blank", Screen.BOTTOM), 0).text)
        assertEquals("No app on the bottom screen", mapper.map(HudEvent.NothingToMove(Screen.BOTTOM), 0).text)
    }

    @Test
    fun brightnessShowsALevel() {
        val message = mapper.map(HudEvent.Brightness(Screen.BOTTOM, 45), 2)
        assertEquals(0.45f, message.level)
        assertEquals(2, message.displayId)
    }

    @Test
    fun gameMode() {
        assertEquals("Game mode · Space Game · 1.5 GB freed", mapper.map(HudEvent.Game(GameHudState.STARTED, "com.game", 1536), 0).text)
        assertEquals("Game mode · Space Game", mapper.map(HudEvent.Game(GameHudState.STARTED, "com.game", 50), 0).text)
        assertEquals("Game mode off", mapper.map(HudEvent.Game(GameHudState.OFF), 0).text)
    }
}
