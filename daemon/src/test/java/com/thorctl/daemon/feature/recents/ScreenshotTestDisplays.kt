package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.BottomPanelHealer
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService

/** The Thor's two screens for the recents tests: the top one is display 0 (physical 111), the bottom one display 1 (222). */
object ScreenshotTestDisplays {
    fun create(inputService: FakeInputService = FakeInputService()): DisplayController {
        val displayService = FakeDisplayService(
            listOf(
                DisplayInfo(0, "local:111", type = 1, width = 1920, height = 1080, state = 2),
                DisplayInfo(1, "local:222", type = 1, width = 1240, height = 1080, state = 2),
            ),
        )
        return DisplayController(displayService, inputService, BottomPanelHealer(FakeSys(), FakeClock(), displayService, FakeScheduledExecutor(FakeClock()))).apply { refresh() }
    }
}
