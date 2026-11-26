package com.thorctl.daemon.system

import android.view.KeyEvent
import java.lang.reflect.Method

/** KeyEvent's hidden setDisplayId, resolved once. */
private val setDisplayIdMethod: Method by lazy { KeyEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }

/** Sends this key to one display rather than the focused one. */
internal fun KeyEvent.setTargetDisplay(displayId: Int) {
    setDisplayIdMethod.invoke(this, displayId)
}
