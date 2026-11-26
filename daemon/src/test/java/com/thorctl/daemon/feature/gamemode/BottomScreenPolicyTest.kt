package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.ThorDisplays
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeTaskService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomScreenPolicyTest {
    private val sys = FakeSys()
    private val taskService = FakeTaskService()
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val displays = ThorDisplays.controller(displayService)
    private val policy = BottomScreenPolicy(displays, taskService, sys)
    private val running = listOf(AppProcess(100, 10234, GAME, listOf(GAME), 100))

    private fun inFront(packageName: String) {
        taskService.topTasks[ThorDisplays.TOP.id] = AppTask(5, packageName)
    }

    private fun update(game: String? = GAME, bottomOff: Boolean = true, interactive: Boolean = true) =
        policy.update(game, bottomOff, running, interactive)

    private val bottomRequests get() = displayService.powerRequests.filter { it.first == ThorDisplays.BOTTOM.id }.map { it.second }

    @Test
    fun turnsTheBottomScreenOffAfterTwoTicksInFront() {
        inFront(GAME)
        assertEquals(BottomScreenChange.UNCHANGED, update())
        assertEquals(BottomScreenChange.TURNED_OFF, update())
        assertEquals(listOf(DisplayController.STATE_OFF), bottomRequests)
        assertTrue(displays.bottomForcedOff)
        // Already off: nothing more to do.
        assertEquals(BottomScreenChange.UNCHANGED, update())
    }

    @Test
    fun quickSwitchesStartTheWaitOver() {
        inFront(GAME)
        update()
        inFront("com.browser")
        update()
        inFront(GAME)
        assertEquals(BottomScreenChange.UNCHANGED, update())
        assertEquals(BottomScreenChange.TURNED_OFF, update())
    }

    @Test
    fun turnsItBackOnWhenTheGameLeavesTheFront() {
        inFront(GAME)
        update()
        update()
        inFront("com.browser")
        assertEquals(BottomScreenChange.TURNED_ON, update())
        assertEquals(listOf(DisplayController.STATE_OFF, DisplayController.STATE_UNKNOWN), bottomRequests)
        assertFalse(displays.bottomForcedOff)
    }

    @Test
    fun leavesItAloneWhenNotAskedOrWithoutAGame() {
        inFront(GAME)
        repeat(3) { assertEquals(BottomScreenChange.UNCHANGED, update(bottomOff = false)) }
        repeat(3) { assertEquals(BottomScreenChange.UNCHANGED, update(game = null)) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun doesNothingWhileAsleep() {
        inFront(GAME)
        repeat(3) { assertEquals(BottomScreenChange.UNCHANGED, update(interactive = false)) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun keepsItOnWhenTheGameHasAnActivityThere() {
        inFront(GAME)
        taskService.visible[ThorDisplays.BOTTOM.id] = setOf(GAME)
        repeat(4) { assertEquals(BottomScreenChange.UNCHANGED, update()) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun keepsItOnWhenTheGameDrawsAWindowThere() {
        inFront(GAME)
        sys.shell = { if (it == "dumpsys SurfaceFlinger") " ├─ Display 0 x\n └─ Display 1 y\n    └─ 61 Presentation#210 uid=10234" else null }
        repeat(4) { assertEquals(BottomScreenChange.UNCHANGED, update()) }
        assertTrue(displayService.powerRequests.isEmpty())
        // Decided once for this game: SurfaceFlinger isn't asked again.
        assertEquals(1, sys.commands.size)
    }

    @Test
    fun theUserTurningItBackOnKeepsItOnForThisGame() {
        inFront(GAME)
        update()
        update()
        displays.setBottomPower(true) // AYN tap
        repeat(4) { assertEquals(BottomScreenChange.UNCHANGED, update()) }
        assertFalse(displays.bottomForcedOff)

        policy.gameEnded()
        update()
        assertEquals(BottomScreenChange.TURNED_OFF, update())
    }

    @Test
    fun turnBackOnOnlyUndoesItsOwnChange() {
        assertFalse(policy.turnBackOn())
        displays.setBottomPower(false) // the user's choice
        assertFalse(policy.turnBackOn())
        assertTrue(displays.bottomForcedOff)
    }

    private companion object {
        const val GAME = "com.game"
    }
}
