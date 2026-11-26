package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.TaskService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Turns the bottom screen off while the game is in front on the top screen (when the user asked
 * for it), so the launcher showing there stops drawing and releases its graphics memory (~120 MB
 * for Cocoon). Skipped when the game itself shows something on the bottom screen; turning it
 * back on (AYN tap) keeps it on for the rest of that game.
 */
@Inject
@SingleIn(DaemonScope::class)
class BottomScreenPolicy(
    private val displays: DisplayController,
    private val taskService: TaskService,
    private val sys: Sys,
) {
    /** Bottom screen turned off by game mode (not by the user). */
    private var offByUs = false

    /** The user turned the bottom screen back on, or the game uses it: leave it on for this game. */
    private var keepOn = false
    private var frontTicks = 0

    /** The game ended (or game mode turned off): the next game decides afresh. */
    fun gameEnded() {
        keepOn = false
    }

    /** Called on every game mode tick, with the game being played (if any). */
    fun update(game: String?, bottomOff: Boolean, running: List<AppProcess>, interactive: Boolean): BottomScreenChange {
        // Asleep: leave everything as is (waking re-applies a forced-off bottom screen).
        if (!interactive) return BottomScreenChange.UNCHANGED
        if (offByUs && displays.bottomOn) {
            Timber.i("game mode: bottom screen turned back on by the user, leaving it on for this game")
            offByUs = false
            keepOn = true
        }
        val inFront = game != null && bottomOff && !keepOn && displays.bottomId >= 0 &&
            (try { taskService.topAppTask(displays.topId)?.packageName } catch (_: Throwable) { null }) == game
        if (!inFront) {
            frontTicks = 0
            return if (turnBackOn()) BottomScreenChange.TURNED_ON else BottomScreenChange.UNCHANGED
        }
        // Wait a couple of seconds in front, so quick switches don't flash the bottom screen.
        if (offByUs || !displays.bottomOn || ++frontTicks < 2) return BottomScreenChange.UNCHANGED
        if (gameOnBottom(game, running)) {
            Timber.i("game mode: $game uses the bottom screen, leaving it on")
            keepOn = true
            return BottomScreenChange.UNCHANGED
        }
        if (!displays.setBottomPower(false)) return BottomScreenChange.UNCHANGED
        offByUs = true
        return BottomScreenChange.TURNED_OFF
    }

    /** Turns the bottom screen back on if game mode turned it off. Returns true when its power changed. */
    fun turnBackOn(): Boolean {
        if (!offByUs) return false
        offByUs = false
        return displays.setBottomPower(true)
    }

    /**
     * Whether the game shows anything on the bottom screen: an activity there, or a window such
     * as a Presentation (dual-screen emulators). Windows aren't tasks, so check SurfaceFlinger's
     * layer tree for layers owned by the game's uid under the bottom display. Only runs when the
     * bottom screen is about to be turned off.
     */
    private fun gameOnBottom(game: String, running: List<AppProcess>): Boolean {
        val bottomDisplay = displays.bottomId
        try {
            if (game in taskService.visiblePackages(bottomDisplay)) return true
        } catch (_: Throwable) {
        }
        val uids = running.filter { game in it.packageNames }.map { it.uid }.toSet()
        if (uids.isEmpty()) return false
        val dump = sys.sh("dumpsys SurfaceFlinger", 4000) ?: return false
        return SurfaceFlingerLayers.hasLayerOwnedBy(dump, bottomDisplay, uids)
    }
}
