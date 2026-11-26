package com.thorctl.daemon

import android.os.Bundle
import com.thorctl.daemon.cli.CliCommands
import com.thorctl.daemon.cli.CliRequest
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.ipc.ClientBinder
import com.thorctl.protocol.ipc.IThorDaemon
import com.thorctl.protocol.ipc.ThorIpc
import com.thorctl.protocol.ipc.send
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.closeImages
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Debug client: runs one command ([CliCommands.USAGE]) and prints what the daemon sends back for
 * a while (WAIT_MS, default 1.5 s). It gets the daemon's binder from the app, so the app must be
 * running.
 *
 *   CLASSPATH=<apk> app_process /system/bin com.thorctl.daemon.Cli state
 *   CLASSPATH=<apk> app_process /system/bin com.thorctl.daemon.Cli brightness bottom 0.4
 */
object Cli {
    @Volatile
    private var state: DaemonState? = null
    private val gotState = CountDownLatch(1)

    @JvmStatic
    fun main(arguments: Array<String>) {
        if (arguments.isEmpty()) usage()
        val waitMs = System.getenv("WAIT_MS")?.toLongOrNull() ?: 1500L
        val reply = try {
            SystemServices().callAppProvider(ThorIpc.METHOD_GET, Bundle())
        } catch (error: Exception) {
            System.err.println("can't reach the app's provider ${ThorIpc.AUTHORITY}: $error")
            exitProcess(1)
        }
        val daemon = reply?.getBinder(ThorIpc.KEY_BINDER)?.let { IThorDaemon.Stub.asInterface(it) } ?: run {
            System.err.println("the app has no connection to the daemon")
            exitProcess(1)
        }
        if (daemon.version != IThorDaemon.VERSION) System.err.println("daemon speaks version ${daemon.version}, this CLI ${IThorDaemon.VERSION}")
        val client = ClientBinder(::print)
        daemon.register(client)
        // Some commands change part of the state ("leds on"): wait for it.
        gotState.await(2, TimeUnit.SECONDS)
        when (val request = CliCommands.parse(arguments.toList(), state) ?: usage()) {
            CliRequest.State -> {}
            CliRequest.Stats -> daemon.setStatsSubscribed(client, true)
            is CliRequest.Send -> daemon.send(request.command)
        }
        Thread.sleep(waitMs)
        daemon.unregister(client)
        exitProcess(0)
    }

    private fun usage(): Nothing {
        System.err.println(CliCommands.USAGE)
        exitProcess(2)
    }

    private fun print(event: DaemonEvent) {
        when (event) {
            is DaemonEvent.StateChanged -> {
                state = event.state
                gotState.countDown()
                println(event.state)
            }
            is DaemonEvent.GamepadReport -> println("GamepadReport(${event.report.joinToString(" ") { "%02x".format(it) }})")
            else -> println(event)
        }
        event.closeImages() // images arrive as pipes: drop them unread
    }
}
