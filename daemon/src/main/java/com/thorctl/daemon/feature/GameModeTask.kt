package com.thorctl.daemon.feature

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Lets game mode follow the game: whether it is still running, and which screen it is on. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class GameModeTask(private val game: GameMode, private val powerService: PowerService) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        game.tick(powerService.isInteractive())
    }
}
