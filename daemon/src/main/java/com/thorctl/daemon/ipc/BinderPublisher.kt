package com.thorctl.daemon.ipc

import android.os.Bundle
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.AppProviderClient
import com.thorctl.protocol.ipc.DaemonBinder
import com.thorctl.protocol.ipc.ThorIpc
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands the daemon's binder to the app ([ThorIpc]): at start, and again on every housekeeping
 * tick while the app isn't registered (it was restarted, or the daemon was). On a thread of its
 * own: reaching the provider can wait seconds for Android to start the app's process, and the
 * scheduler's ticks (the fan among them) must not wait with it.
 */
@Inject
@SingleIn(DaemonScope::class)
class BinderPublisher(
    private val appProvider: AppProviderClient,
    private val clients: ClientLink,
    private val binder: Lazy<DaemonBinder>,
) {
    @Volatile
    private var lastFailure: String? = null

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "binder-offer").apply { isDaemon = true } }
    private val pending = AtomicBoolean(false)

    fun offerIfNeeded() {
        if (!clients.appConnected) offer()
    }

    /** At most one offer in flight; ticks meanwhile are dropped. */
    fun offer() {
        if (pending.compareAndSet(false, true)) {
            worker.execute {
                try {
                    offerNow()
                } finally {
                    pending.set(false)
                }
            }
        }
    }

    private fun offerNow() {
        try {
            appProvider.call(ThorIpc.METHOD_OFFER, Bundle().apply { putBinder(ThorIpc.KEY_BINDER, binder.value) })
            if (lastFailure != null) Timber.i("binder offered to the app again")
            lastFailure = null
        } catch (e: Throwable) {
            // Logged once per kind of failure: the app may be missing for a while (reinstall).
            val kind = (e.cause ?: e).toString()
            if (kind != lastFailure) Timber.w(e, "offering the binder to the app failed")
            lastFailure = kind
        }
    }
}
