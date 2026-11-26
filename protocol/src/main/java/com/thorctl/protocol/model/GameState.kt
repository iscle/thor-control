package com.thorctl.protocol.model

data class GameState(
    val enabled: Boolean = false,
    /** Package being boosted, or empty while waiting for a game to open. */
    val pkg: String = "",
    /** Clocks/fan currently applied (false while waiting or with the screen off). */
    val boosted: Boolean = false,
    /** Memory freed by closing other apps when the current game started. */
    val freedMb: Int = 0,
    val options: GameOptions = GameOptions(),
)
