package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.Jpeg

/** App side: wraps a received pipe as a [Jpeg]. */
fun interface JpegImporter {
    fun import(pfd: ParcelFileDescriptor?): Jpeg?
}
