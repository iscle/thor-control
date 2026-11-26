package com.thorctl.daemon

import com.thorctl.daemon.core.Sys

/** An in-memory sysfs: files are paths with contents, directories exist through their files. */
class FakeSys : Sys {
    val files = LinkedHashMap<String, String>()
    val links = HashMap<String, String>()
    val writes = mutableListOf<Pair<String, String>>()
    val commands = mutableListOf<String>()
    val properties = HashMap<String, String>()
    var shell: (String) -> String? = { null }

    operator fun set(path: String, value: Any) {
        files[path] = value.toString()
    }

    operator fun get(path: String): String? = files[path]

    override fun read(path: String): String? = files[path]?.trim()

    override fun write(path: String, value: String): Boolean {
        files[path] = value
        writes += path to value
        return true
    }

    override fun exists(path: String): Boolean = path in files || files.keys.any { it.startsWith("$path/") } || path in links

    override fun list(directory: String): List<String> =
        (files.keys + links.keys).filter { it.startsWith("$directory/") }.map { "$directory/" + it.removePrefix("$directory/").substringBefore('/') }.distinct()

    override fun canonical(path: String): String? = links[path] ?: path.takeIf { exists(it) }

    override fun mkdirs(directory: String): Boolean = true

    override fun prop(name: String): String = properties[name].orEmpty()

    override fun sh(command: String, timeoutMs: Long): String? {
        commands += command
        return shell(command)
    }
}
