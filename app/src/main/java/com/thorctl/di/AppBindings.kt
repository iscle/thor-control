package com.thorctl.di

import com.thorctl.data.daemon.BinderDaemonRepository
import com.thorctl.data.daemon.DaemonConnection
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.GamepadPairing
import com.thorctl.platform.bluetooth.BluetoothGamepad
import com.thorctl.platform.haptics.Haptics
import com.thorctl.platform.haptics.VibratorHaptics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

/** Implementations bound to the interfaces the rest of the app asks for. */
@ContributesTo(AppScope::class)
@BindingContainer
interface AppBindings {
    @Binds
    val BinderDaemonRepository.bindDaemon: DaemonRepository

    @Binds
    val BinderDaemonRepository.bindConnection: DaemonConnection

    @Binds
    val VibratorHaptics.bindHaptics: Haptics

    @Binds
    val BluetoothGamepad.bindPairing: GamepadPairing
}
