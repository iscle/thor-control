package com.thorctl.daemon.system

/** Reads a field of a hidden framework object (searching superclasses), or null if it has none. */
internal fun Any.hiddenField(name: String): Any? {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        try {
            return currentClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
        } catch (_: NoSuchFieldException) {
            currentClass = currentClass.superclass
        }
    }
    return null
}
