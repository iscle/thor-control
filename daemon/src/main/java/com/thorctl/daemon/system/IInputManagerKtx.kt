package com.thorctl.daemon.system

import android.view.InputDevice
import android.view.InputEvent
import java.lang.reflect.Method

/** android.hardware.input.IInputManager (hidden), resolved once. */
private val inputManager: Class<*> by lazy { Class.forName("android.hardware.input.IInputManager") }
private val getInputDeviceIdsMethod: Method by lazy { inputManager.method("getInputDeviceIds", 0) }
private val getInputDeviceMethod: Method by lazy { inputManager.method("getInputDevice", 1) }
private val addAssociationMethod: Method by lazy { inputManager.method("addUniqueIdAssociationByDescriptor", 2) }
private val removeAssociationMethod: Method by lazy { inputManager.method("removeUniqueIdAssociationByDescriptor", 1) }
private val enableInputDeviceMethod: Method by lazy { inputManager.method("enableInputDevice", 1) }
private val disableInputDeviceMethod: Method by lazy { inputManager.method("disableInputDevice", 1) }
private val injectInputEventMethod: Method by lazy { inputManager.method("injectInputEvent", 2) }

internal fun SystemServiceProxy.getInputDeviceIds(): IntArray = call(getInputDeviceIdsMethod) as IntArray

internal fun SystemServiceProxy.getInputDevice(deviceId: Int): InputDevice? = call(getInputDeviceMethod, deviceId) as InputDevice?

internal fun SystemServiceProxy.addUniqueIdAssociationByDescriptor(descriptor: String, displayUniqueId: String) {
    call(addAssociationMethod, descriptor, displayUniqueId)
}

internal fun SystemServiceProxy.removeUniqueIdAssociationByDescriptor(descriptor: String) {
    call(removeAssociationMethod, descriptor)
}

internal fun SystemServiceProxy.setInputDeviceEnabled(deviceId: Int, enabled: Boolean) {
    call(if (enabled) enableInputDeviceMethod else disableInputDeviceMethod, deviceId)
}

/** [mode]: an InputManager.INJECT_INPUT_EVENT_MODE_* value. */
internal fun SystemServiceProxy.injectInputEvent(event: InputEvent, mode: Int) {
    call(injectInputEventMethod, event, mode)
}
