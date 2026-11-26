package com.thorctl.daemon.feature.gamepad

import java.io.IOException
import java.io.OutputStream

/** The HID function's device node (/dev/hidg0): where input reports go and host reports come from. */
interface HidgNode {
    /** Opens the node for input reports. */
    @Throws(IOException::class)
    fun openOutput(): OutputStream

    /**
     * Reads and drops what the host sends (rumble, output report 5) until [running] turns false,
     * reopening the node whenever it goes away. Blocks; returns soon after [running] turns false.
     */
    fun drainHostReports(running: () -> Boolean)
}
