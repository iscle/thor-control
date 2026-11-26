package com.thorctl.daemon.feature.recents

import com.thorctl.protocol.ipc.BytesJpeg
import com.thorctl.protocol.model.Jpeg
import java.io.File
import java.io.IOException

/** Reads screen pictures and task previews into memory for sending to the app. */
object JpegFiles {
    const val MAX_IMAGE_BYTES = 1_200_000L

    /** The picture in [file]; null if there is none, or it is empty, too big or unreadable. */
    fun read(file: File?): Jpeg? = try {
        if (file == null || !file.isFile || file.length() == 0L || file.length() > MAX_IMAGE_BYTES) null else BytesJpeg(file.readBytes())
    } catch (_: IOException) {
        null
    }
}
