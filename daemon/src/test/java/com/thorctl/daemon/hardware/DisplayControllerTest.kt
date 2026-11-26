package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.InputDeviceInfo
import com.thorctl.protocol.model.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayControllerTest {
    private val external = DisplayInfo(5, "virtual:cast", type = 3, width = 3840, height = 2160, state = DisplayController.STATE_ON)
    private val displayService = FakeDisplayService(listOf(external, ThorDisplays.BOTTOM, ThorDisplays.TOP))
    private val inputService = FakeInputService(
        listOf(
            InputDeviceInfo(TOP_TOUCH, DisplayController.TOP_TOUCH_NAME, "top", isExternal = false, isGamepad = false),
            InputDeviceInfo(BOTTOM_TOUCH, DisplayController.BOTTOM_TOUCH_NAME, "bottom", isExternal = false, isGamepad = false),
        ),
    )
    private val displays = DisplayController(displayService, inputService, BottomPanelHealer(FakeSys(), FakeClock(), displayService, FakeScheduledExecutor(FakeClock())))

    @Test
    fun aDeadDisplayManagerChangesNothing() {
        var dead = false
        val flaky = object : DisplayService by displayService {
            override fun displays(): List<DisplayInfo> = if (dead) throw ReflectiveOperationException("gone") else displayService.displays()

            override fun brightness(displayId: Int): Float = if (dead) throw ReflectiveOperationException("gone") else displayService.brightness(displayId)

            override fun requestDisplayPower(displayId: Int, state: Int): Boolean =
                if (dead) throw ReflectiveOperationException("gone") else displayService.requestDisplayPower(displayId, state)
        }
        val displays = DisplayController(flaky, inputService, BottomPanelHealer(FakeSys(), FakeClock(), flaky, FakeScheduledExecutor(FakeClock())))
        displays.refresh()
        dead = true

        displays.refresh()
        assertEquals(ThorDisplays.TOP.id, displays.topId)
        assertEquals(ThorDisplays.BOTTOM.id, displays.bottomId)
        assertEquals(0f, displays.getBrightness(Screen.TOP))
        assertFalse(displays.setBottomPower(false))
        assertFalse(displays.bottomForcedOff)
        assertTrue(inputService.disabledDevices.isEmpty())
    }

    @Test
    fun theLargerInternalPanelIsTheTopOne() {
        displays.refresh()
        assertEquals(ThorDisplays.TOP.id, displays.topId)
        assertEquals(ThorDisplays.TOP.uniqueId, displays.topUniqueId)
        assertEquals(ThorDisplays.BOTTOM.id, displays.bottomId)
        assertEquals(ThorDisplays.BOTTOM.uniqueId, displays.bottomUniqueId)
    }

    @Test
    fun aLoneUnknownPanelIsTheTopOne() {
        displayService.displays = listOf(ThorDisplays.TOP)
        displays.refresh()
        assertEquals(ThorDisplays.TOP.id, displays.topId)
        assertEquals(-1, displays.bottomId)
    }

    @Test
    fun aLoneKnownBottomPanelStaysTheBottomOne() {
        displays.refresh()
        displayService.displays = listOf(ThorDisplays.BOTTOM.copy(id = 7))
        displays.refresh()
        assertEquals(7, displays.bottomId)
        assertEquals(-1, displays.topId)

        displayService.displays = listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM)
        displays.refresh()
        assertEquals(ThorDisplays.TOP.id, displays.topId)
        assertEquals(ThorDisplays.BOTTOM.id, displays.bottomId)
    }

    @Test
    fun noInternalPanelsKeepsWhatWasKnown() {
        displays.refresh()
        displayService.displays = listOf(external)
        displays.refresh()
        assertEquals(ThorDisplays.TOP.id, displays.topId)
        assertEquals(ThorDisplays.BOTTOM.id, displays.bottomId)
    }

    @Test
    fun brightnessIsPerScreenWithAFloor() {
        displays.refresh()
        displays.setBrightness(Screen.BOTTOM, 0f)
        displays.setBrightness(Screen.TOP, 1.5f)
        assertEquals(DisplayController.MIN_BRIGHTNESS, displayService.brightness[ThorDisplays.BOTTOM.id])
        assertEquals(1f, displays.getBrightness(Screen.TOP))
    }

    @Test
    fun noBrightnessWithoutTheScreen() {
        displayService.displays = listOf(ThorDisplays.TOP)
        displays.refresh()
        displays.setBrightness(Screen.BOTTOM, 0.5f)
        assertEquals(0f, displays.getBrightness(Screen.BOTTOM))
        assertTrue(displayService.brightness.isEmpty())
    }

    @Test
    fun bottomPowerFollowsTheRequestAndTouchWithIt() {
        displays.refresh()
        assertTrue(displays.setBottomPower(false))
        assertTrue(displays.bottomForcedOff)
        assertFalse(displays.bottomOn)
        assertEquals(setOf(BOTTOM_TOUCH), inputService.disabledDevices)

        assertTrue(displays.setBottomPower(true))
        assertTrue(displays.bottomOn)
        assertTrue(inputService.disabledDevices.isEmpty())
        assertEquals(
            listOf(ThorDisplays.BOTTOM.id to DisplayController.STATE_OFF, ThorDisplays.BOTTOM.id to DisplayController.STATE_UNKNOWN),
            displayService.powerRequests,
        )
    }

    @Test
    fun noBottomPowerWithoutTheScreen() {
        displayService.displays = listOf(ThorDisplays.TOP)
        displays.refresh()
        assertFalse(displays.setBottomPower(false))
        assertFalse(displays.bottomForcedOff)
    }

    @Test
    fun reassertHoldsTheForcedOffBottomScreenOff() {
        displays.refresh()
        displays.reassertOverrides()
        assertTrue(displayService.powerRequests.isEmpty())

        displays.setBottomPower(false)
        displayService.powerRequests.clear()
        displays.reassertOverrides()
        assertEquals(listOf(ThorDisplays.BOTTOM.id to DisplayController.STATE_OFF), displayService.powerRequests)
    }

    @Test
    fun gamepadDarkTurnsOnlyTheTopPanelOff() {
        displays.refresh()
        displays.setGamepadDark(true)
        assertTrue(displays.gamepadDark)
        assertEquals(listOf(ThorDisplays.TOP.id to DisplayController.STATE_OFF), displayService.powerRequests)
        assertEquals(setOf(TOP_TOUCH, BOTTOM_TOUCH), inputService.disabledDevices)

        displayService.powerRequests.clear()
        displays.reassertOverrides()
        assertEquals(listOf(ThorDisplays.TOP.id to DisplayController.STATE_OFF), displayService.powerRequests)

        displayService.powerRequests.clear()
        displays.setGamepadDark(false)
        assertEquals(listOf(ThorDisplays.TOP.id to DisplayController.STATE_UNKNOWN), displayService.powerRequests)
        assertTrue(inputService.disabledDevices.isEmpty())
    }

    @Test
    fun aBottomScreenSwitchedOffIsOnWhileDarkAndOffAfter() {
        displays.refresh()
        displays.setBottomPower(false)
        displayService.powerRequests.clear()

        displays.setGamepadDark(true)
        assertEquals(
            listOf(ThorDisplays.TOP.id to DisplayController.STATE_OFF, ThorDisplays.BOTTOM.id to DisplayController.STATE_UNKNOWN),
            displayService.powerRequests,
        )
        // While dark, switching the bottom screen only changes what happens when the screens light up.
        displayService.powerRequests.clear()
        assertTrue(displays.setBottomPower(true))
        assertTrue(displays.setBottomPower(false))
        assertTrue(displayService.powerRequests.isEmpty())

        displays.setGamepadDark(false)
        assertEquals(
            listOf(ThorDisplays.TOP.id to DisplayController.STATE_UNKNOWN, ThorDisplays.BOTTOM.id to DisplayController.STATE_OFF),
            displayService.powerRequests,
        )
        assertEquals(setOf(BOTTOM_TOUCH), inputService.disabledDevices)
    }

    private companion object {
        const val TOP_TOUCH = 3
        const val BOTTOM_TOUCH = 4
    }
}
