package com.thorctl.daemon.core

import android.os.SystemProperties
import android.system.ErrnoException
import android.system.Os
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Small, allocation-light implementation on java.io. */
@Inject
@ContributesBinding(DaemonScope::class)
class AndroidSys : Sys {
    override fun read(path: String): String? = try {
        File(path).readText().trim()
    } catch (_: IOException) {
        null
    }

    override fun write(path: String, value: String): Boolean = try {
        FileOutputStream(path).use { it.write(value.toByteArray()) }
        true
    } catch (e: IOException) {
        Timber.w("write $path <- '$value' failed: ${e.message}")
        false
    }

    override fun writeBytes(path: String, bytes: ByteArray): Boolean = try {
        FileOutputStream(path).use { it.write(bytes) }
        true
    } catch (e: IOException) {
        Timber.w("write $path <- ${bytes.size} bytes failed: ${e.message}")
        false
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun list(directory: String): List<String> = File(directory).listFiles()?.map { it.path } ?: emptyList()

    override fun canonical(path: String): String? = try {
        File(path).canonicalPath
    } catch (_: IOException) {
        null
    }

    override fun readLink(path: String): String? = try {
        Os.readlink(path)
    } catch (_: ErrnoException) {
        null
    }

    override fun symlink(target: String, path: String): Boolean = try {
        Os.symlink(target, path)
        true
    } catch (e: ErrnoException) {
        Timber.w("symlink $path -> $target failed: ${e.message}")
        false
    }

    override fun remove(path: String): Boolean = try {
        Os.remove(path)
        true
    } catch (_: ErrnoException) {
        false
    }

    override fun mkdirs(directory: String): Boolean = File(directory).mkdirs()

    override fun prop(name: String): String = SystemProperties.get(name)

    override fun setProp(name: String, value: String): Boolean = try {
        SystemProperties.set(name, value)
        true
    } catch (e: RuntimeException) {
        // The property service refused it (SELinux, or a read-only property already set).
        Timber.w("setprop $name=$value failed: ${e.message}")
        false
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
    } catch (e: IOException) {
        Timber.w("sh '$command' failed: ${e.message}")
        null
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    }
}
