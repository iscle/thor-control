package com.thorctl.daemon.feature

import android.app.ActivityManager.RunningAppProcessInfo
import android.content.pm.ApplicationInfo
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.FanController
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.PackageService
import com.thorctl.daemon.system.PowerService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.Proto
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.HudEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Game mode hands the device to one game:
 *
 *  - Memory: when the game is picked, every other app is closed (user apps are force-stopped,
 *    system apps lose their background processes) and memory is compacted. While it runs,
 *    cached background processes are cleared every ~10 s so nothing piles up again.
 *  - CPU: every cluster held at its top frequency. Core parking (core_ctl) is left alone: the
 *    vendor perf HAL saves and rewrites it on its own schedule, so values written there leak.
 *  - GPU: held at its top power level.
 *  - Fan: optionally switched to a stronger profile while boosting.
 *  - Bottom screen: optionally turned off while the game is in front on the top screen, so the
 *    launcher showing there stops drawing and releases its graphics memory (~120 MB for Cocoon).
 *    Skipped when the game itself shows something on the bottom screen; turning it back on (AYN
 *    tap) keeps it on for the rest of that game.
 *
 * Thermal mitigation still caps CPU/GPU on top of the floors written here (the kernel resolves
 * min > max in favour of max), so this can't cook the chip.
 *
 * "The game" is the user app in front when game mode turns on, or the next one opened after that.
 * It is protected (never closed, even while you switch away to look something up) until its
 * process ends, then game mode waits for the next game. Clocks drop back while the screen is off.
 *
 * Every sysfs value written is first saved to /dev/thorctl/game.orig; restore-kernel.sh puts
 * them back if the daemon dies.
 */
