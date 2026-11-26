package com.thorctl.domain.usecase

import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState
import dev.zacsweers.metro.Inject

/** The Fan tile's icon: the next profile in the list, wrapping around. */
@Inject
class CycleFanProfileUseCase(private val daemon: DaemonRepository) {
    operator fun invoke(state: DaemonState) {
        val ids = state.profiles.map { it.id }
        if (ids.isEmpty()) return
        daemon.send(DaemonCommand.SetFanProfile(ids[(ids.indexOf(state.fanProfile) + 1).mod(ids.size)]))
    }
}
