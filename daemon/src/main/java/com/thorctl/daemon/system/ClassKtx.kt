package com.thorctl.daemon.system

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Lookups for the reflection handles in this package's `*Ktx.kt` files. Methods of hidden binder
 * interfaces are found by name and parameter count, as their AIDL declares them.
 */
internal fun Class<*>.method(name: String, parameterCount: Int): Method =
    methods.first { it.name == name && it.parameterCount == parameterCount }

/** A field declared by this class, made accessible; null if it has none by that name. */
internal fun Class<*>.declaredFieldOrNull(name: String): Field? = try {
    getDeclaredField(name).apply { isAccessible = true }
} catch (_: NoSuchFieldException) {
    null
}

/** The class named [name], or null if this release has none. */
internal fun classOrNull(name: String): Class<*>? = try {
    Class.forName(name)
} catch (_: ClassNotFoundException) {
    null
}
