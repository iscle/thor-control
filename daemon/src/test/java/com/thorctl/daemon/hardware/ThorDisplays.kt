package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.InputService

/** The Thor's two internal panels as the display manager reports them, for tests. */
object ThorDisplays {
    val TOP = DisplayInfo(0, "local:top", DisplayController.TYPE_INTERNAL, width = 1920, height = 1080, state = DisplayController.STATE_ON)
    val BOTTOM = DisplayInfo(1, "local:bottom", DisplayController.TYPE_INTERNAL, width = 1240, height = 1080, state = DisplayController.STATE_ON)

    /** A refreshed controller over [displayService] (which should list [TOP] and [BOTTOM]). */
    fun controller(displayService: DisplayService, inputService: InputService = FakeInputService()): DisplayController =
        DisplayController(displayService, inputService, BottomPanelHealer(FakeSys(), FakeClock(), displayService, FakeScheduledExecutor(FakeClock()))).apply { refresh() }

    /** A controller that has found no displays (never refreshed). */
    fun unknown(): DisplayController {
        val displayService = FakeDisplayService()
        return DisplayController(displayService, FakeInputService(), BottomPanelHealer(FakeSys(), FakeClock(), displayService, FakeScheduledExecutor(FakeClock())))
    }
}
