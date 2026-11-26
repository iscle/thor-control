package com.thorctl.daemon

/** Waits for [condition] that another thread makes true; fails naming [what] after [timeoutMs]. */
fun awaitUntil(what: String, timeoutMs: Long = 3000, condition: () -> Boolean) {
    val until = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what")
        Thread.sleep(5)
    }
}
