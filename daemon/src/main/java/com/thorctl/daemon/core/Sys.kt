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

    fun exists(path: String): Boolean

    /** Paths of the entries of a directory (empty if it isn't one). */
    fun list(dir: String): List<String>

    /** Where a symlink points, fully resolved; null if it doesn't resolve. */
    fun canonical(path: String): String?

    fun mkdirs(dir: String): Boolean

    /** System property value ("" if unset), without spawning getprop. */
    fun prop(name: String): String

    /** Runs a shell command as the daemon (root). Returns stdout, or null on failure/timeout. */
    fun sh(cmd: String, timeoutMs: Long = 5000): String?
}
