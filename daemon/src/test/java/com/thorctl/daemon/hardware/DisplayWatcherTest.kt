package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.WakeListener
import com.thorctl.daemon.ipc.FakeDaemonNotifier
import com.thorctl.daemon.system.DisplayChange
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakePowerService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DisplayWatcherTest {
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val powerService = FakePowerService()
    private val notifier = FakeDaemonNotifier()
    private val displays = ThorDisplays.controller(displayService)
    private val wakes = mutableListOf<Boolean>()
    private val watcher = DisplayWatcher(displayService, displays, powerService, notifier, lazyOf(setOf(WakeListener { wakes += it })))

    @Test
    fun registersWithTheDisplayManager() {
        watcher.watch()
        assertSame(watcher, displayService.listener)
    }

    @Test
    fun sleepAndWakeAreToldOnceEach() {
        watcher.onDisplayChanged(ThorDisplays.TOP.id, DisplayChange.STATE)
        assertTrue(wakes.isEmpty())

        powerService.interactive = false
        watcher.onDisplayChanged(ThorDisplays.TOP.id, DisplayChange.STATE)
        watcher.onDisplayChanged(ThorDisplays.BOTTOM.id, DisplayChange.STATE)
        powerService.interactive = true
        watcher.onDisplayChanged(ThorDisplays.TOP.id, DisplayChange.STATE)
        assertEquals(listOf(false, true), wakes)
    }

    @Test
    fun aHeldOffPanelChangingStateIsHeldOffAgain() {
        displays.setBottomPower(false)
        displayService.powerRequests.clear()

        // Waking powers it on, while the display manager still reports it off: every state
        // change of a panel held off asks for it to be off again.
        displayService.displays = listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM.copy(state = DisplayController.STATE_OFF))
        watcher.onDisplayChanged(ThorDisplays.BOTTOM.id, DisplayChange.STATE)
        watcher.onDisplayChanged(ThorDisplays.BOTTOM.id, DisplayChange.STATE)
        assertEquals(List(2) { ThorDisplays.BOTTOM.id to DisplayController.STATE_OFF }, displayService.powerRequests)

        // The top screen is not held off.
        displayService.powerRequests.clear()
        watcher.onDisplayChanged(ThorDisplays.TOP.id, DisplayChange.STATE)
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun displaysComingAndGoingAreFollowed() {
        displayService.displays = listOf(ThorDisplays.TOP)
        watcher.onDisplayChanged(ThorDisplays.BOTTOM.id, DisplayChange.REMOVED)
        assertEquals(1, notifier.stateChanges.get())

        displayService.displays = listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM)
        watcher.onDisplayChanged(ThorDisplays.BOTTOM.id, DisplayChange.ADDED)
        assertEquals(ThorDisplays.BOTTOM.id, displays.bottomId)
        assertEquals(2, notifier.stateChanges.get())
    }
}
