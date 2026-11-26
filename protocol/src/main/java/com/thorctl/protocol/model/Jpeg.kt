package com.thorctl.protocol.model

import java.io.Closeable
import java.io.InputStream

/**
 * A JPEG passed from the daemon to the app (a screenshot or a task preview). Over Binder it is a
 * pipe, so it can be read once: [open] it and close the stream, or [close] it unread.
 */
interface Jpeg : Closeable {
    fun open(): InputStream
}
