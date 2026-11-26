package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.core.WakeListener
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.gamemode.GameMode
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.daemon.hardware.input.InputWatcher
import com.thorctl.daemon.ipc.AppConnectionListener
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.RecentsAction
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
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
 *  - apps visible on the other screen get a fresh picture of that screen when recents opens;
 *  - other apps use the previews the system persists in /data/system_ce/0/snapshots/.
 *
 * Events to the app:   RecentsOpened, RecentsThumb, RecentsButton, RecentsClosed, HomeAnimation
 * Commands from it:    RecentsOpen, RecentsHome, RecentsClose, RecentsRemove, RecentsClear,
 *                      HomeGo, OverlayDone
 */
@Inject
@SingleIn(DaemonScope::class)
@ContributesIntoSet(DaemonScope::class, binding = binding<WakeListener>())
@ContributesIntoSet(DaemonScope::class, binding = binding<AppConnectionListener>())
class Recents(
    displays: DisplayController,
    private val input: InputWatcher,
    private val link: ClientLink,
    inputService: InputService,
    taskService: TaskService,
    private val capturer: ScreenCapturer,
    private val files: ScreenshotFiles,
    private val guard: CaptureGuard,
    private val session: RecentsSession,
    /** Told when something else came to front, so it reacts straight away. */
    private val game: Lazy<GameMode>,
) : WakeListener, AppConnectionListener {
    private val thread = RecentsThread(Executors.newSingleThreadScheduledExecutor { Thread(it, "recents") })
    private val captureExecutor = Executors.newSingleThreadExecutor { Thread(it, "capture") }
    private val homeButton = HomeButton(
        displays, link, inputService, taskService, capturer, files, guard, thread, captureExecutor,
        isRecentsOpen = { isOpen },
        onHomeShown = ::onHomeShown,
    )
    private val navigator = RecentsNavigator(thread, ::send)

    /** Recents thread only (controller events are handed over to it, see [RecentsNavigator]). */
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

    /** Nobody sees recents with the screens off. */
    override fun onWakeChanged(awake: Boolean) {
        if (!awake && isOpen) close()
    }

    /** Recents is drawn by the app: without it there is nothing to show. */
    override fun onAppDisconnected() {
        if (isOpen) close()
    }

    /** AYN while open: same as B (back to where recents was opened from). */
    fun back() = thread.run { if (isOpen) send(RecentsAction.BACK) }

    fun handle(command: DaemonCommand) = thread.run {
        when (command) {
            is DaemonCommand.HomeGo -> {
                homeButton.goHome(command.seq)
                return@run
            }
            DaemonCommand.OverlayDone -> {
                homeButton.overlayDone()
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

        val grabbed = input.grabController(navigator.start(homeHeld = input.isDown(EvdevCodes.KEY_HOME)))
        Timber.i("recents: open on display $displayId from ${origin?.packageName ?: "home"}, ${cards.size} apps (controller ${if (grabbed) "grabbed" else "not grabbed"})")
        bumpIdle()
        capturePreviews(previews)
    }

    /** Captures the other screens and sends each app shown there that picture as its preview. */
    private fun capturePreviews(request: PreviewRequest) {
        val number = session.number
        for ((display, taskIds) in request.tasksByDisplay) {
            captureExecutor.execute {
                val file = files.preview(display)
                if (!capturer.capture(display, file)) return@execute
                thread.run {
                    if (!isOpen || session.number != number) return@run
                    for (id in taskIds) JpegFiles.read(file)?.let { link.broadcast(DaemonEvent.RecentsThumb(id, it)) }
                }
            }
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

    private fun send(action: RecentsAction) {
        bumpIdle()
        link.broadcast(DaemonEvent.RecentsButton(action))
    }

    companion object {
        const val IDLE_CLOSE_MS = 120_000L
        const val CLOSE_ANIMATION_MS = 2500L
    }
}
