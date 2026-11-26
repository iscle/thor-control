package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.PI
import kotlin.math.abs
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
            .filter { it.substringAfterLast('/').let { ledName -> ledName == prefix || ledName.startsWith("$prefix:") } }
            .sorted()

    fun apply(newState: LedState) {
        lock.withLock {
            state = newState
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
        val multiIntensity = "${(rgb shr 16) and 0xFF} ${(rgb shr 8) and 0xFF} ${rgb and 0xFF}"
        if (lastWritten["$path/mi"] != multiIntensity && sys.write("$path/multi_intensity", multiIntensity)) lastWritten["$path/mi"] = multiIntensity
        val brightnessValue = brightness.coerceIn(0, 255).toString()
        if (lastWritten["$path/b"] != brightnessValue && sys.write("$path/brightness", brightnessValue)) lastWritten["$path/b"] = brightnessValue
    }

    private fun allOff() {
        (left + right).forEach { writeLed(it, 0, 0) }
    }

    override fun run() {
        val start = System.nanoTime()
        while (true) {
            try {
                val ledState = state
                val on = ledState.enabled && (awake || !ledState.offWhenAsleep)
                val animated = on && ledState.mode != LedMode.SOLID
                if (!on) {
                    allOff()
                } else {
                    val animationTime = (System.nanoTime() - start) / 1e9 * ledState.speed
                    when (ledState.mode) {
                        LedMode.BREATHE -> {
                            val phase = 0.5 - 0.5 * cos(2 * PI * animationTime / 3.0)
                            val breathBrightness = (ledState.brightness * (0.06 + 0.94 * phase)).roundToInt()
                            left.forEach { writeLed(it, ledState.left, breathBrightness) }
                            right.forEach { writeLed(it, ledState.rightColor, breathBrightness) }
                        }
                        LedMode.RAINBOW -> {
                            val base = (animationTime * 90.0) % 360.0
                            left.forEachIndexed { index, ledPath -> writeLed(ledPath, hue(base + index * 90.0), ledState.brightness) }
                            right.forEachIndexed { index, ledPath -> writeLed(ledPath, hue(base + 45.0 + index * 90.0), ledState.brightness) }
                        }
                        LedMode.SOLID -> {
                            left.forEach { writeLed(it, ledState.left, ledState.brightness) }
                            right.forEach { writeLed(it, ledState.rightColor, ledState.brightness) }
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
        fun hue(degrees: Double): Int {
            val sector = ((degrees % 360.0) + 360.0) % 360.0 / 60.0
            val secondary = 1.0 - abs(sector % 2.0 - 1.0)
            val (red, green, blue) = when (sector.toInt()) {
                0 -> Triple(1.0, secondary, 0.0)
                1 -> Triple(secondary, 1.0, 0.0)
                2 -> Triple(0.0, 1.0, secondary)
                3 -> Triple(0.0, secondary, 1.0)
                4 -> Triple(secondary, 0.0, 1.0)
                else -> Triple(1.0, 0.0, secondary)
            }
            return ((red * 255).roundToInt() shl 16) or ((green * 255).roundToInt() shl 8) or (blue * 255).roundToInt()
        }
    }
}
