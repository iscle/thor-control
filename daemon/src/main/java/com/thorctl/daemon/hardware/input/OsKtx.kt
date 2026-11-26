package com.thorctl.daemon.hardware.input

import android.system.ErrnoException
import android.system.Os
import java.io.FileDescriptor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** [Os].ioctlInt is hidden API, so it is looked up once by reflection. */
private val ioctlIntMethod: Method by lazy {
    Os::class.java.getMethod("ioctlInt", FileDescriptor::class.java, Int::class.javaPrimitiveType)
}

/**
 * `Os.ioctlInt(fd, request)`. It passes a non-null int*, which EVIOCGRAB treats as "grab".
 * Throws [ErrnoException] when the ioctl fails, [ReflectiveOperationException] when the hidden
 * method can't be called at all.
 */
@Throws(ErrnoException::class, ReflectiveOperationException::class)
internal fun ioctlInt(fd: FileDescriptor, request: Int): Int = try {
    ioctlIntMethod.invoke(null, fd, request) as Int
} catch (e: InvocationTargetException) {
    throw e.targetException as? ErrnoException ?: e
}
