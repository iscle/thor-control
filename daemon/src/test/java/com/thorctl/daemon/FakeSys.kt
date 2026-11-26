package com.thorctl.daemon

import com.thorctl.daemon.core.Sys

/** An in-memory sysfs: files are paths with contents, directories exist through their files. */
class FakeSys : Sys {
    val files = LinkedHashMap<String, String>()
    val links = HashMap<String, String>()
    val writes = mutableListOf<Pair<String, String>>()
    val commands = mutableListOf<String>()
    val props = HashMap<String, String>()
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

    override fun list(dir: String): List<String> =
        (files.keys + links.keys).filter { it.startsWith("$dir/") }.map { "$dir/" + it.removePrefix("$dir/").substringBefore('/') }.distinct()

    override fun canonical(path: String): String? = links[path] ?: path.takeIf { exists(it) }

    override fun mkdirs(dir: String): Boolean = true

    override fun prop(name: String): String = props[name].orEmpty()

    override fun sh(cmd: String, timeoutMs: Long): String? {
        commands += cmd
        return shell(cmd)
    }
}
