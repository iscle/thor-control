package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.hardware.FanController
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Game mode's files in /dev/thorctl, a tmpfs: they outlive a daemon restart but not a reboot.
 *
 *  - game.on: game mode is on, so a restarted daemon keeps it on.
 *  - game.orig: every sysfs value game mode overwrote, as it was before, one "path value" per
 *    line. magisk/restore-kernel.sh writes them back (and deletes the file) if the daemon dies.
 */
class GameModeFiles(directory: File) {
    @Inject
    constructor() : this(File(FanController.STATE_DIRECTORY))

    private val onFlag = File(directory, "game.on")
    private val originalsFile = File(directory, "game.orig")

    fun isMarkedOn(): Boolean = onFlag.exists()

    /** Writes or deletes game.on. Returns false if the file couldn't be written or deleted. */
    fun markOn(on: Boolean): Boolean {
        if (!on) return onFlag.delete() || !onFlag.exists()
        onFlag.parentFile?.mkdirs()
        return try {
            onFlag.writeText("1")
            true
        } catch (e: IOException) {
            Timber.w(e, "writing ${onFlag.path} failed")
            false
        }
    }

    fun hasOriginals(): Boolean = originalsFile.exists()

    fun loadOriginals(): Map<String, String> {
        if (!originalsFile.exists()) return emptyMap()
        return originalsFile.readLines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
        }.toMap()
    }

    /**
     * Replaces the file in one step (write then rename), so the restore script never reads half of
     * it. Returns false if it couldn't be saved; the previous file, if any, is then left as it was.
     */
    fun saveOriginals(originals: Map<String, String>): Boolean {
        originalsFile.parentFile?.mkdirs()
        val tempFile = File(originalsFile.path + ".tmp")
        return try {
            tempFile.writeText(originals.entries.joinToString("") { "${it.key} ${it.value}\n" })
            tempFile.renameTo(originalsFile).also { renamed -> if (!renamed) Timber.w("renaming ${tempFile.path} failed") }
        } catch (e: IOException) {
            Timber.w(e, "writing ${tempFile.path} failed")
            false
        }
    }

    fun deleteOriginals() {
        originalsFile.delete()
    }
}
