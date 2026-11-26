package com.thorctl.daemon.feature.recents

import android.view.KeyEvent
import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.ipc.FakeClientLink
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.protocol.model.DaemonEvent
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeButtonTest {
    private val clock = FakeClock()
    private val recentsExecutor = FakeScheduledExecutor(clock)
    private val captureExecutor = FakeScheduledExecutor(clock)
    private val inputService = FakeInputService()
    private val displays = ScreenshotTestDisplays.create(inputService)
    private val link = FakeClientLink()
    private val taskService = FakeTaskService()
    private val capturer = FakeScreenCapturer()
    private val files = ScreenshotFiles(PhysicalDisplayIds(displays), createTempDirectory("recents").toFile())
    private val guard = CaptureGuard(clock)
    private var recentsOpen = false
    private var homeShown = 0
    private val button = HomeButton(
        displays, link, inputService, taskService, capturer, files, guard, RecentsThread(recentsExecutor), captureExecutor,
        isRecentsOpen = { recentsOpen },
        onHomeShown = { homeShown++ },
    )

    private val app = AppTask(5, "app")
    private val home = KeyEvent.KEYCODE_HOME to 0

    private fun inApp() {
        taskService.topTasks[0] = app
    }

    private fun withHomePicture() {
        files.home(0).apply { parentFile.mkdirs() }.writeBytes(ByteArray(2000))
    }

    /** Home goes down and the capture it starts finishes. */
    private fun press(display: Int = 0) {
        button.down(display)
        captureExecutor.runDue()
    }

    @Test
    fun downCapturesTheAppOrTheHomeScreen() {
        press()
        inApp()
        press()
        assertEquals(listOf(0 to "home-111.jpg", 0 to "screen.jpg"), capturer.captures)
    }

    @Test
    fun downCapturesNothingWhileOurScreensShow() {
        recentsOpen = true
        press()
        recentsOpen = false
        guard.overlayFor(1000)
        press()
        button.down(-1)
        captureExecutor.runDue()
        assertTrue(capturer.captures.isEmpty())
        clock.now += 1000
        press()
        assertEquals(1, capturer.captures.size)
    }

    @Test
    fun aShortPressInAnAppAnimatesHome() {
        inApp()
        withHomePicture()
        press()
        assertTrue(button.short(0))
        recentsExecutor.runDue()
        val animation = assertIs<DaemonEvent.HomeAnimation>(link.events.single())
        assertEquals(1, animation.seq)
        assertEquals(0, animation.displayId)
        assertNotNull(animation.appScreen)
        assertNotNull(animation.homeScreen)
        assertTrue(guard.overlayShowing)
        assertTrue(inputService.injectedKeys.isEmpty())

        // Never waits long for the app.
        recentsExecutor.advanceBy(HomeButton.HOME_FALLBACK_MS - 1)
        assertTrue(inputService.injectedKeys.isEmpty())
        recentsExecutor.advanceBy(1)
        assertEquals(listOf(home), inputService.injectedKeys)
        assertEquals(1, homeShown)
    }

    @Test
    fun theAppSaysWhenToGoHomeAndItHappensOnce() {
        inApp()
        withHomePicture()
        press()
        button.short(0)
        recentsExecutor.runDue()
        button.goHome(1)
        recentsExecutor.advanceBy(HomeButton.HOME_FALLBACK_MS)
        button.goHome(1)
        assertEquals(listOf(home), inputService.injectedKeys)
    }

    @Test
    fun aFailedCaptureGoesHomeWithoutAnimation() {
        inApp()
        withHomePicture()
        capturer.works = false
        press()
        assertTrue(button.short(0))
        recentsExecutor.runDue()
        assertTrue(link.events.isEmpty())
        assertEquals(listOf(home), inputService.injectedKeys)
        assertFalse(guard.overlayShowing)
    }

    @Test
    fun withoutAHomePictureHomeIsSentAndPicturedLater() {
        inApp()
        press()
        assertFalse(button.short(0))
        taskService.topTasks.clear() // the caller sent Home
        recentsExecutor.advanceBy(HomeButton.HOME_CAPTURE_DELAY_MS)
        captureExecutor.runDue()
        assertEquals(0 to "home-111.jpg", capturer.captures.last())
        assertTrue(files.home(0).exists())
    }

    @Test
    fun aShortPressThatCannotAnimateIsLeftToTheCaller() {
        withHomePicture()
        assertFalse(button.short(0)) // no press

        press() // on the home screen
        assertFalse(button.short(0))

        inApp()
        press()
        assertFalse(button.short(1)) // another screen

        press()
        link.appConnected = false
        assertFalse(button.short(0))

        press()
        link.appConnected = true
        assertTrue(button.short(0))
        assertFalse(button.short(0)) // used up
    }

    @Test
    fun aHoldUsesThePressPicture() {
        inApp()
        press()
        val shot = button.hold(0)
        assertEquals(app, shot.origin)
        assertEquals(files.appScreen, shot.picture)
        assertEquals(1, capturer.captures.size)
    }

    @Test
    fun aHoldWithoutPressCapturesNow() {
        val shot = button.hold(0)
        assertNull(shot.origin)
        assertEquals(files.home(0), shot.picture)
        assertEquals(listOf(0 to "home-111.jpg"), capturer.captures)

        capturer.works = false
        inApp()
        press(display = 1) // on the other screen: not this hold's
        assertEquals(HomeShot(app, null), button.hold(0))
    }

    @Test
    fun theHomeScreenIsPicturedWhenItShowsAndNothingCoversIt() {
        button.maybeCaptureHome(interactive = false)
        guard.noteHud()
        button.maybeCaptureHome(interactive = true)
        captureExecutor.runDue()
        assertTrue(capturer.captures.isEmpty())

        clock.now += CaptureGuard.HUD_MS
        inApp()
        button.maybeCaptureHome(interactive = true)
        captureExecutor.runDue()
        assertTrue(capturer.captures.isEmpty())

        taskService.topTasks.clear()
        button.maybeCaptureHome(interactive = true)
        captureExecutor.runDue()
        assertEquals(listOf(0 to "home-111.jpg"), capturer.captures)

        // Fresh enough now.
        button.maybeCaptureHome(interactive = true)
        captureExecutor.runDue()
        assertEquals(1, capturer.captures.size)
    }

    @Test
    fun sendingHomePicturesTheHomeScreenAfterwards() {
        button.sendHome(1)
        assertEquals(listOf(KeyEvent.KEYCODE_HOME to 1), inputService.injectedKeys)
        assertEquals(1, homeShown)
        recentsExecutor.advanceBy(HomeButton.HOME_CAPTURE_DELAY_MS)
        captureExecutor.runDue()
        assertEquals(listOf(1 to "home-222.jpg"), capturer.captures)
    }
}
