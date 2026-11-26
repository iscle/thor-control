package com.thorctl.daemon.core

import java.io.File

/**
 * The daemon's window on the kernel and the shell: sysfs / procfs files, system properties and
 * shell commands. Everything here runs as root. Behind an interface so the hardware logic can be
 * tested against an in-memory file system.
 */
interface Sys {
    /** File contents, trimmed; null if it can't be read. */
    fun read(path: String): String?

    /** First whitespace-separated number in the file. */
    fun readInt(path: String): Int? = read(path)?.split(' ', '\n')?.firstOrNull()?.toIntOrNull()

    fun readLong(path: String): Long? = read(path)?.split(' ', '\n')?.firstOrNull()?.toLongOrNull()

    fun write(path: String, value: String): Boolean

    /** Writes raw bytes (a binary attribute, like a HID report descriptor). */
    fun writeBytes(path: String, bytes: ByteArray): Boolean

    fun exists(path: String): Boolean

    fun isDirectory(path: String): Boolean

    /** Paths of the entries of a directory (empty if it isn't one). */
    fun list(directory: String): List<String>

    /** Where a symlink points, fully resolved; null if it doesn't resolve. */
    fun canonical(path: String): String?

    /** Where a symlink points, as stored in the link (not resolved); null if it isn't one. */
    fun readLink(path: String): String?

    /** Creates the symlink [path], pointing at [target]. */
    fun symlink(target: String, path: String): Boolean

    /** Removes a file, a symlink or an empty directory (rmdir); false if that failed. */
    fun remove(path: String): Boolean

    fun mkdirs(directory: String): Boolean

    /** System property value ("" if unset), without spawning getprop. */
    fun prop(name: String): String

    /** Sets a system property; false if the system refused. */
    fun setProp(name: String, value: String): Boolean

    /** Runs a shell command as the daemon (root). Returns stdout, or null on failure/timeout. */
    fun sh(command: String, timeoutMs: Long = 5000): String?
}
