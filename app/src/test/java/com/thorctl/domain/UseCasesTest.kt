package com.thorctl.domain

import com.thorctl.domain.usecase.CreateCustomFanProfileUseCase
import com.thorctl.domain.usecase.CycleFanProfileUseCase
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.sampleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UseCasesTest {
    private val daemon = FakeDaemonRepository()

    @Test
    fun cyclingWrapsAround() {
        val cycle = CycleFanProfileUseCase(daemon)
        cycle(sampleState.copy(fanProfile = "max"))
        cycle(sampleState.copy(fanProfile = "unknown"))
        assertEquals(
            listOf<DaemonCommand>(
                DaemonCommand.SetFanProfile(FanProfile.AUTO_ID),
                DaemonCommand.SetFanProfile(FanProfile.AUTO_ID)
            ), daemon.sent
        )
        cycle(sampleState.copy(profiles = emptyList()))
        assertEquals(2, daemon.sent.size)
    }

    @Test
    fun customProfilesGetTheFirstFreeName() {
        val taken = FanProfile.BUILTINS + FanProfile("custom-a", "Custom 2", emptyList())
        val profile = CreateCustomFanProfileUseCase.newCustom(
            taken,
            FanProfile.BUILTINS.first { it.id == "max" },
            now = 35
        )
        assertEquals("Custom 3", profile.name)
        assertEquals("custom-z", profile.id)
        assertTrue(!profile.isBuiltin)
    }

    @Test
    fun autoHasNoCurveSoSmartsIsCopied() {
        val profile = CreateCustomFanProfileUseCase.newCustom(
            FanProfile.BUILTINS,
            FanProfile.BUILTINS.first(),
            now = 1
        )
        assertEquals(
            FanProfile.BUILTINS.first { it.id == FanProfile.SMART_ID }.points,
            profile.points
        )
        assertEquals("Custom 1", profile.name)
    }
}
