package com.thorctl.daemon.hardware

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.InputService
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
class InputRouter(private val displays: DisplayController, private val inputService: InputService) {
    @Volatile
    var target = ControllerTarget.FREE
        private set

    private fun descriptor(): String {
        return try {
            val devices = inputService.devices()
            devices.firstOrNull { it.descriptor == KNOWN_DESCRIPTOR }?.descriptor
                ?: devices.firstOrNull {
                    it.name == CONTROLLER_NAME && !it.isExternal && it.isGamepad
                }?.descriptor
                ?: KNOWN_DESCRIPTOR
        } catch (_: ReflectiveOperationException) {
            KNOWN_DESCRIPTOR
        }
    }

    fun apply(newTarget: ControllerTarget) {
        val deviceDescriptor = descriptor()
        try {
            when (newTarget) {
                ControllerTarget.TOP -> {
                    if (displays.topUniqueId.isEmpty()) return
                    inputService.associate(deviceDescriptor, displays.topUniqueId)
                }
                ControllerTarget.BOTTOM -> {
                    if (displays.bottomUniqueId.isEmpty()) return
                    inputService.associate(deviceDescriptor, displays.bottomUniqueId)
                }
                ControllerTarget.FREE -> inputService.removeAssociation(deviceDescriptor)
            }
            target = newTarget
            Timber.i("controller -> ${newTarget.wire} ($deviceDescriptor)")
        } catch (e: ReflectiveOperationException) {
            Timber.w(e, "controller routing failed")
        }
    }

    companion object {
        const val KNOWN_DESCRIPTOR = "90dfbbe0bbbab74642c53cae4ae0aa640d40ec21"
        const val CONTROLLER_NAME = "Xbox Wireless Controller"
    }
}
