package com.thorctl.daemon.system

import android.os.IBinder
import java.lang.reflect.Method

/**
 * A hidden system service: its binder from ServiceManager, turned into the AIDL proxy
 * ([interfaceName]'s Stub.asInterface). The proxy is rebuilt if the service restarts. Each
 * interface's methods are resolved once, in its `I<Name>Ktx.kt` file, and called through [call].
 */
internal class SystemServiceProxy(private val serviceName: String, interfaceName: String) {
    private val asInterfaceMethod: Method by lazy {
        Class.forName("$interfaceName\$Stub").getMethod("asInterface", IBinder::class.java)
    }

    private var binder: IBinder? = null
    private var proxy: Any? = null

    /** The service's proxy, or null while it isn't registered. */
    @Synchronized
    fun get(): Any? {
        val cachedBinder = binder
        if (cachedBinder != null && cachedBinder.isBinderAlive && proxy != null) return proxy
        val freshBinder = serviceBinder(serviceName) ?: return null
        binder = freshBinder
        proxy = asInterfaceMethod.invoke(null, freshBinder)
        return proxy
    }

    /**
     * Calls one of the interface's [method]s. Throws a [ReflectiveOperationException] if the service
     * is missing ([SystemServiceUnavailableException]) or the call fails (the service's own
     * exception wrapped in an InvocationTargetException).
     */
    fun call(method: Method, vararg arguments: Any?): Any? {
        val service = get() ?: throw SystemServiceUnavailableException(serviceName)
        return method.invoke(service, *arguments)
    }
}
