package com.thorctl.daemon.feature.recents

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpegFilesTest {
    private val directory = createTempDirectory("recents").toFile()

    @Test
    fun readsAPicture() {
        val bytes = ByteArray(100) { it.toByte() }
        val file = File(directory, "a.jpg").apply { writeBytes(bytes) }
        val jpeg = assertNotNull(JpegFiles.read(file))
        assertContentEquals(bytes, jpeg.open().use { it.readBytes() })
    }

    @Test
    fun skipsWhatIsNotAPicture() {
        assertNull(JpegFiles.read(null))
        assertNull(JpegFiles.read(File(directory, "missing.jpg")))
        assertNull(JpegFiles.read(directory))
        assertNull(JpegFiles.read(File(directory, "empty.jpg").apply { writeBytes(ByteArray(0)) }))
        assertNull(JpegFiles.read(File(directory, "big.jpg").apply { writeBytes(ByteArray(JpegFiles.MAX_IMAGE_BYTES.toInt() + 1)) }))
    }
}
