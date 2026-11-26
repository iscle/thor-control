package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.Jpeg
import java.io.IOException
import java.util.concurrent.Executor

class PipeJpegExporter(private val writer: Executor) : JpegExporter {
    override fun export(jpeg: Jpeg?): ParcelFileDescriptor? {
        if (jpeg == null) return null
        val (read, write) = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: IOException) {
            return null
        }
        writer.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> jpeg.open().use { it.copyTo(out) } }
            } catch (_: IOException) {
                // The client closed its end unread, or went away.
            } finally {
                jpeg.close()
            }
        }
        return read
    }
}
