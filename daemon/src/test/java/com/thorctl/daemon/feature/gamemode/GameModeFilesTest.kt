package com.thorctl.daemon.feature.gamemode

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameModeFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory by lazy { File(folder.root, "thorctl") }
    private val files by lazy { GameModeFiles(directory) }

    @Test
    fun marksGameModeOnAndOff() {
        assertFalse(files.isMarkedOn())
        files.markOn(true)
        assertTrue(files.isMarkedOn())
        assertEquals("1", File(directory, "game.on").readText())
        files.markOn(false)
        assertFalse(files.isMarkedOn())
    }

    @Test
    fun savesOriginalsInTheRestoreScriptsFormat() {
        files.saveOriginals(linkedMapOf("/sys/a/scaling_min_freq" to "300000", "/sys/kgsl/min_pwrlevel" to "8"))
        assertTrue(files.hasOriginals())
        assertEquals("/sys/a/scaling_min_freq 300000\n/sys/kgsl/min_pwrlevel 8\n", File(directory, "game.orig").readText())
        assertFalse(File(directory, "game.orig.tmp").exists())
        assertEquals(mapOf("/sys/a/scaling_min_freq" to "300000", "/sys/kgsl/min_pwrlevel" to "8"), files.loadOriginals())
    }

    @Test
    fun skipsMalformedLines() {
        directory.mkdirs()
        File(directory, "game.orig").writeText("/sys/a 1\n\nbroken\n/sys/b 2 3\n  /sys/c 4  \n")
        assertEquals(mapOf("/sys/a" to "1", "/sys/c" to "4"), files.loadOriginals())
    }

    @Test
    fun nothingSavedIsEmpty() {
        assertFalse(files.hasOriginals())
        assertEquals(emptyMap(), files.loadOriginals())
        files.saveOriginals(mapOf("/sys/a" to "1"))
        files.deleteOriginals()
        assertFalse(files.hasOriginals())
    }
}
