package com.thorctl.platform.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.GamepadHostRepository
import com.thorctl.domain.repository.GamepadPairing
import com.thorctl.platform.overlay.OverlayMenu
import com.thorctl.platform.startup.ProcessObserver
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GamepadTransport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Gamepad mode over Bluetooth. While the daemon's gamepad mode is on with Bluetooth, the Thor is
 * registered as a Bluetooth HID device (a gamepad, with the same report as over USB) and the
 * daemon's reports are sent to the connected host. On start it reconnects to the last host, as
 * Bluetooth gamepads do; a new host pairs from its own Bluetooth settings while the Thor is
 * discoverable ([makeDiscoverable]).
 */
@SuppressLint("MissingPermission")
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ProcessObserver>())
class BluetoothGamepad(
    private val context: Context,
    private val daemon: DaemonRepository,
    private val hosts: GamepadHostRepository,
    private val menu: Lazy<OverlayMenu>,
) : GamepadPairing, ProcessObserver {
    private val radioExecutor = Executors.newSingleThreadExecutor { Thread(it, "gamepad-bt") }

    private var wanted = false

    @Volatile
    private var proxy: BluetoothHidDevice? = null

    @Volatile
    private var host: BluetoothDevice? = null

    @Volatile
    private var lastReport = GamepadHid.neutral()

    /** Newest report not yet handed to the radio (newest wins, nothing queues up). */
    private val pending = AtomicReference<ByteArray?>(null)

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(scope: CoroutineScope) {
        scope.launch {
            daemon.status
                .flatMapLatest { status ->
                    when (status) {
                        DaemonStatus.Disconnected -> flowOf(false)
                        is DaemonStatus.Connected -> status.state.map { it.gamepad.on && it.gamepad.transport == GamepadTransport.BLUETOOTH }
                    }
                }
                .distinctUntilChanged()
                .collect(::update)
        }
        // Reports go straight to the radio's thread, not through the main thread.
        scope.launch(Dispatchers.Default) { daemon.gamepadReports.collect(::send) }
    }

    /**
     * While gamepad mode wants Bluetooth: gamepad mode turns Bluetooth on if it was off, so
     * register once the adapter is up; if it goes off, the host is gone.
     */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (adapterState == BluetoothAdapter.STATE_ON && wanted && proxy == null) open()
            if (adapterState == BluetoothAdapter.STATE_OFF) {
                proxy = null
                setHost(null)
            }
        }
    }

    private fun update(want: Boolean) {
        if (want == wanted) return
        wanted = want
        if (want) {
            context.registerReceiver(
                adapterReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
            open()
        } else {
            context.unregisterReceiver(adapterReceiver)
            close()
        }
    }

    private fun open() {
        val bluetoothAdapter = adapter ?: return
        if (!bluetoothAdapter.isEnabled) return // the daemon is switching Bluetooth on; ACTION_STATE_CHANGED follows
        try {
            bluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            Timber.w(e, "no HID device profile")
        }
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, serviceProxy: BluetoothProfile) {
            val hidDevice = serviceProxy as BluetoothHidDevice
            if (!wanted) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
                return
            }
            proxy = hidDevice
            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                "Thor Controller",
                "Gamepad",
                "Thor Control",
                (BluetoothHidDevice.SUBCLASS1_NONE.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte(),
                GamepadHid.DESCRIPTOR,
            )
            try {
                if (!hidDevice.registerApp(
                        sdpSettings,
                        null,
                        null,
                        radioExecutor,
                        callback
                    )
                ) Timber.w("registerApp refused")
            } catch (e: Exception) {
                Timber.w(e, "registerApp failed")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            proxy = null
            setHost(null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Timber.i("registered=$registered plugged=${pluggedDevice?.address}")
            if (!registered) {
                setHost(null)
                return
            }
            // Gamepads reconnect to their host themselves.
            val target = pluggedDevice ?: lastHost() ?: return
            try {
                proxy?.connect(target)
            } catch (e: Exception) {
                Timber.w(e, "reconnect failed")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Timber.i("host ${device.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hosts.remember(device.address)
                    setHost(device)
                    // The host starts from the current controller state.
                    pending.set(lastReport)
                    flush()
                }

                BluetoothProfile.STATE_DISCONNECTED -> if (host == null || host?.address == device.address) setHost(
                    null
                )
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            try {
                proxy?.replyReport(device, type, id, lastReport)
            } catch (_: Exception) {
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            try {
                proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (_: Exception) {
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            setHost(null)
        }
    }

    private fun lastHost(): BluetoothDevice? {
        val address = hosts.lastHost.value ?: return null
        val bluetoothAdapter = adapter ?: return null
        return bluetoothAdapter.bondedDevices?.firstOrNull { it.address == address }
    }

    private fun setHost(device: BluetoothDevice?) {
        host = device
        val name = device?.let { it.alias ?: it.name ?: it.address } ?: ""
        daemon.send(DaemonCommand.GamepadBluetooth(device != null, name))
    }

    private fun close() {
        val oldProxy = proxy
        proxy = null
        host = null
        if (oldProxy != null) {
            radioExecutor.execute {
                try {
                    oldProxy.unregisterApp()
                } catch (_: Exception) {
                }
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, oldProxy)
            }
        }
    }

    /** A report from the daemon (payload, no report ID). */
    private fun send(report: ByteArray) {
        if (report.size != GamepadHid.PAYLOAD) return
        lastReport = report
        if (host == null) return
        // Only one hand-off in flight; a newer report replaces one still waiting.
        if (pending.getAndSet(report) == null) flush()
    }

    private fun flush() {
        radioExecutor.execute {
            val report = pending.getAndSet(null) ?: return@execute
            val currentProxy = proxy ?: return@execute
            val currentHost = host ?: return@execute
            try {
                currentProxy.sendReport(currentHost, GamepadHid.REPORT_ID, report)
            } catch (e: Exception) {
                Timber.w(e, "sendReport failed")
            }
        }
    }

    /**
     * Lets a new host find the Thor: Android's "make visible" prompt (two minutes). The quick menu
     * closes first, since it covers the prompt.
     */
    override fun makeDiscoverable() {
        menu.value.hide()
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "discoverable prompt failed")
        }
    }
}
