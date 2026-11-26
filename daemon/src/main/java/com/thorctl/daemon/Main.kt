package com.thorctl.daemon

import android.os.Process
import com.thorctl.daemon.core.DaemonLogTree
import com.thorctl.daemon.di.DaemonGraph
import dev.zacsweers.metro.createGraph
import timber.log.Timber
import java.io.File
import java.io.IOException
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
        exitWhenMainThreadDies()
        protectFromOomKiller()
        Timber.i("thorctld starting, pid=${Process.myPid()} uid=${Process.myUid()}")
        createGraph<DaemonGraph>().daemon.run()
    }

    /**
     * A crash on a worker thread is logged and survived; one on the main thread (the daemon's
     * startup) ends the process, and the supervisor starts a fresh one.
     */
    private fun exitWhenMainThreadDies() {
        val mainThread = Thread.currentThread()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Timber.w(e, "uncaught exception in ${thread.name}")
            if (thread == mainThread) exitProcess(1)
        }
    }

    /** Out of the kernel OOM killer's reach (lmkd only manages app processes anyway). */
    private fun protectFromOomKiller() {
        try {
            File("/proc/self/oom_score_adj").writeText(OOM_SCORE_ADJ_NEVER_KILL)
        } catch (e: IOException) {
            Timber.w(e, "could not set oom_score_adj")
        }
    }

    private const val OOM_SCORE_ADJ_NEVER_KILL = "-1000"
}
