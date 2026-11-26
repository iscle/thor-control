package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.DaemonCommand

/** The client's side: one [DaemonCommand] as the matching [IThorDaemon] call. Throws RemoteException. */
fun IThorDaemon.send(command: DaemonCommand) {
    when (command) {
        is DaemonCommand.SetFanProfile -> setFanProfile(command.id)
        is DaemonCommand.SaveFanProfile -> saveFanProfile(command.profile.toParcel(), command.activate)
        is DaemonCommand.DeleteFanProfile -> deleteFanProfile(command.id)
        is DaemonCommand.SetBrightness -> setBrightness(command.screen.wire, command.value, command.final)
        is DaemonCommand.SetController -> setController(command.target.wire)
        is DaemonCommand.MoveApp -> moveApp(command.to.wire)
        is DaemonCommand.SetLeds -> setLeds(command.state.toParcel())
        is DaemonCommand.SetBottomPower -> setBottomPower(command.on)
        is DaemonCommand.SetKeepAccessibility -> setKeepAccessibility(command.on)
        is DaemonCommand.SetGameMode -> setGameMode(command.on)
        is DaemonCommand.SetGameOptions -> setGameOptions(command.options.toParcel())
        is DaemonCommand.RecentsOpen -> recentsOpen(command.taskId)
        DaemonCommand.RecentsHome -> recentsHome()
        is DaemonCommand.RecentsRemove -> recentsRemove(command.taskId)
        is DaemonCommand.RecentsClear -> recentsClear(command.taskIds.toIntArray())
        DaemonCommand.RecentsClose -> recentsClose()
        is DaemonCommand.HomeGo -> homeGo(command.seq)
        DaemonCommand.OverlayDone -> overlayDone()
        is DaemonCommand.KeyboardVisible -> keyboardVisible(command.on)
        is DaemonCommand.SetGamepad -> setGamepad(command.on)
        is DaemonCommand.SetGamepadTransport -> setGamepadTransport(command.transport.wire)
        is DaemonCommand.SetGamepadScreensOff -> setGamepadScreensOff(command.on)
        is DaemonCommand.GamepadBluetooth -> gamepadBluetooth(command.connected, command.host)
    }
}
