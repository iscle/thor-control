package com.thorctl.daemon.system

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [InputService] over IInputManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiInputService : InputService {
    private val service = SystemServiceProxy("input", "android.hardware.input.IInputManager")

    override fun devices(): List<InputDeviceInfo> {
        val ids = service.call("getInputDeviceIds") as IntArray
        return ids.toList().mapNotNull { id ->
            val device = service.call("getInputDevice", id) as InputDevice? ?: return@mapNotNull null
            InputDeviceInfo(
                id = device.id,
                name = device.name,
                descriptor = device.descriptor,
                isExternal = device.isExternal,
                isGamepad = (device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD,
            )
        }
    }

    override fun associate(descriptor: String, displayUniqueId: String) {
        service.call("addUniqueIdAssociationByDescriptor", descriptor, displayUniqueId)
    }

    override fun removeAssociation(descriptor: String) {
        service.call("removeUniqueIdAssociationByDescriptor", descriptor)
    }

    override fun setDeviceEnabled(deviceId: Int, enabled: Boolean) {
        service.call(if (enabled) "enableInputDevice" else "disableInputDevice", deviceId)
    }

    override fun injectKey(keyCode: Int, displayId: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(
                now, now, action, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD,
            )
            if (displayId >= 0) {
                KeyEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, displayId)
            }
            service.call("injectInputEvent", event, INJECT_ASYNC)
        }
    }

    private companion object {
        const val INJECT_ASYNC = 0 // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
    }
}
