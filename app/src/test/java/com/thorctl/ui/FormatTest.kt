package com.thorctl.ui

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.testing.sampleStats
import com.thorctl.ui.format.Format
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun temperatures() {
        assertEquals("61 °C", Format.temperature(61.9f, TemperatureUnit.CELSIUS))
        assertEquals("143 °F", Format.temperature(61.9f, TemperatureUnit.FAHRENHEIT))
        assertEquals("32 °F", Format.temperature(0f, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun power() {
        assertEquals("6.3 W", Format.powerValue(sampleStats))
        assertEquals("On battery · 81% · 31 °C", Format.powerDetail(sampleStats, TemperatureUnit.CELSIUS))
        val charging = sampleStats.copy(batteryStatus = "Charging", batteryWatts = 12f, inputWatts = 18f)
        assertEquals("+12.0 W", Format.powerValue(charging))
        assertEquals("Charging · 81% · in 18.0 W", Format.powerDetail(charging, TemperatureUnit.CELSIUS))
        assertEquals("Plugged in · 81%", Format.powerDetail(sampleStats.copy(inputWatts = 3f), TemperatureUnit.CELSIUS))
    }

    @Test
    fun telemetry() {
        assertEquals("2.40 GHz · 61 °C", Format.cpuDetail(sampleStats, TemperatureUnit.CELSIUS))
        assertEquals("500 MHz · 48 °C", Format.gpuDetail(sampleStats, TemperatureUnit.CELSIUS))
        assertEquals("4.5 GB", Format.memoryValue(sampleStats))
        assertEquals("of 8 GB · 60% used", Format.memoryDetail(sampleStats))
        assertEquals("42%", Format.percent(0.42f))
    }

    @Test
    fun gamepadSummaries() {
        assertEquals("Off", Format.gamepadSummary(GamepadState()))
        assertEquals("USB · waiting for a computer", Format.gamepadSummary(GamepadState(on = true)))
        assertEquals("Connected to Mac", Format.gamepadSummary(GamepadState(on = true, transport = GamepadTransport.BLUETOOTH, connected = true, host = "Mac")))
        assertEquals("Connected to a device", Format.gamepadSummary(GamepadState(on = true, transport = GamepadTransport.BLUETOOTH, connected = true)))
        assertEquals("No gadget", Format.gamepadSummary(GamepadState(on = true, error = "No gadget")))
        assertEquals("Try Bluetooth instead", Format.gamepadHint(GamepadState(on = true, error = "No gadget")))
    }

    @Test
    fun labels() {
        assertEquals("Unavailable", Format.controller(null))
        assertEquals("Follows focus", Format.controller(ControllerTarget.FREE))
        assertEquals("Balanced", Format.fanDescription(FanProfile.BUILTINS.first { it.id == FanProfile.SMART_ID }))
        assertEquals("Custom curve", Format.fanDescription(FanProfile("custom-1", "x", emptyList())))
    }
}
