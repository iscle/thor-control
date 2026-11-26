package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.DaemonCommand

/** The client's side: one [DaemonCommand] as the matching [IThorDaemon] call. Throws RemoteException. */
fun IThorDaemon.send(c: DaemonCommand) {
    when (c) {
        is DaemonCommand.SetFanProfile -> setFanProfile(c.id)
        is DaemonCommand.SaveFanProfile -> saveFanProfile(c.profile.toParcel(), c.activate)
        is DaemonCommand.DeleteFanProfile -> deleteFanProfile(c.id)
        is DaemonCommand.SetBrightness -> setBrightness(c.screen.wire, c.value, c.final)
        is DaemonCommand.SetController -> setController(c.target.wire)
        is DaemonCommand.MoveApp -> moveApp(c.to.wire)
        is DaemonCommand.SetLeds -> setLeds(c.state.toParcel())
        is DaemonCommand.SetBottomPower -> setBottomPower(c.on)
        is DaemonCommand.SetKeepAccessibility -> setKeepAccessibility(c.on)
        is DaemonCommand.SetGameMode -> setGameMode(c.on)
        is DaemonCommand.SetGameOptions -> setGameOptions(c.options.toParcel())
        is DaemonCommand.RecentsOpen -> recentsOpen(c.taskId)
        DaemonCommand.RecentsHome -> recentsHome()
        is DaemonCommand.RecentsRemove -> recentsRemove(c.taskId)
        is DaemonCommand.RecentsClear -> recentsClear(c.taskIds.toIntArray())
        DaemonCommand.RecentsClose -> recentsClose()
        is DaemonCommand.HomeGo -> homeGo(c.seq)
        DaemonCommand.OverlayDone -> overlayDone()
        is DaemonCommand.KeyboardVisible -> keyboardVisible(c.on)
        is DaemonCommand.SetGamepad -> setGamepad(c.on)
        is DaemonCommand.SetGamepadTransport -> setGamepadTransport(c.transport.wire)
        is DaemonCommand.SetGamepadScreensOff -> setGamepadScreensOff(c.on)
        is DaemonCommand.GamepadBluetooth -> gamepadBluetooth(c.connected, c.host)
    }
}
