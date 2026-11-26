package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.Jpeg
import java.io.IOException
import java.io.InputStream

object PipeJpegImporter : JpegImporter {
    override fun import(pfd: ParcelFileDescriptor?): Jpeg? = pfd?.let(::PipeJpeg)
}

private class PipeJpeg(private val pfd: ParcelFileDescriptor) : Jpeg {
    override fun open(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)

    override fun close() {
        try {
            pfd.close()
        } catch (_: IOException) {
        }
    }
}
