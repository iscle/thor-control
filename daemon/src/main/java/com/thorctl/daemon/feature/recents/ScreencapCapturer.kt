package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.File

/** Captures the screen with the shell's screencap in JPEG mode (~140 ms), through a temporary file. */
@Inject
@ContributesBinding(DaemonScope::class)
class ScreencapCapturer(private val sys: Sys, private val ids: PhysicalDisplayIds) : ScreenCapturer {
    override fun capture(display: Int, file: File): Boolean {
        val physicalDisplayId = ids.of(display)
        if (physicalDisplayId.isEmpty()) return false
        file.parentFile?.mkdirs()
        val tempFile = File(file.path + ".tmp")
        tempFile.delete()
        sys.sh("screencap -d $physicalDisplayId -j ${tempFile.path}", TIMEOUT_MS)
        if (!tempFile.exists() || tempFile.length() < MIN_BYTES) {
            tempFile.delete()
            return false
        }
        return tempFile.renameTo(file)
    }

    companion object {
        const val TIMEOUT_MS = 3000L

        /** Anything smaller is not a picture of the screen. */
        const val MIN_BYTES = 1000L
    }
}
