package com.thorctl.daemon.system

import android.os.IBinder
import java.lang.reflect.Method

/** android.os.ServiceManager (hidden), resolved once. */
private val getServiceMethod: Method by lazy {
    Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
}

/** The binder ServiceManager has registered under [name], or null. */
internal fun serviceBinder(name: String): IBinder? = try {
    getServiceMethod.invoke(null, name) as IBinder?
} catch (_: Throwable) {
    null
}
