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
    } catch (error: Exception) {
        Timber.w("write $path <- '$value' failed: ${error.message}")
        false
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun list(directory: String): List<String> = File(directory).listFiles()?.map { it.path } ?: emptyList()

    override fun canonical(path: String): String? = try {
        File(path).canonicalPath
    } catch (_: Exception) {
        null
    }

    override fun mkdirs(directory: String): Boolean = File(directory).mkdirs()

    private val getProp by lazy { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }

    override fun prop(name: String): String = try {
        getProp.invoke(null, name) as String
    } catch (_: Throwable) {
        ""
    }

    override fun sh(command: String, timeoutMs: Long): String? = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            null
        } else {
            output.trim()
        }
    } catch (error: Exception) {
        Timber.w("sh '$command' failed: ${error.message}")
        null
    }
}
