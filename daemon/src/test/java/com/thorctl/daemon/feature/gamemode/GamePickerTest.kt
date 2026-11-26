package com.thorctl.daemon.feature.gamemode

import android.content.pm.ApplicationInfo
import com.thorctl.daemon.hardware.ThorDisplays
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakePackageService
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.protocol.AppContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GamePickerTest {
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val displays = ThorDisplays.controller(displayService)
    private val taskService = FakeTaskService()
    private val packageService = FakePackageService().apply {
        home = "com.launcher"
        flags["com.game"] = 0
        flags["com.android.settings"] = ApplicationInfo.FLAG_SYSTEM
        flags["com.launcher"] = 0
        flags[AppContract.PACKAGE] = 0
    }
    private val picker = GamePicker(displays, taskService, packageService)

    private fun inFront(packageName: String?) {
        taskService.topTasks[ThorDisplays.TOP.id] = AppTask(5, packageName)
    }

    @Test
    fun picksTheUserAppInFrontOnTheTopScreen() {
        inFront("com.game")
        assertEquals("com.game", picker.pick())
    }

    @Test
    fun ignoresWhatIsOnlyOnTheBottomScreen() {
        taskService.topTasks[ThorDisplays.BOTTOM.id] = AppTask(5, "com.game")
        assertNull(picker.pick())
    }

    @Test
    fun ignoresSystemAppsHomeThisAppAndUnknownPackages() {
        for (packageName in listOf("com.android.settings", "com.launcher", AppContract.PACKAGE, "com.unknown", null)) {
            inFront(packageName)
            assertNull(picker.pick(), "picked $packageName")
        }
    }

    @Test
    fun waitsWhileThereIsNoTopScreen() {
        inFront("com.game")
        displayService.displays = listOf(ThorDisplays.BOTTOM)
        displays.refresh()
        assertNull(picker.pick())
    }
}
