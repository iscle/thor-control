package com.thorctl.daemon.feature.gamepad

import android.view.KeyEvent
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.FakePowerService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AwakeKeeperTest {
    private val sys = FakeSys()
    private val displayService = FakeDisplayService()
    private val inputService = FakeInputService()
    private val powerService = FakePowerService()
    private val displays = DisplayController(sys, displayService, inputService)
    private val awake = AwakeKeeper(displays, powerService, inputService, sys)

    @Test
    fun keepAwakeTakesATimedWakelockAndPokesEveryPanel() {
        displayService.displays = listOf(display(0, 1080, 1920), display(3, 1080, 1240))
        displays.refresh()
        awake.keepAwake()
        assertEquals(listOf(AwakeKeeper.WAKE_LOCK to "thorctl_gamepad 60000000000"), sys.writes)
        assertEquals(listOf(0, 3), powerService.userActivity)
    }

    @Test
    fun aMissingPanelIsSkipped() {
        awake.keepAwake() // no bottom panel known yet: top only
        assertEquals(listOf(0), powerService.userActivity)
    }

    @Test
    fun releaseDropsTheWakelock() {
        awake.release()
        assertEquals(listOf(AwakeKeeper.WAKE_UNLOCK to "thorctl_gamepad"), sys.writes)
    }

    @Test
    fun wakeUpInjectsWakeupOnTheTopPanel() {
        powerService.interactive = false
        assertFalse(awake.isInteractive())
        awake.wakeUp()
        assertEquals(listOf(KeyEvent.KEYCODE_WAKEUP to 0), inputService.injectedKeys)
    }

    private fun display(id: Int, width: Int, height: Int) =
        DisplayInfo(id = id, uniqueId = "local:$id", type = DisplayController.TYPE_INTERNAL, width = width, height = height, state = 2)
}
