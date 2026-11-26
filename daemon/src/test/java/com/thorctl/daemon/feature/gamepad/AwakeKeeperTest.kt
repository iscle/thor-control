package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.hardware.PanelBacklight
import android.view.KeyEvent
import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.BottomPanelHealer
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.input.FakeTouchscreens
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.FakePowerService
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.PowerService
import com.thorctl.daemon.system.SystemServiceUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AwakeKeeperTest {
    private val sys = FakeSys()
    private val displayService = FakeDisplayService()
    private val inputService = FakeInputService()
    private val powerService = FakePowerService()
    private val displays = DisplayController(displayService, FakeTouchscreens(), BottomPanelHealer(sys, FakeClock(), displayService, FakeScheduledExecutor(FakeClock())), PanelBacklight(sys))
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

    @Test
    fun aPanelThatCantBePokedDoesNotStopTheOthers() {
        displayService.displays = listOf(display(0, 1080, 1920), display(3, 1080, 1240))
        displays.refresh()
        val poked = mutableListOf<Int>()
        val flakyPower = object : PowerService by powerService {
            override fun userActivity(displayId: Int) {
                if (displayId == 0) throw SystemServiceUnavailableException("power")
                poked += displayId
            }
        }
        AwakeKeeper(displays, flakyPower, inputService, sys).keepAwake()
        assertEquals(listOf(3), poked)
    }

    @Test
    fun onlySystemServiceFailuresAreSwallowed() {
        val brokenPower = object : PowerService by powerService {
            override fun userActivity(displayId: Int) = throw IllegalStateException("a bug")
        }
        assertFailsWith<IllegalStateException> { AwakeKeeper(displays, brokenPower, inputService, sys).keepAwake() }
    }

    @Test
    fun aWakeUpThatFailsIsLoggedNotThrown() {
        val noInput = object : InputService by inputService {
            override fun injectKey(keyCode: Int, displayId: Int) = throw SystemServiceUnavailableException("input")
        }
        AwakeKeeper(displays, powerService, noInput, sys).wakeUp()
    }

    private fun display(id: Int, width: Int, height: Int) =
        DisplayInfo(id = id, uniqueId = "local:$id", type = DisplayController.TYPE_INTERNAL, width = width, height = height, state = 2)
}
