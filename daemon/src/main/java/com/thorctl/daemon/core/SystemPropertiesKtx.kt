package com.thorctl.daemon.core

import java.lang.reflect.Method

/** android.os.SystemProperties (hidden), resolved once. */
private val getMethod: Method by lazy { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }

/** A system property's value ("" if unset or unreadable), without spawning getprop. */
internal fun systemProperty(name: String): String = try {
    getMethod.invoke(null, name) as String
} catch (_: Throwable) {
    ""
}
