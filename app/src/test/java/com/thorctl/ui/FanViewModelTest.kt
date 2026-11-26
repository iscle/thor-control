package com.thorctl.ui

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.Clock
import com.thorctl.domain.usecase.CreateCustomFanProfileUseCase
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakeDisplayPreferences
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.sampleState
import com.thorctl.ui.fan.FanViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FanViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()
    private val prefs = FakeDisplayPreferences()
    private val vm by lazy { FanViewModel(daemon, prefs, CreateCustomFanProfileUseCase(daemon, Clock { 36L * 36 * 36 })) }

    @Test
    fun showsTheSelectedProfile() {
        assertEquals(FanProfile.SMART_ID, vm.uiState.value.active.id)
        daemon.state.value = sampleState.copy(fanProfile = "max")
        assertEquals("max", vm.uiState.value.active.id)
    }

    @Test
    fun customizingSavesAndSelectsACopy() {
        val base = FanProfile.BUILTINS.first { it.id == "quiet" }
        vm.createCustom(base)
        assertEquals(listOf<DaemonCommand>(DaemonCommand.SaveFanProfile(FanProfile("custom-1000", "Custom 1", base.points), activate = true)), daemon.sent)
    }

    @Test
    fun unitIsAPreference() {
        vm.setUnit(TemperatureUnit.FAHRENHEIT)
        assertEquals(TemperatureUnit.FAHRENHEIT, vm.uiState.value.unit)
        assertTrue(daemon.sent.isEmpty())
    }
}
