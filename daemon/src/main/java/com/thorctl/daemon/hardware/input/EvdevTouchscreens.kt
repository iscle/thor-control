package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.IOException

/**
 * [Touchscreens] by taking the touchscreen's evdev node exclusively (EVIOCGRAB) and not reading
 * it: Android gets no touches until the node is closed again. The kernel drops the grab when the
 * daemon exits, so a crash never leaves a panel untouchable.
 */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class EvdevTouchscreens(private val nodes: InputNodes, private val evdev: Evdev) : Touchscreens {
    /** The grabbed nodes, by device name. */
    private val grabbed = HashMap<String, EvdevDevice>()

    @Synchronized
    override fun setEnabled(name: String, enabled: Boolean) {
        if (enabled) {
            grabbed.remove(name)?.closeQuietly()
            return
        }
        if (name in grabbed) return
        val path = nodes.find { it.name == name } ?: return Timber.w("no touchscreen named $name")
        val device = try {
            evdev.open(path)
        } catch (e: IOException) {
            return Timber.w(e, "can't open touchscreen $name")
        }
        try {
            device.grab()
            grabbed[name] = device
        } catch (e: IOException) {
            device.closeQuietly()
            Timber.w(e, "can't take touchscreen $name")
        }
    }
}
