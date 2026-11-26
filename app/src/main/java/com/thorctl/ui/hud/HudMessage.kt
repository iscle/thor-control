package com.thorctl.ui.hud

/** [level] (0..1) shows a level bar instead of [text] (brightness). */
data class HudMessage(
    val icon: HudIcon,
    val text: String,
    val displayId: Int,
    val level: Float? = null,
    val stamp: Long = System.nanoTime(),
)
