package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaemonBinderTest {
    private class RecordingEndpoint(private val allowed: Set<Int>) : DaemonEndpoint {
        val commands = mutableListOf<DaemonCommand>()
        val registered = mutableListOf<Int>()
        val stats = mutableListOf<Boolean>()

        override fun isAllowed(uid: Int) = uid in allowed

        override fun register(client: IThorClient, uid: Int) {
            registered += uid
        }

        override fun unregister(client: IThorClient) {}

        override fun setStatsSubscribed(client: IThorClient, on: Boolean) {
            stats += on
        }

        override fun handle(command: DaemonCommand) {
            commands += command
        }
    }

    private val all = listOf(
        DaemonCommand.SetFanProfile("quiet"),
        DaemonCommand.SaveFanProfile(Samples.custom, activate = true),
        DaemonCommand.DeleteFanProfile("custom-1"),
        DaemonCommand.SetBrightness(Screen.BOTTOM, 0.4f, final = true),
        DaemonCommand.SetController(ControllerTarget.TOP),
        DaemonCommand.MoveApp(Screen.BOTTOM),
        DaemonCommand.SetLeds(Samples.leds),
        DaemonCommand.SetBottomPower(false),
        DaemonCommand.SetGameMode(true),
        DaemonCommand.SetGameOptions(GameOptions(cpu = false)),
        DaemonCommand.RecentsOpen(12),
        DaemonCommand.RecentsHome,
        DaemonCommand.RecentsRemove(13),
        DaemonCommand.RecentsClear(listOf(1, 2, 3)),
        DaemonCommand.RecentsClose,
        DaemonCommand.HomeGo(5),
        DaemonCommand.OverlayDone,
        DaemonCommand.KeyboardVisible(true),
        DaemonCommand.SetGamepad(true),
        DaemonCommand.SetGamepadTransport(GamepadTransport.BLUETOOTH),
        DaemonCommand.SetGamepadScreensOff(false),
        DaemonCommand.GamepadBluetooth(true, "Mac"),
    )

    @Test
    fun everyCommandArrivesAsSent() {
        val endpoint = RecordingEndpoint(setOf(1234))
        val daemon: IThorDaemon = DaemonBinder(endpoint, callingUid = { 1234 })
        all.forEach(daemon::send)
        assertEquals(all, endpoint.commands)
    }

    @Test
    fun strangersAreIgnored() {
        val endpoint = RecordingEndpoint(setOf(0))
        val daemon = DaemonBinder(endpoint, callingUid = { 10_123 })
        all.forEach(daemon::send)
        daemon.register(ClientBinder({}))
        daemon.setStatsSubscribed(ClientBinder({}), true)
        assertTrue(endpoint.commands.isEmpty())
        assertTrue(endpoint.registered.isEmpty())
        assertTrue(endpoint.stats.isEmpty())
    }

    @Test
    fun registeringPassesTheCallersUid() {
        val endpoint = RecordingEndpoint(setOf(2000))
        DaemonBinder(endpoint, callingUid = { 2000 }).register(ClientBinder({}))
        assertEquals(listOf(2000), endpoint.registered)
    }

    @Test
    fun unknownEnumValuesFromANewerClientAreDropped() {
        val endpoint = RecordingEndpoint(setOf(0))
        val daemon = DaemonBinder(endpoint, callingUid = { 0 })
        daemon.setController("sideways")
        daemon.setBrightness("middle", 0.5f, true)
        daemon.setGamepadTransport("serial")
        assertTrue(endpoint.commands.isEmpty())
        assertEquals(IThorDaemon.VERSION, daemon.version)
    }
}
