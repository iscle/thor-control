package com.thorctl.ui.game

import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.format.Format

data class GameUiState(
    val game: GameState = GameState(),
    val stats: Stats? = null,
    val label: String = "",
    val enabled: Boolean = false,
) {
    /** Title and line under it on the status tile. */
    val status: Pair<String, String>
        get() = when {
            !game.enabled -> "Off" to "The next game you open gets boosted once this is on"
            game.pkg.isEmpty() -> "Waiting for a game" to "Open a game on the top screen"
            !game.boosted -> "$label · paused" to "Boost comes back when the screen turns on"
            else -> label to buildString {
                append("Boosting")
                if (game.freedMb >= 100) append(" · ${Format.gigabytes(game.freedMb)} freed")
                stats?.let { append(" · ${Format.gigabytes(it.ramAvailMb)} free now") }
            }
        }
}
