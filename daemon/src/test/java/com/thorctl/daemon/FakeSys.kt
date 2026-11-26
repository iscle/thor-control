package com.thorctl.daemon

import com.thorctl.daemon.core.Sys

/**
 * An in-memory sysfs: files are paths with contents, directories exist through their files (or
 * through [mkdirs]). Binary writes are kept as ISO-8859-1 text, one char per byte ([bytes]).
 */
class FakeSys : Sys {
    val files = LinkedHashMap<String, String>()
    val links = HashMap<String, String>()
    val directories = LinkedHashSet<String>()
    val writes = mutableListOf<Pair<String, String>>()
    val removals = mutableListOf<String>()
    val commands = mutableListOf<String>()
    val properties = HashMap<String, String>()
    var shell: (String) -> String? = { null }

    operator fun set(path: String, value: Any) {
        files[path] = value.toString()
    }

    operator fun get(path: String): String? = files[path]

    /** What [writeBytes] put in [path]. */
    fun bytes(path: String): ByteArray? = files[path]?.toByteArray(Charsets.ISO_8859_1)

    override fun read(path: String): String? = files[path]?.trim()

    override fun write(path: String, value: String): Boolean {
        files[path] = value
        writes += path to value
        return true
    }

    override fun writeBytes(path: String, bytes: ByteArray): Boolean = write(path, String(bytes, Charsets.ISO_8859_1))

    override fun exists(path: String): Boolean =
        path in files || files.keys.any { it.startsWith("$path/") } || path in links || path in directories

    override fun isDirectory(path: String): Boolean =
        path in directories || (files.keys + links.keys + directories).any { it.startsWith("$path/") }

    override fun list(directory: String): List<String> =
        (files.keys + links.keys + directories).filter { it.startsWith("$directory/") }
            .map { "$directory/" + it.removePrefix("$directory/").substringBefore('/') }.distinct()

    override fun canonical(path: String): String? = links[path] ?: path.takeIf { exists(it) }

    override fun readLink(path: String): String? = links[path]

    override fun symlink(target: String, path: String): Boolean {
        if (exists(path)) return false
        links[path] = target
        return true
    }

    /** Like remove(3): a directory goes only when it is empty. Every attempt is kept in [removals]. */
    override fun remove(path: String): Boolean {
        removals += path
        if ((files.keys + links.keys + directories).any { it.startsWith("$path/") }) return false
        return (files.remove(path) != null) or (links.remove(path) != null) or directories.remove(path)
    }

    override fun mkdirs(directory: String): Boolean {
        directories += directory
        return true
    }

    override fun prop(name: String): String = properties[name].orEmpty()

    override fun setProp(name: String, value: String): Boolean {
        properties[name] = value
        return true
    }

    override fun sh(command: String, timeoutMs: Long): String? {
        commands += command
        return shell(command)
    }
}
