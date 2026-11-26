package com.thorctl.ui.status

import com.thorctl.platform.overlay.OverlayLayer

data class StatusUiState(
    val connected: Boolean = false,
    val accessibilityBound: Boolean = false,
    val menuShownAs: OverlayLayer? = null,
    val systemApp: Boolean = false,
    val persistent: Boolean = false,
)
