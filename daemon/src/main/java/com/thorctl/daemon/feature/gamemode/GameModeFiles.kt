package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.hardware.FanController
import dev.zacsweers.metro.Inject
import java.io.File

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

    fun markOn(on: Boolean) {
        if (on) {
            onFlag.parentFile?.mkdirs()
            onFlag.writeText("1")
        } else {
            onFlag.delete()
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

    /** Replaces the file in one step (write then rename), so the restore script never reads half of it. */
    fun saveOriginals(originals: Map<String, String>) {
        originalsFile.parentFile?.mkdirs()
        val tempFile = File(originalsFile.path + ".tmp")
        tempFile.writeText(originals.entries.joinToString("") { "${it.key} ${it.value}\n" })
        tempFile.renameTo(originalsFile)
    }

    fun deleteOriginals() {
        originalsFile.delete()
    }
}
