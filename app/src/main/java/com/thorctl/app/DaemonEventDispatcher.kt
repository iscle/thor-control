package com.thorctl.app

import com.thorctl.platform.haptics.Haptics
import com.thorctl.platform.keyboard.KeyboardEditor
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.ui.hud.HudWindow
import com.thorctl.ui.menu.MenuWindow
import com.thorctl.ui.recents.RecentsOverlay
import dev.zacsweers.metro.Inject

/**
 * The one place the daemon's events are handled, in the order they arrive. The `when` is
 * exhaustive: a new [DaemonEvent] doesn't compile until it has a handler here.
 */
@Inject
class DaemonEventDispatcher(
    private val menu: MenuWindow,
    private val hud: HudWindow,
    private val recents: RecentsOverlay,
    private val keyboard: KeyboardEditor,
    private val haptics: Haptics,
) {
    fun dispatch(event: DaemonEvent) {
        when (event) {
            DaemonEvent.MenuButton -> menu.toggle()
            // A long Home press: the heavy click confirms it before recents opens.
            DaemonEvent.Haptic -> haptics.heavyClick()
            is DaemonEvent.Hud -> hud.show(event.hud, event.displayId)
            is DaemonEvent.RecentsOpened -> recents.open(event)
            is DaemonEvent.HomeAnimation -> recents.openHomePress(event)
            is DaemonEvent.RecentsThumb -> recents.updateThumb(event)
            is DaemonEvent.RecentsButton -> recents.onButton(event.action)
            DaemonEvent.RecentsClosed -> recents.close()
            DaemonEvent.KeyboardShift -> keyboard.onControllerShift()
            // These reach the app through DaemonStatus and DaemonRepository.gamepadReports instead.
            is DaemonEvent.StateChanged, is DaemonEvent.StatsUpdated, is DaemonEvent.GamepadReport -> Unit
        }
    }
}
