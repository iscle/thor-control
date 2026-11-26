package com.thorctl.daemon.system

import java.lang.reflect.Field

/** A field declared by this class, made accessible; null if it has none by that name. */
internal fun Class<*>.declaredFieldOrNull(name: String): Field? = try {
    getDeclaredField(name).apply { isAccessible = true }
} catch (_: NoSuchFieldException) {
    null
}
