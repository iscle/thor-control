package com.thorctl.daemon.ipc

import com.thorctl.protocol.ipc.ClientBinder
import com.thorctl.protocol.ipc.JpegExporter
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientRegistryTest {
    private val appUid = 10_100
    private val uids = UidPolicy { appUid }
    private val registry = ClientRegistry(JpegExporter { null }, uids)
    private val stats = Stats(0f, 0, emptyList(), 0f, 0f, 0, 0f, 0, 0, 0, "", 0f, 0f, 0f, 0, 0f)

    @Test
    fun onlyTheAppCountsAsConnected() {
        registry.add(ClientBinder({}), 2000) // the debug CLI
        assertFalse(registry.appConnected)
        val app = ClientBinder({})
        registry.add(app, appUid)
        assertTrue(registry.appConnected)
        registry.remove(app)
        assertFalse(registry.appConnected)
    }

    @Test
    fun broadcastsReachEveryClientOnce() {
        val a = mutableListOf<DaemonEvent>()
        val b = mutableListOf<DaemonEvent>()
        val app = ClientBinder(a::add)
        registry.add(app, appUid)
        registry.add(app, appUid) // registering again changes nothing
        registry.add(ClientBinder(b::add), 0)
        registry.broadcast(DaemonEvent.MenuButton)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.MenuButton), a)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.MenuButton), b)
    }

    @Test
    fun statsGoOnlyToSubscribers() {
        val a = mutableListOf<DaemonEvent>()
        val b = mutableListOf<DaemonEvent>()
        val subscriber = ClientBinder(a::add)
        registry.add(subscriber, appUid)
        registry.add(ClientBinder(b::add), 0)
        assertFalse(registry.hasStatsSubscribers)
        registry.setStats(subscriber, true)
        assertTrue(registry.hasStatsSubscribers)
        registry.broadcastStats(stats)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.StatsUpdated(stats)), a)
        assertTrue(b.isEmpty())
    }
}
