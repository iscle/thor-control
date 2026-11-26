package com.thorctl.platform.overlay

import kotlinx.coroutines.flow.StateFlow

/** The quick menu on the bottom screen. */
interface OverlayMenu {
    /** The layer it was last shown in, or null if it couldn't be shown yet. */
    val shownAs: StateFlow<OverlayLayer?>

    fun show()

    fun hide()

    fun toggle()
}
