package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.system.DisplayInfo
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.InputDeviceInfo
import com.thorctl.protocol.model.ControllerTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputRouterTest {
    private val displayService = FakeDisplayService(
        listOf(
            DisplayInfo(0, "local:top", type = 1, width = 1920, height = 1080, state = 2),
            DisplayInfo(1, "local:bottom", type = 1, width = 1240, height = 1080, state = 2),
        ),
    )
    private val inputService = FakeInputService()
    private val displays = DisplayController(displayService, inputService, BottomPanelHealer(FakeSys(), FakeClock(), displayService)).apply { refresh() }
    private val router = InputRouter(displays, inputService)

    @Test
    fun routesTheBuiltInControllerToTheChosenScreen() {
        inputService.devices = listOf(
            InputDeviceInfo(7, InputRouter.CONTROLLER_NAME, "external", isExternal = true, isGamepad = true),
            InputDeviceInfo(8, InputRouter.CONTROLLER_NAME, "builtin", isExternal = false, isGamepad = true),
        )
        router.apply(ControllerTarget.BOTTOM)
        assertEquals(mapOf("builtin" to displays.bottomUniqueId), inputService.associations)
        assertEquals(ControllerTarget.BOTTOM, router.target)

        router.apply(ControllerTarget.FREE)
        assertTrue(inputService.associations.isEmpty())
        assertEquals(ControllerTarget.FREE, router.target)
    }

    @Test
    fun fallsBackToTheKnownDescriptor() {
        router.apply(ControllerTarget.TOP)
        assertEquals(mapOf(InputRouter.KNOWN_DESCRIPTOR to displays.topUniqueId), inputService.associations)
    }
}
