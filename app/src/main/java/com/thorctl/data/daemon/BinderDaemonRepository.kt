package com.thorctl.data.daemon

import android.os.IBinder
import android.os.RemoteException
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.ipc.ClientBinder
import com.thorctl.protocol.ipc.IThorDaemon
import com.thorctl.protocol.ipc.send
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.closeImages
import com.thorctl.protocol.model.Stats
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
 * [DaemonRepository] over Binder. Calls to the daemon go out in order on one thread (they are
 * oneway, so it never waits for the daemon's work); the daemon's calls arrive on binder threads.
 */
@SingleIn(AppScope::class)
class BinderDaemonRepository(
    private val calls: Executor,
    private val asInterface: (IBinder) -> IThorDaemon,
) : DaemonRepository, DaemonConnection {
    @Inject
    constructor() : this(Executors.newSingleThreadExecutor { Thread(it, "thor-daemon-tx") }, IThorDaemon.Stub::asInterface)

    private val _state = MutableStateFlow<DaemonState?>(null)
    override val state: StateFlow<DaemonState?> = _state

    private val _stats = MutableStateFlow<Stats?>(null)
    override val stats: StateFlow<Stats?> = _stats

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected

    private val _events = MutableSharedFlow<DaemonEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<DaemonEvent> = _events

    private val _gamepadReports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val gamepadReports: SharedFlow<ByteArray> = _gamepadReports

    private val client = ClientBinder(::onEvent)

    @Volatile
    private var daemon: IThorDaemon? = null

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var statsWanted = 0

    private val death = IBinder.DeathRecipient { calls.execute { disconnected("daemon died") } }

    override fun onDaemonBinder(binder: IBinder) {
        calls.execute { connect(binder) }
    }

    override fun currentBinder(): IBinder? = binder.takeIf { _connected.value }

    private fun connect(b: IBinder) {
        if (b == binder && _connected.value) {
            // The daemon offers its binder only while it doesn't count us as registered: it
            // dropped us (a failed call), so register again. Registering twice changes nothing.
            try {
                daemon?.register(client)
            } catch (e: RemoteException) {
                Timber.w("registering again failed: ${e.javaClass.simpleName}")
                disconnected("register failed")
            }
            return
        }
        if (binder != null) disconnected("daemon restarted")
        val d = asInterface(b)
        try {
            val version = d.version
            if (version != IThorDaemon.VERSION) {
                // A daemon from another APK version (an update not fully applied yet): its calls
                // may not mean what ours do. Wait for the matching one.
                Timber.w("daemon speaks version $version, the app ${IThorDaemon.VERSION}: not connecting")
                return
            }
            b.linkToDeath(death, 0)
            binder = b
            daemon = d
            d.register(client)
            if (statsWanted > 0) d.setStatsSubscribed(client, true)
            _connected.value = true
            Timber.i("connected to the daemon")
        } catch (e: RemoteException) {
            Timber.w(e, "connecting to the daemon failed")
            disconnected("connect failed")
        }
    }

    private fun disconnected(why: String) {
        val b = binder ?: return
        try {
            b.unlinkToDeath(death, 0)
        } catch (_: Exception) {
        }
        binder = null
        daemon = null
        _connected.value = false
        Timber.i("disconnected from the daemon: $why")
    }

    private fun onEvent(e: DaemonEvent) {
        when (e) {
            is DaemonEvent.StateChanged -> _state.value = e.state
            is DaemonEvent.StatsUpdated -> _stats.value = e.stats
            is DaemonEvent.GamepadReport -> _gamepadReports.tryEmit(e.report)
            else -> if (_events.subscriptionCount.value == 0 || !_events.tryEmit(e)) {
                // Nobody would read its images: free the daemon's pipes now.
                Timber.w("event dropped, nobody keeps up: $e")
                e.closeImages()
            }
        }
    }

    override fun send(command: DaemonCommand) {
        optimistic(command)
        call { it.send(command) }
    }

    /** Commands whose effect sliders and switches must show at once, before the daemon echoes it. */
    private fun optimistic(c: DaemonCommand) {
        val s = _state.value ?: return
        _state.value = when (c) {
            is DaemonCommand.SetLeds -> s.copy(leds = c.state)
            is DaemonCommand.SetGameMode -> s.copy(game = s.game.copy(enabled = c.on))
            is DaemonCommand.SetGameOptions -> s.copy(game = s.game.copy(options = c.options))
            else -> return
        }
    }

    override fun subscribeStats(on: Boolean) {
        synchronized(this) { statsWanted = (statsWanted + if (on) 1 else -1).coerceAtLeast(0) }
        call { it.setStatsSubscribed(client, statsWanted > 0) }
    }

    private fun call(block: (IThorDaemon) -> Unit) {
        calls.execute {
            val d = daemon ?: return@execute
            try {
                block(d)
            } catch (e: RemoteException) {
                // A oneway call fails when the daemon's buffer is full too: only a dead binder
                // ends the connection (its death recipient then fires as well).
                Timber.w("call to the daemon failed: ${e.javaClass.simpleName}")
                if (binder?.isBinderAlive != true) disconnected("daemon gone")
            }
        }
    }
}
