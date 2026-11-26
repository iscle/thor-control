package com.thorctl.daemon.system

import java.lang.reflect.Method

/** android.window.TaskSnapshot (hidden), resolved once. */
private val getHardwareBufferMethod: Method by lazy { Class.forName("android.window.TaskSnapshot").getMethod("getHardwareBuffer") }

/** Frees this TaskSnapshot's graphics buffer (when only the copy on disk is wanted). */
internal fun Any.releaseSnapshotBuffer() {
    (getHardwareBufferMethod.invoke(this) as? AutoCloseable)?.close()
}
