package com.thorctl.ui.recents

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class Card(
    /** Task id, or a negative key for the home card / the app in a Home press. */
    val key: Int,
    val pkg: String,
    val label: String,
    val icon: ImageBitmap? = null,
    val image: ImageBitmap? = null,
    val onBottom: Boolean = false,
    /** Not running any more: opening it starts the app again (done by the app, not the daemon). */
    val running: Boolean = true,
    val isHome: Boolean = false,
) {
    companion object {
        const val HOME_KEY = -1000
        const val PRESS_APP_KEY = -1001
    }
}
