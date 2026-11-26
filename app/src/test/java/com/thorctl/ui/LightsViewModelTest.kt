package com.thorctl.ui

import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.sampleState
import com.thorctl.ui.lights.LightsViewModel
import com.thorctl.ui.lights.StickTarget
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LightsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val leds = LedState(left = 0x111111, right = 0x222222, sync = false)
    private val daemon = FakeDaemonRepository(sampleState.copy(leds = leds))
    private val vm by lazy { LightsViewModel(daemon) }

    private fun lastLeds() = (daemon.sent.last() as DaemonCommand.SetLeds).state

    @Test
    fun startsOnTheLeftStickWhenTheSticksDiffer() {
        assertEquals(StickTarget.LEFT, vm.uiState.value.target)
        assertEquals(0x111111, vm.uiState.value.color)
    }

    @Test
    fun colorGoesToTheChosenStick() {
        vm.setTarget(StickTarget.RIGHT)
        vm.setColor(0xFF00AA)
        assertEquals(leds.copy(right = 0xFF00AA, sync = false), lastLeds())
    }

    @Test
    fun bothSticksLinkTheColors() {
        vm.setTarget(StickTarget.BOTH)
        assertEquals(leds.copy(sync = true, right = 0x111111), lastLeds())
        daemon.state.value = sampleState.copy(leds = lastLeds())
        vm.setColor(0x00FF00 or 0x7F000000)
        assertEquals(leds.copy(left = 0x00FF00, right = 0x00FF00, sync = true), lastLeds())
    }

    @Test
    fun slidersStayInRange() {
        vm.setBrightness(0f)
        assertEquals(LightsViewModel.MIN_BRIGHTNESS, lastLeds().brightness)
        vm.setSpeed(1f)
        assertEquals(LedState.MAX_SPEED, lastLeds().speed)
        assertEquals(1f, LightsViewModel.speedFraction(LedState.MAX_SPEED))
    }

    @Test
    fun pickingAModeTurnsTheLightsOn() {
        daemon.state.value = sampleState.copy(leds = leds.copy(enabled = false))
        vm.setMode(LedMode.RAINBOW)
        assertEquals(leds.copy(mode = LedMode.RAINBOW, enabled = true), lastLeds())
        daemon.state.value = sampleState.copy(leds = lastLeds())
        assertFalse(vm.uiState.value.colorEditable)
    }
}
