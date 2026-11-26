package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.Jpeg

/*
 * Images go over Binder as pipes, not as bytes in the parcel (screenshots are far larger than a
 * binder transaction may be) and not as the files themselves (they live in root-only directories,
 * which SELinux doesn't let the app read even through a passed descriptor).
 */

/** Daemon side: turns a [Jpeg] into the read end of a pipe it fills on [writer]. */
fun interface JpegExporter {
    fun export(jpeg: Jpeg?): ParcelFileDescriptor?
}
