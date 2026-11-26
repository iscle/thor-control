package com.thorctl.daemon.system

import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import android.os.ServiceManager

/**
 * A system service's hidden binder interface, from ServiceManager: [asInterface] turns its
 * binder into the AIDL proxy, which is rebuilt if the service restarts. The daemon runs as root
 * under app_process, so the services' permission checks pass and hidden-API restrictions don't
 * apply. The interfaces are compiled against the stubs in :hidden-api (Android 17's signatures).
 */
internal class HiddenService<T : IInterface>(private val name: String, private val asInterface: (IBinder) -> T) {
    private var binder: IBinder? = null
    private var service: T? = null

    /**
     * Runs [block] on the service. Whatever goes wrong becomes a [SystemServiceException]: the
     * service is missing, the binder call fails ([RemoteException]), the service throws (its
     * exception crosses binder as a RuntimeException), a hidden field can't be read
     * ([ReflectiveOperationException]), or the device's framework differs from the stubs
     * ([LinkageError]).
     */
    fun <R> call(block: T.() -> R): R {
        val current = get() ?: throw SystemServiceUnavailableException(name)
        return try {
            current.block()
        } catch (e: RemoteException) {
            throw SystemServiceException("$name call failed", e)
        } catch (e: RuntimeException) {
            throw SystemServiceException("$name refused the call", e)
        } catch (e: ReflectiveOperationException) {
            throw SystemServiceException("$name returned something unreadable", e)
        } catch (e: LinkageError) {
            throw SystemServiceException("$name differs from the Android 17 API", e)
        }
    }

    /** The service's binder right now, or null while it isn't registered. */
    fun binder(): IBinder? = synchronized(this) { get()?.let { binder } }

    @Synchronized
    private fun get(): T? {
        val cachedBinder = binder
        val cached = service
        if (cachedBinder != null && cachedBinder.isBinderAlive && cached != null) return cached
        val freshBinder = ServiceManager.getService(name) ?: return null
        return asInterface(freshBinder).also {
            binder = freshBinder
            service = it
        }
    }
}
