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
    private var appDisconnects = 0
    private val registry = ClientRegistry(JpegExporter { null }, uids, lazyOf(setOf(AppConnectionListener { appDisconnects++ })))
    private val stats = Stats(0f, 0, emptyList(), 0f, 0f, 0, 0f, 0, 0, 0, "", 0f, 0f, 0f, 0, 0f)

    @Test
    fun onlyTheAppCountsAsConnected() {
        registry.add(ClientBinder({}), 2000) // the debug CLI
        assertFalse(registry.appConnected)
        val appClient = ClientBinder({})
        registry.add(appClient, appUid)
        assertTrue(registry.appConnected)
        registry.remove(appClient)
        assertFalse(registry.appConnected)
    }

    @Test
    fun theAppGoingAwayIsToldOnce() {
        val cli = ClientBinder({})
        val app = ClientBinder({})
        registry.add(cli, 2000)
        registry.add(app, appUid)
        registry.remove(cli)
        assertEquals(0, appDisconnects)
        registry.remove(app)
        registry.remove(app)
        assertEquals(1, appDisconnects)
    }

    @Test
    fun broadcastsReachEveryClientOnce() {
        val appEvents = mutableListOf<DaemonEvent>()
        val rootEvents = mutableListOf<DaemonEvent>()
        val appClient = ClientBinder(appEvents::add)
        registry.add(appClient, appUid)
        registry.add(appClient, appUid) // registering again changes nothing
        registry.add(ClientBinder(rootEvents::add), 0)
        registry.broadcast(DaemonEvent.MenuButton)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.MenuButton), appEvents)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.MenuButton), rootEvents)
    }

    @Test
    fun statsGoOnlyToSubscribers() {
        val subscriberEvents = mutableListOf<DaemonEvent>()
        val otherEvents = mutableListOf<DaemonEvent>()
        val subscriber = ClientBinder(subscriberEvents::add)
        registry.add(subscriber, appUid)
        registry.add(ClientBinder(otherEvents::add), 0)
        assertFalse(registry.hasStatsSubscribers)
        registry.setStats(subscriber, true)
        assertTrue(registry.hasStatsSubscribers)
        registry.broadcastStats(stats)
        assertEquals(listOf<DaemonEvent>(DaemonEvent.StatsUpdated(stats)), subscriberEvents)
        assertTrue(otherEvents.isEmpty())
    }
}
