package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.PackageService
import com.thorctl.daemon.system.PowerService
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.HudEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Game mode hands the device to one game:
 *
 *  - Memory: when the game is picked, every other app is closed (user apps are force-stopped,
 *    system apps lose their background processes) and memory is compacted. While it runs,
 *    cached background processes are cleared every ~10 s so nothing piles up again. See [AppCloser].
 *  - CPU: every cluster held at its top frequency. Core parking (core_ctl) is left alone: the
 *    vendor perf HAL saves and rewrites it on its own schedule, so values written there leak.
 *  - GPU: held at its top power level. CPU and GPU: see [ClockBoost].
 *  - Fan: optionally switched to a stronger profile while boosting ([fanOverride]).
 *  - Bottom screen: optionally turned off while the game is in front on the top screen, so the
 *    launcher showing there stops drawing and releases its graphics memory (~120 MB for Cocoon).
 *    Skipped when the game itself shows something on the bottom screen; turning it back on (AYN
 *    tap) keeps it on for the rest of that game. See [BottomScreenPolicy].
 *
 * Thermal mitigation still caps CPU/GPU on top of the floors written here (the kernel resolves
 * min > max in favour of max), so this can't cook the chip.
 *
 * "The game" is the user app in front when game mode turns on, or the next one opened after that
 * ([GamePicker]). It is protected (never closed, even while you switch away to look something up)
 * until its process ends, then game mode waits for the next game. Clocks drop back while the
 * screen is off.
 *
 * Every sysfs value written is first saved to /dev/thorctl/game.orig ([GameModeFiles]);
 * restore-kernel.sh puts them back if the daemon dies.
 */
@Inject
@SingleIn(DaemonScope::class)
class GameMode(
    private val displays: DisplayController,
    private val packageService: PackageService,
    private val powerService: PowerService,
    private val notifier: DaemonNotifier,
    private val scheduler: ScheduledExecutorService,
    private val files: GameModeFiles,
    private val picker: GamePicker,
    private val closer: AppCloser,
    private val boost: ClockBoost,
    private val bottomScreen: BottomScreenPolicy,
) {
    private fun onChanged() = notifier.stateChanged()

    private fun onStarted(packageName: String, freedMb: Int) =
        notifier.hud(HudEvent.Game(GameHudState.STARTED, packageName, freedMb), displays.topId)

    private fun onBottomOff() = notifier.hud(HudEvent.Game(GameHudState.BOTTOM_OFF), displays.topId)

    /** Leaving or returning to the game: react in [delayMs] instead of on the next tick. */
    fun nudge(delayMs: Long = 500) {
        if (!enabled) return
        scheduler.schedule({
            try {
                tick(powerService.isInteractive())
            } catch (e: Throwable) {
                // Task boundary: the scheduler would keep anything thrown in a future nobody reads.
                Timber.w(e, "game mode tick failed")
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    @Volatile
    var options = GameOptions()
        private set

    @Volatile
    var enabled = false
        private set

    @Volatile
    var game: String? = null
        private set

    val boosted: Boolean get() = boost.boosted

    @Volatile
    var freedMb = 0
        private set

    private var ticks = 0

    /** Fan profile to run instead of the selected one, while boosting. */
    val fanOverride: String? get() = if (boosted && options.fan.isNotEmpty()) options.fan else null

    fun state() = GameState(enabled, game.orEmpty(), boosted, freedMb, options)

    /** Daemon start: keep a game mode that was on earlier this boot, otherwise undo leftovers. */
    @Synchronized
    fun init(savedOptions: GameOptions) {
        options = savedOptions
        enabled = files.isMarkedOn()
        if (!enabled) boost.restoreSaved()
        Timber.i("game mode: cpu knobs=${boost.cpuKnobCount} gpu knobs=${boost.gpuKnobCount} enabled=$enabled")
    }

    /**
     * Turning on only arms it; the next [tick] picks the game. It stays off if game.on can't be
     * written, since a restarted daemon would not know it was on. Turning off always works (a
     * game.on that can't be deleted only means a restarted daemon turns it back on). Returns
     * whether game mode is on now.
     */
    @Synchronized
    fun setEnabled(on: Boolean): Boolean {
        if (on == enabled) return enabled
        if (!files.markOn(on)) {
            Timber.w("game mode: ${if (on) "marking it on failed, leaving it off" else "unmarking it failed"}")
            if (on) return false
        }
        enabled = on
        if (!on) {
            boost.release()
            bottomScreen.turnBackOn()
            game = null
            freedMb = 0
            bottomScreen.gameEnded()
        }
        Timber.i("game mode ${if (on) "on" else "off"}")
        return enabled
    }

    @Synchronized
    fun setOptions(newOptions: GameOptions) {
        val old = options
        options = newOptions
        if (boosted && (old.cpu != newOptions.cpu || old.gpu != newOptions.gpu)) {
            boost.release()
            boost.apply(options)
        }
        if (!newOptions.bottomOff) bottomScreen.turnBackOn()
    }

    /** Called every couple of seconds, and right after game mode is turned on. */
    @Synchronized
    fun tick(interactive: Boolean) {
        if (!enabled) {
            // Turning the bottom screen back on may have failed when game mode was turned off.
            if (bottomScreen.turnBackOn()) onChanged()
            return
        }
        val before = state()
        val running = try {
            packageService.runningApps()
        } catch (e: ReflectiveOperationException) {
            Timber.w(e, "game mode: listing processes failed")
            return
        }

        val currentGame = game
        if (currentGame != null && running.none { currentGame in it.packageNames }) {
            Timber.i("game mode: $currentGame closed, waiting for the next game")
            game = null
            freedMb = 0
            bottomScreen.gameEnded()
        }
        val playing = game
        if (playing == null) {
            picker.pick()?.let { packageName ->
                game = packageName
                Timber.i("game mode: game is $packageName")
                freedMb = 0
                if (options.closeApps) {
                    closer.closeOthers(packageName, running) { freed -> onAppsClosed(packageName, freed) }
                } else {
                    onStarted(packageName, 0)
                }
            }
        } else if (options.closeApps && ++ticks % TRIM_EVERY_TICKS == 0) {
            closer.trimCached(playing, running)
        }

        val want = game != null && interactive
        if (want && !boosted) boost.apply(options) else if (!want && boosted) boost.release() else if (boosted) boost.reassert()
        val bottomChange = bottomScreen.update(game, options.bottomOff, running, interactive)
        if (bottomChange == BottomScreenChange.TURNED_OFF) onBottomOff()
        if (state() != before || bottomChange != BottomScreenChange.UNCHANGED) onChanged()
    }

    /** [AppCloser] measured what closing the other apps freed: announce the game with it. */
    @Synchronized
    private fun onAppsClosed(packageName: String, freed: Int) {
        // Game mode was turned off, or the game closed, while the memory settled.
        if (game != packageName) return
        freedMb = freed
        onStarted(packageName, freed)
        onChanged()
    }

    companion object {
        const val TRIM_EVERY_TICKS = 5
    }
}
