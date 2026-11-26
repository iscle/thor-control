package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture

/**
 * The Home button's screens, replacing Trebuchet: recent apps (hold) and the animated way home
 * (short press). The app draws both; this side does the system work, and this class ties the
 * pieces together: the Home button ([HomeButton]), the open recents screen ([RecentsSession]) and
 * the controller driving it ([RecentsNavigator]).
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
 */
@Inject
@SingleIn(DaemonScope::class)
class Recents(
    displays: DisplayController,
    private val input: InputWatcher,
    private val link: ClientLink,
    inputService: InputService,
    taskService: TaskService,
    capturer: ScreenCapturer,
    private val files: ScreenshotFiles,
    private val guard: CaptureGuard,
    private val session: RecentsSession,
    /** Told when something else came to front, so it reacts straight away. */
    private val game: Lazy<GameMode>,
) {
    private val thread = RecentsThread(Executors.newSingleThreadScheduledExecutor { Thread(it, "recents") })
    private val captureExecutor = Executors.newSingleThreadExecutor { Thread(it, "capture") }
    private val homeButton = HomeButton(
        displays, link, inputService, taskService, capturer, files, guard, thread, captureExecutor,
        isRecentsOpen = { isOpen },
        onHomeShown = ::onHomeShown,
    )
    private val navigator = RecentsNavigator(thread, ::key)
    private var idle: ScheduledFuture<*>? = null

    /** Display the recents screen is open on, or -1. */
    val openOn: Int get() = session.openOn

    val isOpen: Boolean get() = session.isOpen

    /** Home was brought to front (game mode wants to know straight away). */
    private fun onHomeShown() = game.value.nudge()

    // ---- Home button ------------------------------------------------------------------------

    /** Home went down: capture the screen now, in case this becomes the zoom-out of a press or hold. */
    fun homeDown(display: Int) = homeButton.down(display)

    /**
     * Short press. Returns false when the caller should just send Home: already home, no app
     * connected, or no picture of the home screen yet (it is taken the next time home shows).
     */
    fun homeShort(display: Int): Boolean = homeButton.short(display)

    /** Hold: open recent apps (or close them if already open). */
    fun homeLong(display: Int) = thread.run {
        if (isOpen) {
            close(notifyApp = true)
            return@run
        }
        open(display, homeButton.hold(display))
    }

    fun noteHud() = guard.noteHud()

    /** Called every couple of seconds: keeps the top screen's home picture fresh while it shows. */
    fun maybeCaptureHome(interactive: Boolean) = homeButton.maybeCaptureHome(interactive)

    // ---- Recents session --------------------------------------------------------------------

    fun close() = thread.run { close(notifyApp = true) }

    /** AYN while open: same as B (back to where recents was opened from). */
    fun back() = thread.run { if (isOpen) key("b") }

    fun handle(command: DaemonCommand) = thread.run {
        when (command) {
            is DaemonCommand.HomeGo -> {
                homeButton.goHome(command.seq)
                return@run
            }
            DaemonCommand.OverlayDone -> {
                guard.overlayDone()
                return@run
            }
            else -> {}
        }
        if (!isOpen) return@run
        val display = openOn
        when (command) {
            is DaemonCommand.RecentsOpen -> {
                val id = command.taskId
                val task = session.task(id)
                close(notifyApp = false)
                session.launch(id, task, display)
                onHomeShown() // something else is in front now: let game mode look
            }
            DaemonCommand.RecentsHome -> {
                close(notifyApp = false)
                homeButton.sendHome(display)
            }
            is DaemonCommand.RecentsRemove -> session.remove(command.taskId)
            is DaemonCommand.RecentsClear -> session.clear(command.taskIds)
            DaemonCommand.RecentsClose -> close(notifyApp = false)
            else -> {}
        }
    }

    private fun open(displayId: Int, shot: HomeShot) {
        val origin = shot.origin
        val previews = session.open(displayId, origin)
        val cards = session.cards(origin, heroCaptured = shot.picture != null)
        link.broadcast(
            DaemonEvent.RecentsOpened(
                displayId = displayId,
                fromHome = origin == null,
                originTask = origin?.taskId ?: -1,
                hero = if (origin != null) JpegFiles.read(shot.picture) else null,
                home = JpegFiles.read(files.home(displayId).takeIf { it.exists() }),
                tasks = cards,
            ),
        )

        navigator.start(homeHeld = input.isDown(InputWatcher.KEY_HOME))
        val grabbed = input.grabController(navigator::onEvent)
        Timber.i("recents: open on display $displayId from ${origin?.packageName ?: "home"}, ${cards.size} apps (controller ${if (grabbed) "grabbed" else "not grabbed"})")
        bumpIdle()
        if (previews.taskIds.isNotEmpty()) refreshThumbs(previews.taskIds, previews.since, attempt = 0)
    }

    /** Sends the fresh previews of apps on the other screen once the system has written them. */
    private fun refreshThumbs(ids: List<Int>, since: Long, attempt: Int) {
        thread.schedule(THUMB_FIRST_CHECK_MS + attempt * THUMB_CHECK_STEP_MS) {
            if (!isOpen) return@schedule
            val pending = ids.filter { id ->
                if (session.previewWrittenSince(id, since)) {
                    session.thumb(id)?.let { link.broadcast(DaemonEvent.RecentsThumb(id, it)) }
                    false
                } else {
                    true
                }
            }
            if (pending.isNotEmpty() && attempt < THUMB_RETRIES) refreshThumbs(pending, since, attempt + 1)
        }
    }

    private fun close(notifyApp: Boolean) {
        if (!session.close()) return
        idle?.cancel(false)
        idle = null
        navigator.stop()
        input.releaseController()
        // The app animates into the chosen screen and then removes its overlay.
        guard.overlayFor(CLOSE_ANIMATION_MS)
        if (notifyApp) link.broadcast(DaemonEvent.RecentsClosed)
    }

    /** A crashed or stuck app must not keep the controller forever. */
    private fun bumpIdle() {
        idle?.cancel(false)
        idle = thread.schedule(IDLE_CLOSE_MS) {
            Timber.w("recents: idle for ${IDLE_CLOSE_MS / 1000}s, closing")
            close(notifyApp = true)
        }
    }

    private fun key(name: String) {
        bumpIdle()
        link.broadcast(DaemonEvent.RecentsKey(name))
    }

    companion object {
        const val IDLE_CLOSE_MS = 120_000L
        const val CLOSE_ANIMATION_MS = 2500L
        const val THUMB_FIRST_CHECK_MS = 200L
        const val THUMB_CHECK_STEP_MS = 250L
        const val THUMB_RETRIES = 3
    }
}
