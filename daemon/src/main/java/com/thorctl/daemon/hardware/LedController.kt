package com.thorctl.daemon.hardware

import timber.log.Timber
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Joystick ring lights: /sys/class/leds/{left,right}:stick:{0..3}, multicolor class devices
 * (multi_intensity "R G B", brightness 0..255). Writes go over I2C (~0.5 ms each), so animations
 * run at 25 fps and only changed values are written.
 */
@Inject
@SingleIn(DaemonScope::class)
class LedController(private val sys: Sys) : Thread("leds") {
    private val left = list("left:stick")
    private val right = list("right:stick")
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val lastWritten = HashMap<String, String>()

    @Volatile
    private var state = LedState()

    @Volatile
    private var awake = true

    @Volatile
    private var dirty = true

    val available: Boolean get() = left.isNotEmpty() || right.isNotEmpty()

    private fun list(prefix: String): List<String> =
        sys.list("/sys/class/leds")
            .filter { it.substringAfterLast('/').let { n -> n == prefix || n.startsWith("$prefix:") } }
            .sorted()

    fun apply(s: LedState) {
        lock.withLock {
            state = s
            dirty = true
            changed.signalAll()
        }
    }

    fun setAwake(value: Boolean) {
        if (awake == value) return
        lock.withLock {
            awake = value
            dirty = true
            changed.signalAll()
        }
    }

    /** Re-write everything on the next frame (e.g. after another app touched the nodes). */
    fun invalidate() {
        lock.withLock {
            lastWritten.clear()
            dirty = true
            changed.signalAll()
        }
    }

    private fun writeLed(path: String, rgb: Int, brightness: Int) {
        val mi = "${(rgb shr 16) and 0xFF} ${(rgb shr 8) and 0xFF} ${rgb and 0xFF}"
        if (lastWritten["$path/mi"] != mi && sys.write("$path/multi_intensity", mi)) lastWritten["$path/mi"] = mi
        val b = brightness.coerceIn(0, 255).toString()
        if (lastWritten["$path/b"] != b && sys.write("$path/brightness", b)) lastWritten["$path/b"] = b
    }

    private fun allOff() {
        (left + right).forEach { writeLed(it, 0, 0) }
    }

    override fun run() {
        val start = System.nanoTime()
        while (true) {
            try {
                val s = state
                val on = s.enabled && (awake || !s.offWhenAsleep)
                val animated = on && s.mode != LedMode.SOLID
                if (!on) {
                    allOff()
                } else {
                    val t = (System.nanoTime() - start) / 1e9 * s.speed
                    when (s.mode) {
                        LedMode.BREATHE -> {
                            val phase = 0.5 - 0.5 * cos(2 * PI * t / 3.0)
                            val b = (s.brightness * (0.06 + 0.94 * phase)).roundToInt()
                            left.forEach { writeLed(it, s.left, b) }
                            right.forEach { writeLed(it, s.rightColor, b) }
                        }
                        LedMode.RAINBOW -> {
                            val base = (t * 90.0) % 360.0
                            left.forEachIndexed { i, p -> writeLed(p, hue(base + i * 90.0), s.brightness) }
                            right.forEachIndexed { i, p -> writeLed(p, hue(base + 45.0 + i * 90.0), s.brightness) }
                        }
                        LedMode.SOLID -> {
                            left.forEach { writeLed(it, s.left, s.brightness) }
                            right.forEach { writeLed(it, s.rightColor, s.brightness) }
                        }
                    }
                }
                lock.withLock {
                    if (animated) {
                        changed.await(40, TimeUnit.MILLISECONDS)
                    } else {
                        dirty = false
                        while (!dirty) changed.await()
                    }
                }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                Timber.w(e, "led loop")
                sleep(1000)
            }
        }
    }

    companion object {
        /** Fully saturated color for a hue in degrees (pure Kotlin; no native graphics in app_process). */
        fun hue(deg: Double): Int {
            val h = ((deg % 360.0) + 360.0) % 360.0 / 60.0
            val x = 1.0 - kotlin.math.abs(h % 2.0 - 1.0)
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(1.0, x, 0.0)
                1 -> Triple(x, 1.0, 0.0)
                2 -> Triple(0.0, 1.0, x)
                3 -> Triple(0.0, x, 1.0)
                4 -> Triple(x, 0.0, 1.0)
                else -> Triple(1.0, 0.0, x)
            }
            return ((r * 255).roundToInt() shl 16) or ((g * 255).roundToInt() shl 8) or (b * 255).roundToInt()
        }
    }
}
