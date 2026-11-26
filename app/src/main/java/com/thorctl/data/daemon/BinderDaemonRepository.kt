package com.thorctl.data.daemon

import android.os.IBinder
import android.os.RemoteException
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.ipc.ClientBinder
import com.thorctl.protocol.ipc.IThorDaemon
import com.thorctl.protocol.ipc.send
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.closeImages
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * [DaemonRepository] over Binder: connects when the daemon offers its binder, and keeps one
 * [DaemonSession] until the daemon dies or restarts. Calls to the daemon go out in order on one
 * thread (they are oneway, so it never waits for the daemon's work); the daemon's calls arrive on
 * binder threads.
 */
@SingleIn(AppScope::class)
class BinderDaemonRepository(
    private val calls: Executor,
    private val asInterface: (IBinder) -> IThorDaemon,
) : DaemonRepository, DaemonConnection {
    @Inject
    constructor() : this(
        Executors.newSingleThreadExecutor { Thread(it, "thor-daemon-tx") },
        IThorDaemon.Stub::asInterface,
    )

    private val _status = MutableStateFlow<DaemonStatus>(DaemonStatus.Disconnected)
    override val status: StateFlow<DaemonStatus> = _status

    private val _events = MutableSharedFlow<DaemonEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<DaemonEvent> = _events

    private val _gamepadReports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val gamepadReports: SharedFlow<ByteArray> = _gamepadReports

    private val client = ClientBinder(::onEvent)

    @Volatile
    private var session: DaemonSession? = null

    @Volatile
    private var statsWanted = 0

    private val death = IBinder.DeathRecipient { calls.execute { disconnect("daemon died") } }

    override fun onDaemonBinder(binder: IBinder) {
        calls.execute { connect(binder) }
    }

    override fun currentBinder(): IBinder? = session?.takeIf { it.registered }?.binder

    override fun send(command: DaemonCommand) {
        session?.updateState { it.withExpectedEffectOf(command) }
        call { it.send(command) }
    }

    override fun subscribeStats(on: Boolean) {
        synchronized(this) { statsWanted = (statsWanted + if (on) 1 else -1).coerceAtLeast(0) }
        call { it.setStatsSubscribed(client, statsWanted > 0) }
    }

    private fun connect(binder: IBinder) {
        val current = session
        if (current != null && current.binder == binder && current.registered) {
            // The daemon offers its binder only while it doesn't count us as registered: it
            // dropped us (a failed call), so register again. Registering twice changes nothing.
            register(current)
            return
        }
        if (current != null) disconnect("daemon restarted")
        val daemon = asInterface(binder)
        try {
            val version = daemon.version
            if (version != IThorDaemon.VERSION) {
                // A daemon from another APK version (an update not fully applied yet): its calls
                // may not mean what ours do. Wait for the matching one.
                Timber.w("daemon speaks version $version, the app ${IThorDaemon.VERSION}: not connecting")
                return
            }
            binder.linkToDeath(death, 0)
        } catch (e: RemoteException) {
            Timber.w(e, "connecting to the daemon failed")
            return
        }
        // Set before registering: the daemon's first state can arrive before register returns.
        val newSession = DaemonSession(daemon, binder)
        session = newSession
        if (register(newSession)) Timber.i("connected to the daemon")
    }

    private fun register(session: DaemonSession): Boolean = try {
        session.daemon.register(client)
        if (statsWanted > 0) session.daemon.setStatsSubscribed(client, true)
        session.registered = true
        true
    } catch (e: RemoteException) {
        Timber.w("registering with the daemon failed: ${e.javaClass.simpleName}")
        disconnect("register failed")
        false
    }

    private fun disconnect(why: String) {
        val lost = synchronized(this) {
            val current = session ?: return
            session = null
            _status.value = DaemonStatus.Disconnected
            current
        }
        try {
            lost.binder.unlinkToDeath(death, 0)
        } catch (_: Exception) {
        }
        Timber.i("disconnected from the daemon: $why")
    }

    private fun onEvent(event: DaemonEvent) {
        when (event) {
            is DaemonEvent.StateChanged -> fromSession { it.onState(event.state) }
            is DaemonEvent.StatsUpdated -> fromSession { it.onStats(event.stats) }
            is DaemonEvent.GamepadReport -> _gamepadReports.tryEmit(event.report)
            else -> if (_events.subscriptionCount.value == 0 || !_events.tryEmit(event)) {
                // Nobody would read its images: free the daemon's pipes now.
                Timber.w("event dropped, nobody keeps up: $event")
                event.closeImages()
            }
        }
    }

    /** Hands a state or reading to the current session, and publishes the connection it completes. */
    private fun fromSession(deliver: (DaemonSession) -> DaemonStatus.Connected?) {
        val current = session ?: return // a late call from a dead daemon
        val connected = deliver(current) ?: return
        synchronized(this) {
            if (session === current) _status.value = connected
        }
    }

    private fun call(block: (IThorDaemon) -> Unit) {
        calls.execute {
            val current = session ?: return@execute
            try {
                block(current.daemon)
            } catch (e: RemoteException) {
                // A oneway call fails when the daemon's buffer is full too: only a dead binder
                // ends the connection (its death recipient then fires as well).
                Timber.w("call to the daemon failed: ${e.javaClass.simpleName}")
                if (!current.binder.isBinderAlive) disconnect("daemon gone")
            }
        }
    }
}
