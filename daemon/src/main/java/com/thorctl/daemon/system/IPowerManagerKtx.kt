package com.thorctl.daemon.system

import java.lang.reflect.Method

/** android.os.IPowerManager (hidden), resolved once. */
private val powerManager: Class<*> by lazy { Class.forName("android.os.IPowerManager") }
private val isInteractiveMethod: Method by lazy { powerManager.method("isInteractive", 0) }
private val userActivityMethod: Method by lazy { powerManager.method("userActivity", 4) }

internal fun SystemServiceProxy.isInteractive(): Boolean = call(isInteractiveMethod) as Boolean

/** [event]: a PowerManager.USER_ACTIVITY_EVENT_* value. */
internal fun SystemServiceProxy.userActivity(displayId: Int, uptimeMillis: Long, event: Int, flags: Int) {
    call(userActivityMethod, displayId, uptimeMillis, event, flags)
}
