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
    fun main(args: Array<String>) {
        DaemonLogTree.plant()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Timber.w(e, "uncaught exception in ${t.name}")
            if (t.name == "main") exitProcess(1)
        }
        try {
            // Immune to the kernel OOM killer; lmkd only manages app processes anyway.
            File("/proc/self/oom_score_adj").writeText("-1000")
        } catch (e: Exception) {
            Timber.w(e, "could not set oom_score_adj")
        }
        Timber.i("thorctld starting, pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()}")
        try {
            createGraph<DaemonGraph>().daemon.run()
        } catch (e: Throwable) {
            Timber.w(e, "daemon died")
            exitProcess(1)
        }
    }
}
