package com.thorctl.daemon

import com.thorctl.daemon.core.DaemonLogTree
import com.thorctl.daemon.di.DaemonGraph
import dev.zacsweers.metro.createGraph
import timber.log.Timber
import java.io.File
import kotlin.system.exitProcess

/**
 * Root daemon entry point. Started by the Magisk module's service.sh supervisor:
 *
 *   CLASSPATH=<apk> app_process /system/bin --nice-name=thorctld com.thorctl.daemon.Main
 *
 * If it exits for any reason the supervisor restores kernel fan control and restarts it.
 */
object Main {
    @JvmStatic
    fun main(arguments: Array<String>) {
        DaemonLogTree.plant()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Timber.w(error, "uncaught exception in ${thread.name}")
            if (thread.name == "main") exitProcess(1)
        }
        try {
            // Immune to the kernel OOM killer; lmkd only manages app processes anyway.
            File("/proc/self/oom_score_adj").writeText("-1000")
        } catch (error: Exception) {
            Timber.w(error, "could not set oom_score_adj")
        }
        Timber.i("thorctld starting, pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()}")
        try {
            createGraph<DaemonGraph>().daemon.run()
        } catch (error: Throwable) {
            Timber.w(error, "daemon died")
            exitProcess(1)
        }
    }
}
