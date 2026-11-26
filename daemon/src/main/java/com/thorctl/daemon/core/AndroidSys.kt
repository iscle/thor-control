package com.thorctl.daemon.core

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Small, allocation-light implementation on java.io. */
@Inject
@ContributesBinding(DaemonScope::class)
class AndroidSys : Sys {
    override fun read(path: String): String? = try {
        File(path).readText().trim()
    } catch (_: Exception) {
        null
    }

    override fun write(path: String, value: String): Boolean = try {
        FileOutputStream(path).use { it.write(value.toByteArray()) }
        true
    } catch (e: Exception) {
        Timber.w("write $path <- '$value' failed: ${e.message}")
        false
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun list(dir: String): List<String> = File(dir).listFiles()?.map { it.path } ?: emptyList()

    override fun canonical(path: String): String? = try {
        File(path).canonicalPath
    } catch (_: Exception) {
        null
    }

    override fun mkdirs(dir: String): Boolean = File(dir).mkdirs()

    private val getProp by lazy { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }

    override fun prop(name: String): String = try {
        getProp.invoke(null, name) as String
    } catch (_: Throwable) {
        ""
    }

    override fun sh(cmd: String, timeoutMs: Long): String? = try {
        val p = ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            null
        } else {
            out.trim()
        }
    } catch (e: Exception) {
        Timber.w("sh '$cmd' failed: ${e.message}")
        null
    }
}
