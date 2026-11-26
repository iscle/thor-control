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
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    private val _status = MutableStateFlow<DaemonStatus>(DaemonStatus.Disconnected)
    override val status: StateFlow<DaemonStatus> = _status


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

    /** Registered with the daemon (its first state may still be on the way). */
    @Volatile
    private var registered = false

    /**
     * The connection being set up: registering, the daemon sends its state and a telemetry
     * reading. [DaemonStatus.Connected] waits for both, so neither is ever missing on screen.
     */
    private var pendingState: DaemonState? = null
    private var pendingStats: Stats? = null

    /** The connection's flows, once [DaemonStatus.Connected]. */
    @Volatile
    private var state: MutableStateFlow<DaemonState>? = null

    @Volatile
    private var stats: MutableStateFlow<Stats>? = null

    private val death = IBinder.DeathRecipient { calls.execute { disconnected("daemon died") } }

    override fun onDaemonBinder(binder: IBinder) {
        calls.execute { connect(binder) }
    }

    override fun currentBinder(): IBinder? = binder.takeIf { registered }

    private fun connect(newBinder: IBinder) {
        if (newBinder == binder && registered) {
            // The daemon offers its binder only while it doesn't count us as registered: it
            // dropped us (a failed call), so register again. Registering twice changes nothing.
            try {
                daemon?.register(client)
            } catch (error: RemoteException) {
                Timber.w("registering again failed: ${error.javaClass.simpleName}")
                disconnected("register failed")
            }
            return
        }
        if (binder != null) disconnected("daemon restarted")
        val newDaemon = asInterface(newBinder)
        try {
            val version = newDaemon.version
            if (version != IThorDaemon.VERSION) {
                // A daemon from another APK version (an update not fully applied yet): its calls
                // may not mean what ours do. Wait for the matching one.
                Timber.w("daemon speaks version $version, the app ${IThorDaemon.VERSION}: not connecting")
                return
            }
            newBinder.linkToDeath(death, 0)
            // Set before registering: the daemon's first state can arrive before register returns.
            binder = newBinder
            daemon = newDaemon
            newDaemon.register(client)
            if (statsWanted > 0) newDaemon.setStatsSubscribed(client, true)
            registered = true
            Timber.i("connected to the daemon")
        } catch (error: RemoteException) {
            Timber.w(error, "connecting to the daemon failed")
            disconnected("connect failed")
        }
    }

    private fun disconnected(why: String) {
        val lostBinder = binder ?: return
        try {
            lostBinder.unlinkToDeath(death, 0)
        } catch (_: Exception) {
        }
        binder = null
        daemon = null
        synchronized(this) {
            registered = false
            pendingState = null
            pendingStats = null
            state = null
            stats = null
            _status.value = DaemonStatus.Disconnected
        }
        Timber.i("disconnected from the daemon: $why")
    }

    private fun onEvent(event: DaemonEvent) {
        when (event) {
            // Only from the daemon we're connected to: a late call from a dead one changes nothing.
            is DaemonEvent.StateChanged -> onState(event.state)
            is DaemonEvent.StatsUpdated -> onStats(event.stats)
            is DaemonEvent.GamepadReport -> _gamepadReports.tryEmit(event.report)
            else -> if (_events.subscriptionCount.value == 0 || !_events.tryEmit(event)) {
                // Nobody would read its images: free the daemon's pipes now.
                Timber.w("event dropped, nobody keeps up: $event")
                event.closeImages()
            }
        }
    }

    private fun onState(newState: DaemonState) = synchronized(this) {
        if (daemon == null) return // a late call from a dead daemon
        val current = state
        if (current != null) current.value = newState else pendingState = newState
        publishWhenComplete()
    }

    private fun onStats(reading: Stats) = synchronized(this) {
        if (daemon == null) return
        val current = stats
        if (current != null) current.value = reading else pendingStats = reading
        publishWhenComplete()
    }

    /** The first state and the first reading of a connection make it [DaemonStatus.Connected]. */
    private fun publishWhenComplete() {
        val firstState = pendingState ?: return
        val firstStats = pendingStats ?: return
        pendingState = null
        pendingStats = null
        val stateFlow = MutableStateFlow(firstState)
        val statsFlow = MutableStateFlow(firstStats)
        state = stateFlow
        stats = statsFlow
        _status.value = DaemonStatus.Connected(stateFlow, statsFlow)
    }

    override fun send(command: DaemonCommand) {
        optimistic(command)
        call { it.send(command) }
    }

    /** Commands whose effect sliders and switches must show at once, before the daemon echoes it. */
    private fun optimistic(command: DaemonCommand) {
        val flow = state ?: return
        flow.update { current ->
            when (command) {
                is DaemonCommand.SetLeds -> current.copy(leds = command.state)
                is DaemonCommand.SetGameMode -> current.copy(game = current.game.copy(enabled = command.on))
                is DaemonCommand.SetGameOptions -> current.copy(game = current.game.copy(options = command.options))
                else -> return
            }
        }
    }

    override fun subscribeStats(on: Boolean) {
        synchronized(this) { statsWanted = (statsWanted + if (on) 1 else -1).coerceAtLeast(0) }
        call { it.setStatsSubscribed(client, statsWanted > 0) }
    }

    private fun call(block: (IThorDaemon) -> Unit) {
        calls.execute {
            val currentDaemon = daemon ?: return@execute
            try {
                block(currentDaemon)
            } catch (error: RemoteException) {
                // A oneway call fails when the daemon's buffer is full too: only a dead binder
                // ends the connection (its death recipient then fires as well).
                Timber.w("call to the daemon failed: ${error.javaClass.simpleName}")
                if (binder?.isBinderAlive != true) disconnected("daemon gone")
            }
        }
    }
}
