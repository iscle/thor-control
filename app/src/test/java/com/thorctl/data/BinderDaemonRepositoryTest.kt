package com.thorctl.data

import com.thorctl.data.daemon.BinderDaemonRepository
import com.thorctl.domain.model.DaemonStatus
import kotlin.test.assertIs
import kotlin.test.assertSame
import com.thorctl.protocol.ipc.DaemonBinder
import com.thorctl.protocol.ipc.DaemonEndpoint
import com.thorctl.protocol.ipc.IThorClient
import com.thorctl.protocol.ipc.IThorDaemon
import com.thorctl.protocol.ipc.toParcel
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.LedState
import com.thorctl.testing.sampleState
import com.thorctl.testing.sampleStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app's repository talking to a real [DaemonBinder] (called directly, as Binder would in-process). */
class BinderDaemonRepositoryTest {
    private class Endpoint : DaemonEndpoint {
        val commands = mutableListOf<DaemonCommand>()
        val stats = mutableListOf<Boolean>()
        var client: IThorClient? = null
        var registrations = 0

        override fun isAllowed(uid: Int) = true

        override fun register(client: IThorClient, uid: Int) {
            this.client = client
            registrations++
        }

        override fun unregister(client: IThorClient) {}

        override fun setStatsSubscribed(client: IThorClient, on: Boolean) {
            stats += on
        }

        override fun handle(command: DaemonCommand) {
            commands += command
        }
    }

    private val endpoint = Endpoint()
    private val daemonBinder = DaemonBinder(endpoint, callingUid = { 10_100 })
    private var daemonVersion = IThorDaemon.VERSION
    private val repo = BinderDaemonRepository(calls = { it.run() }) { daemon ->
        object : IThorDaemon by daemon as IThorDaemon {
            override fun getVersion() = daemonVersion
        }
    }

    private fun connect() = repo.onDaemonBinder(daemonBinder)

    @Test
    fun connectingRegistersAClient() {
        assertEquals(DaemonStatus.Disconnected, repo.status.value)
        connect()
        assertTrue(endpoint.client != null)
        // Connected once the daemon's first state and first reading are both in.
        assertEquals(DaemonStatus.Disconnected, repo.status.value)
        endpoint.client!!.onState(sampleState.toParcel())
        assertEquals(DaemonStatus.Disconnected, repo.status.value)
        endpoint.client!!.onStats(sampleStats.toParcel())
        val connected = assertIs<DaemonStatus.Connected>(repo.status.value)
        assertEquals(sampleState, connected.state.value)
        assertEquals(sampleStats, connected.stats.value)
        assertEquals(daemonBinder, repo.currentBinder())
    }

    @Test
    fun aSecondOfferRegistersAgain() {
        // The daemon offers its binder again only after dropping us.
        connect()
        connect()
        assertEquals(2, endpoint.registrations)
        assertEquals(daemonBinder, repo.currentBinder())
    }

    @Test
    fun aDaemonOfAnotherVersionIsLeftAlone() {
        daemonVersion = IThorDaemon.VERSION + 1
        connect()
        assertEquals(DaemonStatus.Disconnected, repo.status.value)
        assertNull(endpoint.client)
        assertNull(repo.currentBinder())
    }

    @Test
    fun laterStatesAndReadingsFlowInsideTheConnection() {
        connect()
        endpoint.client!!.onState(sampleState.toParcel())
        endpoint.client!!.onStats(sampleStats.toParcel())
        val connected = assertIs<DaemonStatus.Connected>(repo.status.value)
        val later = sampleState.copy(bottomOn = false)
        val hotter = sampleStats.copy(cpuTemp = 90f)
        endpoint.client!!.onState(later.toParcel())
        endpoint.client!!.onStats(hotter.toParcel())
        // The status itself doesn't change: only what flows inside it.
        assertSame(connected, repo.status.value)
        assertEquals(later, connected.state.value)
        assertEquals(hotter, connected.stats.value)
    }

    @Test
    fun commandsReachTheDaemonInOrder() {
        connect()
        repo.send(DaemonCommand.SetFanProfile("quiet"))
        repo.send(DaemonCommand.RecentsClose)
        assertEquals(listOf(DaemonCommand.SetFanProfile("quiet"), DaemonCommand.RecentsClose), endpoint.commands)
    }

    @Test
    fun nothingIsSentWhileDisconnected() {
        repo.send(DaemonCommand.SetFanProfile("quiet"))
        assertTrue(endpoint.commands.isEmpty())
    }

    @Test
    fun lightsAndGameModeShowAtOnce() {
        connect()
        endpoint.client!!.onState(sampleState.toParcel())
        endpoint.client!!.onStats(sampleStats.toParcel())
        val leds = LedState(brightness = 12)
        repo.send(DaemonCommand.SetLeds(leds))
        repo.send(DaemonCommand.SetGameMode(true))
        val state = assertIs<DaemonStatus.Connected>(repo.status.value).state.value
        assertEquals(leds, state.leds)
        assertTrue(state.game.enabled)
    }

    @Test
    fun statsAreReferenceCounted() {
        repo.subscribeStats(true) // before connecting: subscribed on connect
        connect()
        repo.subscribeStats(true)
        repo.subscribeStats(false)
        repo.subscribeStats(false)
        repo.subscribeStats(false) // never below zero
        assertEquals(listOf(true, true, true, false, false), endpoint.stats)
    }
}
