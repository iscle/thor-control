package com.thorctl.daemon.system

import android.content.Context
import android.hardware.input.IInputManager
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
    private val service = HiddenService(Context.INPUT_SERVICE, IInputManager.Stub::asInterface)

    override fun devices(): List<InputDeviceInfo> = service.call {
        getInputDeviceIds().toList().mapNotNull { id ->
            val device = getInputDevice(id) ?: return@mapNotNull null
            InputDeviceInfo(
                id = device.id,
                name = device.name,
                descriptor = device.descriptor,
                isExternal = device.isExternal,
                isGamepad = (device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD,
            )
        }
    }

    override fun associate(descriptor: String, displayUniqueId: String) =
        service.call { addUniqueIdAssociationByDescriptor(descriptor, displayUniqueId) }

    override fun removeAssociation(descriptor: String) = service.call { removeUniqueIdAssociationByDescriptor(descriptor) }

    override fun injectKey(keyCode: Int, displayId: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(
                now, now, action, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD,
            )
            service.call {
                if (displayId >= 0) event.setTargetDisplay(displayId)
                injectInputEvent(event, INJECT_ASYNC)
            }
        }
    }

    private companion object {
        const val INJECT_ASYNC = 0 // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
    }
}
