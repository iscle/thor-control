package com.thorctl.daemon.hardware.input

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.fail

/** Evdev nodes in memory: every [open] makes a [FakeEvdevDevice]; [write]s are recorded. */
class FakeEvdev : Evdev {
    val opened = CopyOnWriteArrayList<FakeEvdevDevice>()
    val writes = CopyOnWriteArrayList<Pair<String, List<EvdevEvent>>>()

    /** Grabs of nodes opened from now on fail. */
    @Volatile
    var failGrab = false

    /** Switch bits nodes opened from now on report; null makes EVIOCGSW fail. */
    @Volatile
    var switches: Int? = 0

    override fun open(path: String): EvdevDevice = FakeEvdevDevice(path, failGrab, switches).also { opened += it }

    override fun write(path: String, events: List<EvdevEvent>) {
        writes += path to events
    }

    fun openedCount(path: String): Int = opened.count { it.path == path }

    /** The [number]th device opened on [path] (1 = first), waiting for a reader thread to open it. */
    fun awaitOpened(path: String, number: Int = 1): FakeEvdevDevice {
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            opened.filter { it.path == path }.getOrNull(number - 1)?.let { return it }
            Thread.sleep(5)
        }
        fail("$path was not opened $number time(s)")
    }
}
