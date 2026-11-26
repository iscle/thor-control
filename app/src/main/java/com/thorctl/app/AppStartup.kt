package com.thorctl.app

import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.bluetooth.BluetoothGamepad
import com.thorctl.platform.haptics.Haptics
import com.thorctl.platform.keyboard.KeyboardEditor
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.ui.blackout.BlackoutWindow
import com.thorctl.ui.hud.HudWindow
import com.thorctl.ui.keyboard.KeyboardWindow
import com.thorctl.ui.menu.MenuWindow
import com.thorctl.ui.recents.RecentsOverlay
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** What runs for the whole life of the process: the overlays and the Bluetooth gamepad, listening to the daemon. */
@Inject
class AppStartup(
    private val daemon: DaemonRepository,
    private val menu: MenuWindow,
    private val hud: HudWindow,
    private val recents: RecentsOverlay,
    private val keyboard: KeyboardEditor,
    private val keyboardWindow: KeyboardWindow,
    private val blackout: BlackoutWindow,
    private val gamepad: BluetoothGamepad,
    private val haptics: Haptics,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        menu.start()
        hud.start()
        recents.start()
        keyboard.start()
        keyboardWindow.start()
        blackout.start()
        gamepad.start()
        // A long Home press: the heavy click confirms it before recents opens.
        scope.launch { daemon.events.filterIsInstance<DaemonEvent.Haptic>().collect { haptics.heavyClick() } }
    }
}
