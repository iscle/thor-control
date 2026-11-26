package com.thorctl.protocol.ipc

import android.os.Binder
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.Screen

/**
 * The daemon's binder: checks the caller and turns each call into a [DaemonCommand]. Calls with
 * an unknown enum value (from a newer client) are dropped.
 */
class DaemonBinder(
    private val endpoint: DaemonEndpoint,
    private val callingUid: () -> Int = Binder::getCallingUid,
) : IThorDaemon.Stub() {
    private fun allowed(): Boolean = endpoint.isAllowed(callingUid())

    private fun command(command: DaemonCommand?) {
        if (command != null && allowed()) endpoint.handle(command)
    }

    override fun getVersion(): Int = IThorDaemon.VERSION

    override fun register(client: IThorClient?) {
        val uid = callingUid()
        if (client != null && endpoint.isAllowed(uid)) endpoint.register(client, uid)
    }

    override fun unregister(client: IThorClient?) {
        if (client != null && allowed()) endpoint.unregister(client)
    }

    override fun setStatsSubscribed(client: IThorClient?, on: Boolean) {
        if (client != null && allowed()) endpoint.setStatsSubscribed(client, on)
    }

    override fun setFanProfile(id: String?) = command(id?.let { DaemonCommand.SetFanProfile(it) })

    override fun saveFanProfile(profile: FanProfileParcel?, activate: Boolean) =
        command(profile?.let { DaemonCommand.SaveFanProfile(it.toModel(), activate) })

    override fun deleteFanProfile(id: String?) = command(id?.let { DaemonCommand.DeleteFanProfile(it) })

    override fun setBrightness(screen: String?, value: Float, isFinal: Boolean) =
        command(Screen.fromWire(screen)?.let { DaemonCommand.SetBrightness(it, value, isFinal) })

    override fun setController(target: String?) = command(ControllerTarget.fromWire(target)?.let { DaemonCommand.SetController(it) })

    override fun moveApp(to: String?) = command(Screen.fromWire(to)?.let { DaemonCommand.MoveApp(it) })

    override fun setLeds(state: LedStateParcel?) = command(state?.let { DaemonCommand.SetLeds(it.toModel()) })

    override fun setBottomPower(on: Boolean) = command(DaemonCommand.SetBottomPower(on))

    override fun setKeepAccessibility(on: Boolean) = command(DaemonCommand.SetKeepAccessibility(on))

    override fun setGameMode(on: Boolean) = command(DaemonCommand.SetGameMode(on))

    override fun setGameOptions(options: GameOptionsParcel?) = command(options?.let { DaemonCommand.SetGameOptions(it.toModel()) })

    override fun recentsOpen(taskId: Int) = command(DaemonCommand.RecentsOpen(taskId))

    override fun recentsHome() = command(DaemonCommand.RecentsHome)

    override fun recentsRemove(taskId: Int) = command(DaemonCommand.RecentsRemove(taskId))

    override fun recentsClear(taskIds: IntArray?) = command(DaemonCommand.RecentsClear(taskIds?.toList().orEmpty()))

    override fun recentsClose() = command(DaemonCommand.RecentsClose)

    override fun homeGo(seq: Int) = command(DaemonCommand.HomeGo(seq))

    override fun overlayDone() = command(DaemonCommand.OverlayDone)

    override fun keyboardVisible(on: Boolean) = command(DaemonCommand.KeyboardVisible(on))

    override fun setGamepad(on: Boolean) = command(DaemonCommand.SetGamepad(on))

    override fun setGamepadTransport(transport: String?) =
        command(GamepadTransport.fromWire(transport)?.let { DaemonCommand.SetGamepadTransport(it) })

    override fun setGamepadScreensOff(on: Boolean) = command(DaemonCommand.SetGamepadScreensOff(on))

    override fun gamepadBluetooth(connected: Boolean, host: String?) = command(DaemonCommand.GamepadBluetooth(connected, host.orEmpty()))
}
