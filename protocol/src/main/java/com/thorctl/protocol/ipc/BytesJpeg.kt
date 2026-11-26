package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.Jpeg
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A JPEG held in memory, readable as often as needed (once per client). The daemon reads its
 * screenshots eagerly: the file may be overwritten by the next capture before a pipe is drained.
 */
class BytesJpeg(private val bytes: ByteArray) : Jpeg {
    override fun open(): InputStream = ByteArrayInputStream(bytes)

    override fun close() {}

    override fun toString(): String = "BytesJpeg(${bytes.size} bytes)"
}
