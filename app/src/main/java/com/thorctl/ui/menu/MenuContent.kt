package com.thorctl.ui.menu

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Stats

/** What the menu's main page shows while the daemon is connected. */
data class MenuContent(
    /** Slider positions (perceptual), 0..1. */
    val brightnessTop: Float,
    val brightnessBottom: Float,
    val fan: TileUi,
    /** Drives the fan glyph's speed. */
    val fanPercent: Int,
    val controller: TileUi,
    val lights: TileUi,
    /** Drives the lights glyph's colors. */
    val leds: LedState,
    val game: TileUi,
    val stats: Stats,
    val unit: TemperatureUnit,
)
