package com.thorctl.domain.usecase

import com.thorctl.domain.repository.Clock
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import dev.zacsweers.metro.Inject

/**
 * "New", "Customize" and "Duplicate": a custom copy of [base]'s curve (Smart's for Auto, which has
 * none), named "Custom N" with the first free N, saved and selected.
 */
@Inject
class CreateCustomFanProfileUseCase(private val daemon: DaemonRepository, private val clock: Clock) {
    operator fun invoke(all: List<FanProfile>, base: FanProfile): FanProfile {
        val profile = newCustom(all, base, clock.nowMillis())
        daemon.send(DaemonCommand.SaveFanProfile(profile, activate = true))
        return profile
    }

    companion object {
        fun newCustom(all: List<FanProfile>, base: FanProfile, now: Long): FanProfile {
            val source = if (base.isAuto) FanProfile.BUILTINS.first { it.id == FanProfile.SMART_ID } else base
            var number = all.count { !it.isBuiltin } + 1
            while (all.any { it.name == "Custom $number" }) number++
            return FanProfile("custom-" + now.toString(36), "Custom $number", source.points)
        }
    }
}
