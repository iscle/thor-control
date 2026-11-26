package com.thorctl.daemon.hardware

import android.view.InputDevice
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.model.ControllerTarget
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Routes the built-in controller to a display by associating its input device descriptor with
 * the display's unique id (the same mechanism LineageOS AynParts uses). [ControllerTarget.FREE]
 * removes the association so the controller follows the focused display.
 */
@Inject
@SingleIn(DaemonScope::class)
class InputRouter(private val displays: DisplayController, private val system: SystemServices) {
    @Volatile
    var target = ControllerTarget.FREE
        private set

    private fun descriptor(): String {
        return try {
            val devices = system.inputDevices()
            devices.firstOrNull { it.descriptor == KNOWN_DESCRIPTOR }?.descriptor
                ?: devices.firstOrNull {
                    it.name == CONTROLLER_NAME && !it.isExternal &&
                        (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                }?.descriptor
                ?: KNOWN_DESCRIPTOR
        } catch (_: Throwable) {
            KNOWN_DESCRIPTOR
        }
    }

    fun apply(newTarget: ControllerTarget) {
        val deviceDescriptor = descriptor()
        try {
            when (newTarget) {
                ControllerTarget.TOP -> {
                    if (displays.topUniqueId.isEmpty()) return
                    system.associateByDescriptor(deviceDescriptor, displays.topUniqueId)
                }
                ControllerTarget.BOTTOM -> {
                    if (displays.bottomUniqueId.isEmpty()) return
                    system.associateByDescriptor(deviceDescriptor, displays.bottomUniqueId)
                }
                ControllerTarget.FREE -> system.removeAssociationByDescriptor(deviceDescriptor)
            }
            target = newTarget
            Timber.i("controller -> ${newTarget.wire} ($deviceDescriptor)")
        } catch (error: Throwable) {
            Timber.w(error, "controller routing failed")
        }
    }

    companion object {
        const val KNOWN_DESCRIPTOR = "90dfbbe0bbbab74642c53cae4ae0aa640d40ec21"
        const val CONTROLLER_NAME = "Xbox Wireless Controller"
    }
}
