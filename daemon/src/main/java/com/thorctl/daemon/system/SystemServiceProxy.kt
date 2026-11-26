package com.thorctl.daemon.system

import android.os.IBinder
import java.lang.reflect.Method

/**
 * A hidden system service, reached by reflection: its binder from ServiceManager, turned into the
 * AIDL proxy ([interfaceName]'s Stub.asInterface). The proxy is rebuilt if the service restarts.
 * Methods are looked up by name and argument count, so callers name them as in the AIDL file.
 */
internal class SystemServiceProxy(private val name: String, private val interfaceName: String) {
    private var binder: IBinder? = null
    private var proxy: Any? = null
    private val methods = HashMap<String, Method>()

    /** The service's proxy, or null while it isn't registered. */
    @Synchronized
    fun get(): Any? {
        val cachedBinder = binder
        if (cachedBinder != null && cachedBinder.isBinderAlive && proxy != null) return proxy
        val freshBinder = lookup(name) ?: return null
        binder = freshBinder
        methods.clear()
        proxy = Class.forName("$interfaceName\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, freshBinder)
        return proxy
    }

    /** Calls [method] with [arguments]; throws if the service is missing or the call fails. */
    @Synchronized
    fun call(method: String, vararg arguments: Any?): Any? {
        val service = get() ?: throw IllegalStateException("service $name not available")
        val javaMethod = methods.getOrPut(method + arguments.size) {
            service.javaClass.methods.first { it.name == method && it.parameterTypes.size == arguments.size }
        }
        return javaMethod.invoke(service, *arguments)
    }

    companion object {
        /** The binder ServiceManager has registered under [name], or null. */
        fun lookup(name: String): IBinder? = try {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, name) as IBinder?
        } catch (_: Throwable) {
            null
        }
    }
}
