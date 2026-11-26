package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A Home press, from the moment it went down: the display, the app it went down in (null on the
 * home screen) and the capture of the screen into [file] started then.
 */
class HomePress(val display: Int, val origin: AppTask?, val file: File, private val shot: Future<Boolean>) {
    /** Whether the capture succeeded, waiting for it at most [WAIT_MS]. */
    fun captured(): Boolean = try {
        shot.get(WAIT_MS, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {
        false
    }

    companion object {
        const val WAIT_MS = 800L
    }
}
