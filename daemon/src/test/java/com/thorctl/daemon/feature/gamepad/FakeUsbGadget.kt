package com.thorctl.daemon.feature.gamepad

import java.util.concurrent.atomic.AtomicInteger

class FakeUsbGadget(
    @Volatile var setupError: String? = null,
    @Volatile var leftovers: Boolean = false,
) : UsbGadget {
    @Volatile
    var linked = false

    @Volatile
    var configured = false

    val setups = AtomicInteger()
    val teardowns = AtomicInteger()

    override fun isLinked(): Boolean = linked

    override fun hasLeftovers(): Boolean = leftovers

    override fun addHidFunction(): String? {
        setups.incrementAndGet()
        if (setupError == null) linked = true
        return setupError
    }

    override fun removeHidFunction() {
        teardowns.incrementAndGet()
        linked = false
        leftovers = false
    }

    override fun isConfigured(): Boolean = configured
}
