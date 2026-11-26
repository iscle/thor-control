package com.thorctl.daemon.system

import java.lang.reflect.Method

/** android.hardware.display.IDisplayManager (hidden), resolved once. */
private val displayManager: Class<*> by lazy { Class.forName("android.hardware.display.IDisplayManager") }
private val getDisplayIdsMethod: Method by lazy { displayManager.method("getDisplayIds", 1) }
private val getDisplayInfoMethod: Method by lazy { displayManager.method("getDisplayInfo", 1) }
private val getBrightnessMethod: Method by lazy { displayManager.method("getBrightness", 1) }
private val setBrightnessMethod: Method by lazy { displayManager.method("setBrightness", 2) }
private val requestDisplayPowerMethod: Method by lazy { displayManager.method("requestDisplayPower", 2) }

internal fun SystemServiceProxy.getDisplayIds(includeDisabled: Boolean): IntArray =
    call(getDisplayIdsMethod, includeDisabled) as IntArray

/** An android.view.DisplayInfo (see DisplayInfoKtx.kt), or null if the display is gone. */
internal fun SystemServiceProxy.getDisplayInfo(displayId: Int): Any? = call(getDisplayInfoMethod, displayId)

internal fun SystemServiceProxy.getBrightness(displayId: Int): Float = call(getBrightnessMethod, displayId) as Float

internal fun SystemServiceProxy.setBrightness(displayId: Int, value: Float) {
    call(setBrightnessMethod, displayId, value)
}

internal fun SystemServiceProxy.requestDisplayPower(displayId: Int, state: Int): Boolean =
    call(requestDisplayPowerMethod, displayId, state) as Boolean