@Inject
@SingleIn(DaemonScope::class)
class GameMode(
    private val displays: DisplayController,
    private val packageService: PackageService,
    private val powerService: PowerService,
    private val taskService: TaskService,
    private val sys: Sys,
    private val notifier: DaemonNotifier,
    private val scheduler: ScheduledExecutorService,
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
                Timber.w(e, "game mode tick failed")
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private data class Knob(val path: String, val boost: String)

    @Volatile
    var options = GameOptions()
        private set

    @Volatile
    var enabled = false
        private set

    @Volatile
    var game: String? = null
        private set

    @Volatile
    var boosted = false
        private set

    @Volatile
    var freedMb = 0
        private set

    private var ticks = 0
    private var interactive = true
    private var applied: List<Knob> = emptyList()

    /** Bottom screen turned off by game mode (not by the user). */
    private var bottomOffByUs = false

    /** The user turned the bottom screen back on, or the game uses it: leave it on for this game. */
    private var keepBottomOn = false
    private var frontTicks = 0

    private val onFlag = File(FanController.STATE_DIRECTORY, "game.on")
    private val originalsFile = File(FanController.STATE_DIRECTORY, "game.orig")

    /** Fan profile to run instead of the selected one, while boosting. */
    val fanOverride: String? get() = if (boosted && options.fan.isNotEmpty()) options.fan else null

    fun state() = GameState(enabled, game.orEmpty(), boosted, freedMb, options)

    /** Daemon start: keep a game mode that was on earlier this boot, otherwise undo leftovers. */
    @Synchronized
    fun init(savedOptions: GameOptions) {
        options = savedOptions
        enabled = onFlag.exists()
        if (!enabled) restoreSaved()
        Timber.i("game mode: cpu knobs=${cpuKnobs.size} gpu knobs=${gpuKnobs.size} enabled=$enabled")
    }

    /** Turning on only arms it; the next [tick] picks the game (it may block while closing apps). */
    @Synchronized
    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) {
            onFlag.parentFile?.mkdirs()
            onFlag.writeText("1")
        } else {
            onFlag.delete()
            release()
            bottomOn()
            game = null
            freedMb = 0
            keepBottomOn = false
        }
        Timber.i("game mode ${if (on) "on" else "off"}")
    }

    @Synchronized
    fun setOptions(newOptions: GameOptions) {
        val old = options
        options = newOptions
        if (boosted && (old.cpu != newOptions.cpu || old.gpu != newOptions.gpu)) {
            release()
            apply()
        }
        if (!newOptions.bottomOff) bottomOn()
    }

    /** Called every couple of seconds, and right after game mode is turned on. */
    @Synchronized
    fun tick(interactive: Boolean) {
        this.interactive = interactive
        if (!enabled) return
        val before = state()
        val running = try {
            packageService.runningApps()
        } catch (e: Throwable) {
            Timber.w(e, "game mode: listing processes failed")
            return
        }

        val currentGame = game
        if (currentGame != null && running.none { currentGame in it.packageNames }) {
            Timber.i("game mode: $currentGame closed, waiting for the next game")
            game = null
            freedMb = 0
            keepBottomOn = false
        }
        if (game == null) {
            pickGame()?.let { packageName ->
                game = packageName
                Timber.i("game mode: game is $packageName")
                freedMb = if (options.closeApps) closeOthers(packageName, running) else 0
                onStarted(packageName, freedMb)
            }
        } else if (options.closeApps && ++ticks % TRIM_EVERY_TICKS == 0) {
            trimCached(running)
        }

        val want = game != null && interactive
        if (want && !boosted) apply() else if (!want && boosted) release() else if (boosted) reassert()
        val bottomChanged = updateBottom(running)
        if (state() != before || bottomChanged) onChanged()
    }

    // ---- Bottom screen --------------------------------------------------------------------

    /** Returns true when the bottom screen's power changed. */
    private fun updateBottom(running: List<AppProcess>): Boolean {
        // Asleep: leave everything as is (waking re-applies a forced-off bottom screen).
        if (!interactive) return false
        if (bottomOffByUs && displays.bottomOn) {
            Timber.i("game mode: bottom screen turned back on by the user, leaving it on for this game")
            bottomOffByUs = false
            keepBottomOn = true
        }
        val currentGame = game
        val inFront = currentGame != null && options.bottomOff && !keepBottomOn && displays.bottomId >= 0 &&
            (try { taskService.topAppTask(displays.topId)?.packageName } catch (_: Throwable) { null }) == currentGame
        if (!inFront) {
            frontTicks = 0
            return bottomOn()
        }
        // Wait a couple of seconds in front, so quick switches don't flash the bottom screen.
        if (bottomOffByUs || !displays.bottomOn || ++frontTicks < 2) return false
        if (gameOnBottom(currentGame!!, running)) {
            Timber.i("game mode: $currentGame uses the bottom screen, leaving it on")
            keepBottomOn = true
            return false
        }
        if (!displays.setBottomPower(false)) return false
        bottomOffByUs = true
        onBottomOff()
        return true
    }

    private fun bottomOn(): Boolean {
        if (!bottomOffByUs) return false
        bottomOffByUs = false
        return displays.setBottomPower(true)
    }

    /**
     * Whether the game shows anything on the bottom screen: an activity there, or a window such
     * as a Presentation (dual-screen emulators). Windows aren't tasks, so check SurfaceFlinger's
     * layer tree for layers owned by the game's uid under the bottom display. Only runs when the
     * bottom screen is about to be turned off.
     */
    private fun gameOnBottom(gamePackage: String, running: List<AppProcess>): Boolean {
        val bottomDisplay = displays.bottomId
        try {
            if (gamePackage in taskService.visiblePackages(bottomDisplay)) return true
        } catch (_: Throwable) {
        }
        val uids = running.filter { gamePackage in it.packageNames }.map { it.uid }.toSet()
        if (uids.isEmpty()) return false
        val dump = sys.sh("dumpsys SurfaceFlinger", 4000) ?: return false
        var inBottom = false
        for (line in dump.lineSequence()) {
            val displayMatch = SURFACE_FLINGER_DISPLAY.find(line)
            when {
                displayMatch != null -> inBottom = displayMatch.groupValues[1].toInt() == bottomDisplay
                SURFACE_FLINGER_ROOT_CHILD.containsMatchIn(line) || !line.startsWith(" ") -> inBottom = false
                inBottom -> SURFACE_FLINGER_UID.find(line)?.let { if (it.groupValues[1].toInt() in uids) return true }
            }
        }
        return false
    }

    // ---- Picking the game ------------------------------------------------------------------

    /** The user-installed app in front on the top screen, if any. */
    private fun pickGame(): String? {
        val topDisplay = displays.topId
        if (topDisplay < 0) return null
        val packageName = try {
            taskService.topAppTask(topDisplay)?.packageName
        } catch (_: Throwable) {
            null
        } ?: return null
        if (packageName == Proto.PACKAGE || packageName == packageService.homePackage()) return null
        val flags = packageService.appFlags(packageName) ?: return null
        // Settings, the browser, ... are not games; wait for a user-installed app.
        return if (flags and ApplicationInfo.FLAG_SYSTEM != 0) null else packageName
    }

    // ---- Memory ---------------------------------------------------------------------------

    /** Closes everything but the game and what keeps the device usable. Returns MB freed. */
    private fun closeOthers(game: String, running: List<AppProcess>): Int {
        val before = memAvailableMb()
        val keep = keepSet(game)
        val home = packageService.homePackage()
        var stopped = 0
        var trimmed = 0
        val packageNames = running.filter { it.uid >= FIRST_APP_UID }.flatMap { it.packageNames }.toSet() - keep
        for (packageName in packageNames) {
            try {
                val flags = packageService.appFlags(packageName) ?: continue
                if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_PERSISTENT) != 0 || packageName == home) {
                    // System apps: only their background processes, never the app itself.
                    packageService.killBackground(packageName)
                    trimmed++
                } else {
                    packageService.forceStop(packageName)
                    stopped++
                }
            } catch (e: Throwable) {
                Timber.w(e, "game mode: closing $packageName failed")
            }
        }
        sys.write("/proc/sys/vm/compact_memory", "1")
        Thread.sleep(1200) // let the processes die and the kernel settle before measuring
        val freed = (memAvailableMb() - before).coerceAtLeast(0)
        Timber.i("game mode: stopped $stopped apps, trimmed $trimmed system apps, ${freed} MB freed")
        return freed
    }

    /** Clears background processes that came back (or were opened and left) while playing. */
    private fun trimCached(running: List<AppProcess>) {
        val currentGame = game ?: return
        val packageNames = running
            .filter { it.uid >= FIRST_APP_UID && it.importance >= RunningAppProcessInfo.IMPORTANCE_CACHED && currentGame !in it.packageNames }
            .flatMap { it.packageNames }
            .toSet() - ALWAYS_KEEP - Proto.PACKAGE - currentGame
        if (packageNames.isEmpty()) return
        for (packageName in packageNames) {
            try {
                packageService.killBackground(packageName)
            } catch (_: Throwable) {
            }
        }
        Timber.i("game mode: cleared ${packageNames.size} background apps (${packageNames.joinToString(limit = 6)})")
    }

    private fun keepSet(game: String): Set<String> {
        val keep = HashSet(ALWAYS_KEEP)
        keep += game
        keep += Proto.PACKAGE
        for (displayId in intArrayOf(displays.topId, displays.bottomId)) {
            if (displayId < 0) continue
            try {
                keep += taskService.visiblePackages(displayId) // e.g. a guide open on the other screen
            } catch (_: Throwable) {
            }
        }
        sys.sh("cmd settings get secure default_input_method")?.let { keep += it.substringBefore('/') }
        sys.sh("cmd settings get secure enabled_accessibility_services")?.split(':')?.forEach { keep += it.substringBefore('/') }
        keep.removeAll { it.isBlank() || it == "null" }
        return keep
    }

    private fun memAvailableMb(): Int =
        sys.read("/proc/meminfo")?.lineSequence()?.firstOrNull { it.startsWith("MemAvailable:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.let { (it / 1024).toInt() } ?: 0

    // ---- Clocks ---------------------------------------------------------------------------

    /** Every cpufreq policy's minimum frequency raised to its maximum. */
    private val cpuKnobs: List<Knob> by lazy {
        File("/sys/devices/system/cpu/cpufreq").listFiles()
            ?.filter { it.name.matches(Regex("policy\\d+")) }?.sortedBy { it.name }
            ?.mapNotNull { policy -> sys.read("${policy.path}/cpuinfo_max_freq")?.let { Knob("${policy.path}/scaling_min_freq", it) } }
            ?: emptyList()
    }

    /** Lowest allowed GPU power level = highest allowed one (level 0 is the top clock). */
    private val gpuKnobs: List<Knob> by lazy {
        val gpuDirectory = "/sys/class/kgsl/kgsl-3d0"
        if (File("$gpuDirectory/min_pwrlevel").exists()) listOf(Knob("$gpuDirectory/min_pwrlevel", sys.read("$gpuDirectory/max_pwrlevel") ?: "0")) else emptyList()
    }

    /**
     * scaling_min_freq reads back the effective floor, which includes the vendor perf HAL's own
     * boosts (msm_performance). Saving that would make a temporary boost permanent, so restore the
     * floor the vendor boot script sets instead (or the hardware minimum).
     */
    private val vendorMinFrequencies: Map<String, String> by lazy {
        val floors = HashMap<String, String>()
        val floorPattern = Regex("""echo\s+(\d+)\s*>\s*(/sys/devices/system/cpu/cpufreq/policy\d+/scaling_min_freq)""")
        File("/vendor/bin").listFiles()?.filter { it.name.startsWith("init.kernel.post_boot") }?.sortedBy { it.name }
            ?.forEach { script -> sys.read(script.path)?.let { text -> floorPattern.findAll(text).forEach { floors[it.groupValues[2]] = it.groupValues[1] } } }
        floors
    }

    private fun originalOf(path: String): String? =
        if (path.endsWith("/scaling_min_freq")) {
            vendorMinFrequencies[path] ?: sys.read(path.replace("scaling_min_freq", "cpuinfo_min_freq"))
        } else {
            sys.read(path)
        }

    private fun apply() {
        val knobs = (if (options.cpu) cpuKnobs else emptyList()) + (if (options.gpu) gpuKnobs else emptyList())
        val originals = LinkedHashMap(loadSaved())
        for (knob in knobs) if (knob.path !in originals) originalOf(knob.path)?.let { originals[knob.path] = it }
        saveOriginals(originals)
        for (knob in knobs) sys.write(knob.path, knob.boost)
        applied = knobs
        boosted = true
        Timber.i("game mode: boost applied (cpu=${options.cpu} gpu=${options.gpu} fan=${options.fan.ifEmpty { "unchanged" }})")
    }

    private fun release() {
        if (!boosted && !originalsFile.exists()) return
        restoreSaved()
        applied = emptyList()
        boosted = false
        Timber.i("game mode: boost released")
    }

    /** Puts back a GPU floor something else lowered while we were boosting. */
    private fun reassert() {
        for (knob in applied) {
            val current = sys.read(knob.path) ?: continue
            // scaling_min_freq reads back the effective floor, which thermal limits may lower.
            if (current != knob.boost && !knob.path.endsWith("/scaling_min_freq")) sys.write(knob.path, knob.boost)
        }
    }

    private fun restoreSaved() {
        val saved = loadSaved()
        for ((path, value) in saved) sys.write(path, value)
        originalsFile.delete()
    }

    private fun loadSaved(): Map<String, String> {
        if (!originalsFile.exists()) return emptyMap()
        return originalsFile.readLines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
        }.toMap()
    }

    private fun saveOriginals(originals: Map<String, String>) {
        originalsFile.parentFile?.mkdirs()
        val tempFile = File(originalsFile.path + ".tmp")
        tempFile.writeText(originals.entries.joinToString("") { "${it.key} ${it.value}\n" })
        tempFile.renameTo(originalsFile)
    }

    companion object {
        const val FIRST_APP_UID = 10000
        private val SURFACE_FLINGER_DISPLAY = Regex("""^ [├└]─ Display (\d+) """)
        private val SURFACE_FLINGER_ROOT_CHILD = Regex("""^ [├└]─ """)
        private val SURFACE_FLINGER_UID = Regex("""\buid=(\d+)""")
        const val TRIM_EVERY_TICKS = 5

        /** Never closed: core system processes and the storage daemon games read from. */
        val ALWAYS_KEEP = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.bluetooth",
            "com.android.networkstack",
            "com.android.providers.media.module",
            "com.google.android.providers.media.module",
        )
    }
}
