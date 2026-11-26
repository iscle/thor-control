package com.thorctl.data

import com.thorctl.data.daemon.withExpectedEffectOf
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.Screen
import com.thorctl.testing.sampleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OptimisticStateTest {
    @Test
    fun lightsAndGameModeChangeAhead() {
        val leds = sampleState.leds.copy(brightness = sampleState.leds.brightness / 2)
        assertEquals(leds, sampleState.withExpectedEffectOf(DaemonCommand.SetLeds(leds)).leds)
        val toggled = !sampleState.game.enabled
        assertEquals(toggled, sampleState.withExpectedEffectOf(DaemonCommand.SetGameMode(toggled)).game.enabled)
        val options = GameOptions(fan = "max")
        assertEquals(options, sampleState.withExpectedEffectOf(DaemonCommand.SetGameOptions(options)).game.options)
    }

    @Test
    fun otherCommandsWaitForTheDaemon() {
        assertSame(sampleState, sampleState.withExpectedEffectOf(DaemonCommand.SetBrightness(Screen.TOP, 0.2f, final = true)))
        assertSame(sampleState, sampleState.withExpectedEffectOf(DaemonCommand.RecentsClose))
    }
}
