package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.ThorDisplays
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreencapCapturerTest {
    private val directory = createTempDirectory("recents").toFile()
    private val sys = FakeSys()
    private val capturer = ScreencapCapturer(sys, PhysicalDisplayIds(ScreenshotTestDisplays.create()))

    /** screencap writes [size] bytes to the path after -j. */
    private fun screencapWrites(size: Int) {
        sys.shell = { command ->
            File(command.substringAfter("-j ")).writeBytes(ByteArray(size))
            ""
        }
    }

    @Test
    fun capturesThePhysicalDisplayThroughATemporaryFile() {
        screencapWrites(5000)
        val file = File(directory, "pictures/home.jpg")
        assertTrue(capturer.capture(1, file))
        assertEquals(listOf("screencap -d 222 -j ${file.path}.tmp"), sys.commands)
        assertEquals(5000L, file.length())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun aTinyOutputIsNoPicture() {
        val file = File(directory, "home.jpg").apply { writeBytes(ByteArray(3000)) }
        screencapWrites(ScreencapCapturer.MIN_BYTES.toInt() - 1)
        assertFalse(capturer.capture(0, file))
        assertEquals(3000L, file.length())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun nothingWrittenIsNoPicture() {
        assertFalse(capturer.capture(0, File(directory, "home.jpg")))
        assertEquals(1, sys.commands.size)
    }

    @Test
    fun unknownScreensAreNotCaptured() {
        val unknown = ScreencapCapturer(sys, PhysicalDisplayIds(ThorDisplays.unknown()))
        assertFalse(unknown.capture(0, File(directory, "home.jpg")))
        assertTrue(sys.commands.isEmpty())
    }
}
