package com.thorctl.ui

import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.content
import com.thorctl.testing.sampleState
import com.thorctl.ui.lights.LightsViewModel
import com.thorctl.ui.lights.StickTarget
import com.thorctl.ui.state.DaemonUiState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LightsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val leds = LedState(left = 0x111111, right = 0x222222, sync = false)
    private val daemon = FakeDaemonRepository(sampleState.copy(leds = leds))
    private val viewModel by lazy { LightsViewModel(daemon) }

    private fun lastLeds() = (daemon.sent.last() as DaemonCommand.SetLeds).state

    /** What the daemon does after a SetLeds: echoes the new state. */
    private fun echo() = daemon.push(sampleState.copy(leds = lastLeds()))

    @Test
    fun startsOnTheLeftStickWhenTheSticksDiffer() {
        assertEquals(StickTarget.LEFT, viewModel.uiState.value.content.target)
        assertEquals(0x111111, viewModel.uiState.value.content.color)
    }

    @Test
    fun colorGoesToTheChosenStick() {
        viewModel.setTarget(StickTarget.RIGHT)
        viewModel.setColor(0xFF00AA)
        assertEquals(leds.copy(right = 0xFF00AA, sync = false), lastLeds())
    }

    @Test
    fun bothSticksLinkTheColors() {
        viewModel.setTarget(StickTarget.BOTH)
        assertEquals(leds.copy(sync = true, right = 0x111111), lastLeds())
        echo()
        viewModel.setColor(0x00FF00 or 0x7F000000)
        assertEquals(leds.copy(left = 0x00FF00, right = 0x00FF00, sync = true), lastLeds())
    }

    @Test
    fun slidersStayInRange() {
        viewModel.setBrightness(0f)
        assertEquals(LightsViewModel.MIN_BRIGHTNESS, lastLeds().brightness)
        viewModel.setSpeed(1f)
        assertEquals(LedState.MAX_SPEED, lastLeds().speed)
        assertEquals(1f, LightsViewModel.speedFraction(LedState.MAX_SPEED))
    }

    @Test
    fun pickingAModeTurnsTheLightsOn() {
        daemon.push(sampleState.copy(leds = leds.copy(enabled = false)))
        viewModel.setMode(LedMode.RAINBOW)
        assertEquals(leds.copy(mode = LedMode.RAINBOW, enabled = true), lastLeds())
        echo()
        assertFalse(viewModel.uiState.value.content.colorEditable)
    }

    @Test
    fun unchangedLightsAreNotSentAgain() {
        viewModel.setTarget(StickTarget.LEFT) // already unlinked
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun disconnected() {
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
        viewModel.setColor(0xFFFFFF)
        viewModel.setEnabled(false)
        assertTrue(daemon.sent.isEmpty())
    }
}
