package com.thorctl.ui

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.Clock
import com.thorctl.domain.usecase.CreateCustomFanProfileUseCase
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakeDisplayPreferences
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.content
import com.thorctl.testing.sampleState
import com.thorctl.ui.fan.FanViewModel
import com.thorctl.ui.state.DaemonUiState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()
    private val preferences = FakeDisplayPreferences()
    private val viewModel by lazy {
        FanViewModel(
            daemon,
            preferences,
            CreateCustomFanProfileUseCase(daemon, Clock { 36L * 36 * 36 })
        )
    }

    @Test
    fun showsTheSelectedProfile() {
        assertEquals(FanProfile.SMART_ID, viewModel.uiState.value.content.active.id)
        daemon.push(sampleState.copy(fanProfile = "max"))
        assertEquals("max", viewModel.uiState.value.content.active.id)
    }

    @Test
    fun customizingSavesAndSelectsACopy() {
        val base = FanProfile.BUILTINS.first { it.id == "quiet" }
        viewModel.createCustom(base)
        assertEquals(
            listOf<DaemonCommand>(
                DaemonCommand.SaveFanProfile(
                    FanProfile(
                        "custom-1000",
                        "Custom 1",
                        base.points
                    ), activate = true
                )
            ), daemon.sent
        )
    }

    @Test
    fun unitIsAPreference() {
        viewModel.setUnit(TemperatureUnit.FAHRENHEIT)
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.uiState.value.content.unit)
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun disconnected() {
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
        viewModel.createCustom(FanProfile.BUILTINS.first())
        assertTrue(daemon.sent.isEmpty())
    }
}
