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

    /** Runs as the function is removed, before anything changes (to check what is still open then). */
    @Volatile
    var onRemove: () -> Unit = {}

    override fun removeHidFunction() {
        onRemove()
        teardowns.incrementAndGet()
        linked = false
        leftovers = false
    }

    override fun isConfigured(): Boolean = configured
}
