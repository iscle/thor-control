package com.thorctl.daemon.feature.recents

import android.view.KeyEvent
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.model.DaemonEvent
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * The Home button outside recents, and the pictures of the screen it needs:
 *  - the moment Home goes down, the screen is captured, before we know whether it becomes a press
 *    or a hold;
 *  - a short press inside an app has the app animate from that picture to the home screen's, and
 *    Home is sent once the animation covers the screen;
 *  - the home screen is captured whenever it is in front and nothing of ours covers it, so it is
 *    ready when Home is pressed inside an app.
 *
 * Captures run on [captureExecutor], timers and the animation on [thread]; [down] and [short] are
 * called on the input thread.
 */
class HomeButton(
    private val displays: DisplayController,
    private val link: ClientLink,
    private val inputService: InputService,
    private val taskService: TaskService,
    private val capturer: ScreenCapturer,
    private val files: ScreenshotFiles,
    private val guard: CaptureGuard,
    private val thread: RecentsThread,
    private val captureExecutor: ExecutorService,
    private val isRecentsOpen: () -> Boolean,
    /** Home was brought to front. */
    private val onHomeShown: () -> Unit,
) {
    @Volatile
    private var press: HomePress? = null
    private val pendingHome = PendingHome()

    /** One of our screens is (or may still be) on screen: don't capture. */
    private fun busy(): Boolean = isRecentsOpen() || guard.overlayShowing

    /** Home went down: capture the screen now, in case this becomes the zoom-out of a press or hold. */
    fun down(display: Int) {
        press = null
        if (display < 0 || busy()) return
        val origin = topAppTask(display)
        val file = files.forPress(display, origin)
        press = HomePress(display, origin, file, captureExecutor.submit<Boolean> { capturer.capture(display, file) })
    }

    /**
     * Short press. Returns false when the caller should just send Home: already home, no app
     * connected, or no picture of the home screen yet (it is taken the next time home shows).
     */
    fun short(display: Int): Boolean {
        val homePress = press ?: return false
        press = null
        if (homePress.display != display || homePress.origin == null || !link.appConnected) return false
        val home = files.home(display)
        if (!home.exists()) {
            captureHomeLater(display)
            return false
        }
        thread.run { animate(homePress, home) }
        return true
    }

    /** Hold (on the recents thread): the screen to open recents from, with the press's picture or one taken now. */
    fun hold(display: Int): HomeShot {
        val homePress = press?.takeIf { it.display == display }
        press = null
        val origin = homePress?.origin ?: topAppTask(display)
        val file = homePress?.file ?: files.forPress(display, origin)
        val captured = homePress?.captured() ?: try {
            capturer.capture(display, file)
        } catch (_: Exception) {
            false
        }
        return HomeShot(origin, if (captured) file else null)
    }

    /** The app's overlay covers the screen for animation [sequence]: switch now (on the recents thread). */
    fun goHome(sequence: Int) {
        val display = pendingHome.take(sequence) ?: return
        sendHome(display)
    }

    /** Sends Home to [display] and pictures the home screen once it shows (on the recents thread). */
    fun sendHome(display: Int) {
        inputService.injectKey(KeyEvent.KEYCODE_HOME, display)
        onHomeShown()
        captureHomeLater(display)
    }

    /** Called every couple of seconds: keeps the top screen's home picture fresh while it shows. */
    fun maybeCaptureHome(interactive: Boolean) {
        val topDisplay = displays.topId
        if (!interactive || topDisplay < 0) return
        if (files.hasFreshHome(topDisplay)) return
        captureHomeIfShowing(topDisplay)
    }

    private fun animate(homePress: HomePress, home: File) {
        val display = homePress.display
        val captured = homePress.captured()
        val sequence = pendingHome.start(display)
        if (!captured) {
            goHome(sequence)
            return
        }
        guard.overlayFor(ANIMATION_MS)
        link.broadcast(DaemonEvent.HomeAnimation(sequence, display, JpegFiles.read(homePress.file), JpegFiles.read(home)))
        // The app starts the switch once its overlay covers the screen; never wait long for it.
        thread.schedule(HOME_FALLBACK_MS) { goHome(sequence) }
    }

    private fun captureHomeLater(display: Int) {
        thread.schedule(HOME_CAPTURE_DELAY_MS) { captureHomeIfShowing(display) }
    }

    private fun captureHomeIfShowing(display: Int) {
        captureExecutor.execute {
            try {
                if (busy() || guard.hudShowing) return@execute
                if (taskService.topAppTask(display) != null) return@execute
                if (capturer.capture(display, files.home(display))) Timber.i("recents: home screen of display $display captured")
            } catch (e: Throwable) {
                Timber.w(e, "home capture failed")
            }
        }
    }

    private fun topAppTask(display: Int): AppTask? = try {
        taskService.topAppTask(display)
    } catch (_: Throwable) {
        null
    }

    companion object {
        /** How long the Home animation may cover the screen, unless the app says it is done. */
        const val ANIMATION_MS = 4000L
        const val HOME_FALLBACK_MS = 800L
        const val HOME_CAPTURE_DELAY_MS = 1500L
    }
}
