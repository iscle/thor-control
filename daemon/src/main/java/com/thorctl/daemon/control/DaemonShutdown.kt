package com.thorctl.daemon.control

import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import dev.zacsweers.metro.Inject
import timber.log.Timber
import kotlin.system.exitProcess

/** Ends the daemon for good, first undoing what would outlive it (game mode's boosts, the USB gadget). */
@Inject
class DaemonShutdown(private val game: GameMode, private val gamepad: Gamepad) {
    fun exit(reason: String): Nothing {
        Timber.i("exiting: $reason")
        game.setEnabled(false)
        gamepad.shutdown()
        exitProcess(0)
    }
}
