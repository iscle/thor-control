package com.thorctl.daemon.di

import com.thorctl.daemon.Daemon
import com.thorctl.daemon.Main
import com.thorctl.daemon.ipc.AppUidLookup
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.ipc.ClientRegistry
import com.thorctl.daemon.ipc.DaemonEndpointImpl
import com.thorctl.daemon.ipc.DataDirAppUidLookup
import com.thorctl.protocol.ipc.DaemonBinder
import com.thorctl.protocol.ipc.JpegExporter
import com.thorctl.protocol.ipc.PipeJpegExporter
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/** The root daemon's object graph, created once by [Main]. */
@DependencyGraph(DaemonScope::class)
interface DaemonGraph {
    val daemon: Daemon

    @Binds
    val ClientRegistry.bindLink: ClientLink

    @Binds
    val DataDirAppUidLookup.bindAppUid: AppUidLookup

    @Provides
    @ConfigFile
    fun configFile(): File = File("/data/adb/thorctl/config.json")

    /** Periodic work (fan, stats, housekeeping) and short delays. */
    @Provides
    @SingleIn(DaemonScope::class)
    fun scheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(2) { Thread(it, "sched") }

    /** Fills the image pipes handed to clients (a client may read slowly, so threads come and go). */
    @Provides
    @SingleIn(DaemonScope::class)
    fun jpegExporter(): JpegExporter = PipeJpegExporter(Executors.newCachedThreadPool { Thread(it, "ipc-images").apply { isDaemon = true } })

    @Provides
    @SingleIn(DaemonScope::class)
    fun daemonBinder(endpoint: DaemonEndpointImpl): DaemonBinder = DaemonBinder(endpoint)
}
