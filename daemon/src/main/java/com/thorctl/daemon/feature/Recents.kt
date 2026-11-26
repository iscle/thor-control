package com.thorctl.daemon.feature

import android.os.SystemClock
import android.view.KeyEvent
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.ipc.BytesJpeg
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Jpeg
import com.thorctl.protocol.model.RecentTask
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The Home button's screens, replacing Trebuchet: recent apps (hold) and the animated way home
 * (short press). The app draws both; this side does the system work.
 *
 * Spatially, recent apps are a row of cards: oldest on the left, the app you came from next, and
 * the home screen always rightmost. Pressing Home "zooms out" of the app, slides one card right to
 * the home screen and zooms into it. For that the app needs pictures of exactly what is on screen:
 *  - the moment Home goes down, the screen is captured (screencap's JPEG mode, ~140 ms), before
 *    we know whether it becomes a press or a hold;
 *  - the home screen is captured whenever it is in front and nothing of ours covers it, and kept
 *    on disk (one per display), so it is ready when Home is pressed inside an app;
 *  - other apps use the previews the system persists in /data/system_ce/0/snapshots/.
 *
 * Events to the app:   RecentsOpened, RecentsThumb, RecentsKey, RecentsClosed, HomeAnimation
 * Commands from it:    RecentsOpen, RecentsHome, RecentsClose, RecentsRemove, RecentsClear,
 *                      HomeGo, OverlayDone
 *
 * Only running tasks are opened from here. Restoring a task that is no longer running counts as
 * a background activity start for this (root, windowless) process and Android blocks it; the app
 * launches those itself (it is a persistent process with a visible window, which is allowed).
 */
@Inject
@SingleIn(DaemonScope::class)
class Recents(
    private val displays: DisplayController,
    private val input: InputWatcher,
    private val link: ClientLink,
    private val system: SystemServices,
    private val sys: Sys,
    /** Told when something else came to front, so it reacts straight away. */
    private val game: Lazy<GameMode>,
) {
    private fun send(event: DaemonEvent) = link.broadcast(event)

    private fun hasClient() = link.appConnected

    /** Home was brought to front (game mode wants to know straight away). */
    private fun onHomeShown() = game.value.nudge()

    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "recents") }
    private val captureExecutor = Executors.newSingleThreadExecutor { Thread(it, "capture") }

    /** Display the recents screen is open on, or -1. */
    @Volatile
    var openOn = -1
        private set

    val isOpen: Boolean get() = openOn >= 0

    private var tasks: List<SystemServices.RecentTask> = emptyList()
    private var idle: ScheduledFuture<*>? = null

    /** One of our full-screen animations is (or may still be) on screen: don't capture. */
    @Volatile
    private var busyUntil = 0L

    @Volatile
    private var lastHud = 0L

    // ---- Home button ------------------------------------------------------------------------

    private class Press(val display: Int, val origin: SystemServices.TaskDesc?, val file: File, val shot: Future<Boolean>)

    @Volatile
    private var press: Press? = null
    private var homeSequence = 0
    private var pendingHome: Pair<Int, Int>? = null // seq to display

    /** Home went down: capture the screen now, in case this becomes the zoom-out of a press or hold. */
    fun homeDown(display: Int) {
        press = null
        if (display < 0 || isOpen || busy()) return
        val origin = try {
            system.topAppTask(display)
        } catch (_: Throwable) {
            null
        }
        // On the home screen the capture doubles as a fresh picture of it.
        val file = if (origin == null) homeFile(display) else File(DATA_DIRECTORY, "screen.jpg")
        press = Press(display, origin, file, captureExecutor.submit<Boolean> { capture(display, file) })
    }

    /**
     * Short press. Returns false when the caller should just send Home: already home, no app
     * connected, or no picture of the home screen yet (it is taken the next time home shows).
     */
    fun homeShort(display: Int): Boolean {
        val homePress = press ?: return false
        press = null
        if (homePress.display != display || homePress.origin == null || !hasClient()) return false
        val home = homeFile(display)
        if (!home.exists()) {
            captureHomeLater(display)
            return false
        }
        run {
            val captured = try {
                homePress.shot.get(800, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
            val sequence = ++homeSequence
            pendingHome = sequence to display
            if (!captured) {
                goHome(sequence)
                return@run
            }
            busy(4000)
            send(DaemonEvent.HomeAnimation(sequence, display, jpeg(homePress.file), jpeg(home)))
            // The app starts the switch once its overlay covers the screen; never wait long for it.
            executor.schedule({ goHome(sequence) }, 800, TimeUnit.MILLISECONDS)
        }
        return true
    }

    /** Hold: open recent apps (or close them if already open). */
    fun homeLong(display: Int) = run {
        if (isOpen) {
            close(notifyApp = true)
            return@run
        }
        val homePress = press?.takeIf { it.display == display }
        press = null
        val origin = homePress?.origin ?: try {
            system.topAppTask(display)
        } catch (_: Throwable) {
            null
        }
        val file = homePress?.file ?: (if (origin == null) homeFile(display) else File(DATA_DIRECTORY, "screen.jpg"))
        val captured = try {
            homePress?.shot?.get(800, TimeUnit.MILLISECONDS) ?: capture(display, file)
        } catch (_: Exception) {
            false
        }
        open(display, origin, if (captured) file else null)
    }

    private fun goHome(sequence: Int) {
        val (pendingSequence, display) = pendingHome ?: return
        if (pendingSequence != sequence) return
        pendingHome = null
        system.injectKey(KeyEvent.KEYCODE_HOME, display)
        onHomeShown()
        captureHomeLater(display)
    }

    // ---- Home screen pictures ---------------------------------------------------------------

    fun noteHud() {
        lastHud = SystemClock.elapsedRealtime()
    }

    private fun busy(): Boolean = isOpen || SystemClock.elapsedRealtime() < busyUntil

    private fun busy(durationMs: Long) {
        busyUntil = SystemClock.elapsedRealtime() + durationMs
    }

    private fun homeFile(display: Int) = File(DATA_DIRECTORY, "home-${physicalId(display)}.jpg")

    private fun physicalId(display: Int): String =
        (if (display == displays.bottomId) displays.bottomUniqueId else displays.topUniqueId).substringAfter("local:")

    /** Called every couple of seconds: keeps the top screen's home picture fresh while it shows. */
    fun maybeCaptureHome(interactive: Boolean) {
        val topDisplay = displays.topId
        if (!interactive || topDisplay < 0) return
        val homeImage = homeFile(topDisplay)
        if (homeImage.exists() && System.currentTimeMillis() - homeImage.lastModified() < HOME_MAX_AGE_MS) return
        captureHomeIfShowing(topDisplay)
    }

    private fun captureHomeLater(display: Int) {
        executor.schedule({ captureHomeIfShowing(display) }, 1500, TimeUnit.MILLISECONDS)
    }

    private fun captureHomeIfShowing(display: Int) {
        captureExecutor.execute {
            try {
                if (busy() || SystemClock.elapsedRealtime() - lastHud < 3000) return@execute
                if (system.topAppTask(display) != null) return@execute
                if (capture(display, homeFile(display))) Timber.i("recents: home screen of display $display captured")
            } catch (error: Throwable) {
                Timber.w(error, "home capture failed")
            }
        }
    }

    private fun capture(display: Int, file: File): Boolean {
        val physicalDisplayId = physicalId(display)
        if (physicalDisplayId.isEmpty()) return false
        file.parentFile?.mkdirs()
        val tempFile = File(file.path + ".tmp")
        tempFile.delete()
        sys.sh("screencap -d $physicalDisplayId -j ${tempFile.path}", 3000)
        if (!tempFile.exists() || tempFile.length() < 1000) {
            tempFile.delete()
            return false
        }
        return tempFile.renameTo(file)
    }

    private fun jpeg(file: File?): Jpeg? = try {
        if (file == null || !file.isFile || file.length() == 0L || file.length() > MAX_IMAGE_BYTES) null else BytesJpeg(file.readBytes())
    } catch (_: Exception) {
        null
    }

    // ---- Recents session --------------------------------------------------------------------

    fun close() = run { close(notifyApp = true) }

    /** AYN while open: same as B (back to where recents was opened from). */
    fun back() = run { if (isOpen) key("b") }

    fun handle(command: DaemonCommand) = run {
        when (command) {
            is DaemonCommand.HomeGo -> {
                goHome(command.seq)
                return@run
            }
            DaemonCommand.OverlayDone -> {
                busyUntil = 0
                return@run
            }
            else -> {}
        }
        if (!isOpen) return@run
        val display = openOn
        when (command) {
            is DaemonCommand.RecentsOpen -> {
                val id = command.taskId
                val task = tasks.firstOrNull { it.taskId == id }
                close(notifyApp = false)
                openTask(id, task, display)
                onHomeShown() // something else is in front now: let game mode look
            }
            DaemonCommand.RecentsHome -> {
                close(notifyApp = false)
                system.injectKey(KeyEvent.KEYCODE_HOME, display)
                onHomeShown()
                captureHomeLater(display)
            }
            is DaemonCommand.RecentsRemove -> {
                val id = command.taskId
                system.removeTask(id)
                tasks = tasks.filter { it.taskId != id }
                Timber.i("recents: closed task $id")
            }
            is DaemonCommand.RecentsClear -> {
                // No ids (the debug CLI): everything in the list.
                val ids = command.taskIds.ifEmpty { tasks.map { it.taskId } }
                ids.forEach { system.removeTask(it) }
                tasks = emptyList()
                Timber.i("recents: closed all (${ids.size})")
            }
            DaemonCommand.RecentsClose -> close(notifyApp = false)
            else -> {}
        }
    }

    private fun run(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (error: Throwable) {
                Timber.w(error, "recents failed")
            }
        }
    }

    private fun open(displayId: Int, origin: SystemServices.TaskDesc?, hero: File?) {
        val list = system.recentTasks(MAX_TASKS).toMutableList()
        // The app you came from is "the most recent" one, right next to the home screen.
        if (origin != null) {
            val index = list.indexOfFirst { it.taskId == origin.taskId }
            if (index > 0) list.add(0, list.removeAt(index))
        }
        // Apps visible elsewhere (the other screen) get a fresh preview; written within ~150 ms.
        val requested = System.currentTimeMillis()
        val others = list.filter { it.visible && it.taskId != origin?.taskId }
        others.forEach {
            try {
                system.takeSnapshot(it.taskId)
            } catch (error: Throwable) {
                Timber.w(error, "snapshot of task ${it.taskId} failed")
            }
        }
        tasks = list
        openOn = displayId
        val cards = list.map { task ->
            val thumb = if (task.taskId == origin?.taskId && hero != null) null else thumb(task.taskId)
            RecentTask(task.taskId, task.packageName, task.displayId, task.visible, task.running, thumb)
        }
        val homeImage = homeFile(displayId)
        send(
            DaemonEvent.RecentsOpened(
                displayId = displayId,
                fromHome = origin == null,
                originTask = origin?.taskId ?: -1,
                hero = if (origin != null) jpeg(hero) else null,
                home = jpeg(homeImage.takeIf { it.exists() }),
                tasks = cards,
            ),
        )

        ignoreHomeUp = input.isDown(InputWatcher.KEY_HOME)
        homeDown = false
        directions.clear()
        held = null
        val grabbed = input.grabController(::onPad)
        Timber.i("recents: open on display $displayId from ${origin?.packageName ?: "home"}, ${list.size} apps (controller ${if (grabbed) "grabbed" else "not grabbed"})")
        bumpIdle()
        if (others.isNotEmpty()) refreshThumbs(others.map { it.taskId }, requested, attempt = 0)
    }

    /** Sends the fresh previews of apps on the other screen once the system has written them. */
    private fun refreshThumbs(ids: List<Int>, since: Long, attempt: Int) {
        executor.schedule({
            if (!isOpen) return@schedule
            val pending = ids.filter { id ->
                val snapshot = system.snapshotFile(id)
                if (snapshot != null && snapshot.lastModified() >= since - 50) {
                    thumb(id)?.let { send(DaemonEvent.RecentsThumb(id, it)) }
                    false
                } else {
                    true
                }
            }
            if (pending.isNotEmpty() && attempt < 3) refreshThumbs(pending, since, attempt + 1)
        }, 200L + attempt * 250L, TimeUnit.MILLISECONDS)
    }

    private fun thumb(taskId: Int): Jpeg? = jpeg(system.snapshotFile(taskId))

    private fun openTask(id: Int, task: SystemServices.RecentTask?, display: Int) {
        try {
            if (task != null && task.displayId >= 0 && task.displayId != display) {
                // Running on the other screen: bring it over to where the user is looking.
                system.moveTaskToDisplay(id, display)
            } else {
                system.startFromRecents(id, display)
            }
            Timber.i("recents: opened task $id (${task?.packageName}) on display $display")
        } catch (error: Throwable) {
            Timber.w(error, "recents: open task $id failed")
        }
    }

    private fun close(notifyApp: Boolean) {
        if (!isOpen) return
        openOn = -1
        idle?.cancel(false)
        idle = null
        held = null
        repeat?.cancel(false)
        repeat = null
        input.releaseController()
        tasks = emptyList()
        // The app animates into the chosen screen and then removes its overlay.
        busy(2500)
        if (notifyApp) send(DaemonEvent.RecentsClosed)
    }

    /** A crashed or stuck app must not keep the controller forever. */
    private fun bumpIdle() {
        idle?.cancel(false)
        idle = executor.schedule({
            Timber.w("recents: idle for ${IDLE_CLOSE_MS / 1000}s, closing")
            close(notifyApp = true)
        }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
    }

    // ---- Controller -------------------------------------------------------------------------

    private var ignoreHomeUp = false
    private var homeDown = false

    /** Which inputs hold left (-1) or right (+1): d-pad hat, stick, d-pad keys, bumpers. */
    private val directions = HashMap<String, Int>()
    @Volatile
    private var held: Int? = null
    private var repeat: ScheduledFuture<*>? = null
    private var stickX = 0

    /** Raw evdev events from the grabbed controller (on the grab reader thread). */
    private fun onPad(type: Int, code: Int, value: Int) {
        when (type) {
            InputWatcher.EV_KEY -> onKey(code, value)
            InputWatcher.EV_ABS -> onAxis(code, value)
        }
    }

    private fun onKey(code: Int, value: Int) {
        when (code) {
            InputWatcher.KEY_HOME -> {
                // The hold that opened this screen ends here; a new press goes home.
                if (value == 0 && ignoreHomeUp) {
                    ignoreHomeUp = false
                    return
                }
                if (value == 1) homeDown = true
                if (value == 0 && homeDown) {
                    homeDown = false
                    key("home")
                }
                return
            }
            BTN_TL, InputWatcher.BTN_DPAD_LEFT, InputWatcher.KEY_LEFT -> setDir("k$code", if (value != 0) -1 else 0)
            BTN_TR, InputWatcher.BTN_DPAD_RIGHT, InputWatcher.KEY_RIGHT -> setDir("k$code", if (value != 0) 1 else 0)
        }
        if (value != 1) return
        when (code) {
            BTN_SOUTH, BTN_START -> key("a")
            InputWatcher.BTN_EAST, KEY_BACK -> key("b")
            InputWatcher.BTN_NORTH -> key("x")
            InputWatcher.BTN_WEST -> key("y")
        }
    }

    private fun onAxis(code: Int, value: Int) {
        when (code) {
            InputWatcher.ABS_HAT0X -> setDir("hat", value.coerceIn(-1, 1))
            ABS_X -> {
                stickX = when {
                    value <= -STICK_ON -> -1
                    value >= STICK_ON -> 1
                    kotlin.math.abs(value) < STICK_OFF -> 0
                    else -> stickX
                }
                setDir("stick", stickX)
            }
        }
    }

    /** Held left/right moves one card, then keeps moving after a short pause (like a key repeat). */
    private fun setDir(source: String, direction: Int) {
        if (direction == 0) directions.remove(source) else directions[source] = direction
        val now = directions.values.firstOrNull()
        if (now == held) return
        held = now
        repeat?.cancel(false)
        repeat = null
        if (now == null) return
        val keyName = if (now < 0) "left" else "right"
        key(keyName)
        repeat = executor.scheduleWithFixedDelay({ if (held == now) key(keyName) }, REPEAT_DELAY_MS, REPEAT_EVERY_MS, TimeUnit.MILLISECONDS)
    }

    private fun key(name: String) {
        bumpIdle()
        send(DaemonEvent.RecentsKey(name))
    }

    companion object {
        val DATA_DIRECTORY = File("/data/adb/thorctl")
        const val MAX_TASKS = 16
        const val MAX_IMAGE_BYTES = 1_200_000L
        const val HOME_MAX_AGE_MS = 60_000L
        const val IDLE_CLOSE_MS = 120_000L
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_EVERY_MS = 170L
        const val STICK_ON = 20000
        const val STICK_OFF = 8000
        const val BTN_SOUTH = 304 // "A"
        const val BTN_TL = 310
        const val BTN_TR = 311
        const val BTN_START = 315
        const val KEY_BACK = 158
        const val ABS_X = 0
    }
}
