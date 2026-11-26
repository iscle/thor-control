package com.thorctl.ui.game

import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.format.Format

data class GameContent(
    val game: GameState,
    val stats: Stats,
    /** The boosted game's name, or "". */
    val label: String,
) {
    /** Title and line under it on the status tile. */
    val status: Pair<String, String>
        get() = when {
            !game.enabled -> "Off" to "The next game you open gets boosted once this is on"
            game.packageName.isEmpty() -> "Waiting for a game" to "Open a game on the top screen"
            !game.boosted -> "$label · paused" to "Boost comes back when the screen turns on"
            else -> label to buildString {
                append("Boosting")
                if (game.freedMb >= 100) append(" · ${Format.gigabytes(game.freedMb)} freed")
                append(" · ${Format.gigabytes(stats.ramAvailMb)} free now")
            }
        }
}
