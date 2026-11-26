package com.thorctl.daemon.hardware.input

import android.system.Os
import java.io.FileDescriptor
import java.lang.reflect.Method

/** [Os].ioctlInt is hidden API, so it is looked up once by reflection. */
private val ioctlIntMethod: Method by lazy {
    Os::class.java.getMethod("ioctlInt", FileDescriptor::class.java, Int::class.javaPrimitiveType)
}

/**
 * `Os.ioctlInt(fd, request)`. It passes a non-null int*, which EVIOCGRAB treats as "grab".
 * Throws (the reflective call wraps the errno) when the ioctl fails.
 */
internal fun ioctlInt(fd: FileDescriptor, request: Int): Int = ioctlIntMethod.invoke(null, fd, request) as Int
