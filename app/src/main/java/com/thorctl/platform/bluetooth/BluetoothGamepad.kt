package com.thorctl.platform.bluetooth

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.domain.model.DaemonStatus
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
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.GamepadHostRepository
import com.thorctl.domain.repository.GamepadPairing
import com.thorctl.platform.overlay.OverlayMenu
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GamepadTransport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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
class BluetoothGamepad(
    private val app: Context,
    private val daemon: DaemonRepository,
    private val hosts: GamepadHostRepository,
    private val menu: Lazy<OverlayMenu>,
    @ApplicationScope private val scope: CoroutineScope,
) : GamepadPairing {
    private val tx = Executors.newSingleThreadExecutor { Thread(it, "gamepad-bt") }

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
        get() = app.getSystemService(BluetoothManager::class.java)?.adapter

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
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
        // Gamepad mode turns Bluetooth on if it was off: register once the adapter is up.
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (st == BluetoothAdapter.STATE_ON && wanted && proxy == null) open()
                if (st == BluetoothAdapter.STATE_OFF) {
                    proxy = null
                    setHost(null)
                }
            }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    private fun update(want: Boolean) {
        if (want == wanted) return
        wanted = want
        if (want) open() else close()
    }

    private fun open() {
        val a = adapter ?: return
        if (!a.isEnabled) return // the daemon is switching Bluetooth on; ACTION_STATE_CHANGED follows
        try {
            a.getProfileProxy(app, listener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            Timber.w(e, "no HID device profile")
        }
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
            val hid = p as BluetoothHidDevice
            if (!wanted) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
                return
            }
            proxy = hid
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Thor Controller",
                "Gamepad",
                "Thor Control",
                (BluetoothHidDevice.SUBCLASS1_NONE.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte(),
                GamepadHid.DESCRIPTOR,
            )
            try {
                if (!hid.registerApp(sdp, null, null, tx, callback)) Timber.w("registerApp refused")
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
                BluetoothProfile.STATE_DISCONNECTED -> if (host == null || host?.address == device.address) setHost(null)
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
        val addr = hosts.lastHost.value ?: return null
        val a = adapter ?: return null
        return a.bondedDevices?.firstOrNull { it.address == addr }
    }

    private fun setHost(d: BluetoothDevice?) {
        host = d
        val name = d?.let { it.alias ?: it.name ?: it.address } ?: ""
        daemon.send(DaemonCommand.GamepadBluetooth(d != null, name))
    }

    private fun close() {
        val p = proxy
        proxy = null
        host = null
        if (p != null) {
            tx.execute {
                try {
                    p.unregisterApp()
                } catch (_: Exception) {
                }
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, p)
            }
        }
    }

    /** A report from the daemon (payload, no report ID). */
    private fun send(r: ByteArray) {
        if (r.size != GamepadHid.PAYLOAD) return
        lastReport = r
        if (host == null) return
        // Only one hand-off in flight; a newer report replaces one still waiting.
        if (pending.getAndSet(r) == null) flush()
    }

    private fun flush() {
        tx.execute {
            val r = pending.getAndSet(null) ?: return@execute
            val p = proxy ?: return@execute
            val h = host ?: return@execute
            try {
                p.sendReport(h, GamepadHid.REPORT_ID, r)
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
        val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(i)
        } catch (e: Exception) {
            Timber.w(e, "discoverable prompt failed")
        }
    }
}
