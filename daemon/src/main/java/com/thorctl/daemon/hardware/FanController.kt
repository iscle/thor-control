package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.FanSpeed
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import kotlin.math.max

/**
 * Drives the pwm-fan (hwmon "pwmfan", pwm1 0..255).
 *
 * The kernel normally controls this fan through step_wise thermal zones whose trip points are
 * bound to the "pwm-fan" cooling device (cpu-1-9, cpu-1-10, gpuss-0/1, usb-therm). In the Auto
 * profile we leave that alone. For curve profiles we raise only the fan-bound trip points out of
 * reach (CPU/GPU throttling trips in the same zones are untouched) and drive pwm1 ourselves.
 *
 * Original trip values are saved in /dev (tmpfs, reset every boot) so that the supervisor script
 * can restore kernel control if this process ever dies.
 */
@Inject
@SingleIn(DaemonScope::class)
class FanController(private val sys: Sys, private val clock: Clock) {
    private data class Trip(val path: String, val original: Int)

    private val pwmPath: String? = sys.list(HWMON)
        .firstOrNull { sys.read("$it/name") == "pwmfan" }
        ?.let { "$it/pwm1" }

    private val coolingDevice: String? = sys.list(THERMAL)
        .firstOrNull { it.name().startsWith("cooling_device") && sys.read("$it/type") == "pwm-fan" }

    private val trips: List<Trip>
    private val controlSensors: List<String>
    private val usbSensors: List<String>

    @Volatile
    var kernelMode = true
        private set

    @Volatile
    var currentPwm = 0
        private set

    @Volatile
    var controlTemp = 0f
        private set

    private var filtered = 0f
    private var screenOffHot = false

    init {
        val canonicalCoolingDevice = coolingDevice?.let(sys::canonical)
        val found = ArrayList<Pair<String, Int>>()
        val sensors = LinkedHashSet<String>()
        val usb = LinkedHashSet<String>()
        if (canonicalCoolingDevice != null) {
            sys.list(THERMAL).filter { it.name().startsWith("thermal_zone") }.forEach { zone ->
                val type = sys.read("$zone/type") ?: return@forEach
                sys.list(zone).filter { it.name().matches(COOLING_DEVICE_LINK) }.forEach { link ->
                    if (sys.canonical(link) == canonicalCoolingDevice) {
                        val trip = sys.readInt("${link}_trip_point") ?: return@forEach
                        found += "$zone/trip_point_${trip}_temp" to (sys.readInt("$zone/trip_point_${trip}_temp") ?: 0)
                        if (type.startsWith("usb")) usb += "$zone/temp" else sensors += "$zone/temp"
                    }
                }
            }
        }
        controlSensors = sensors.toList()
        usbSensors = usb.toList()
        trips = loadOrSaveOriginals(found)
        Timber.i("fan: pwm=$pwmPath cdev=${coolingDevice?.name()} trips=${trips.size} sensors=${controlSensors.size}+${usbSensors.size}")
    }

    /** Original values are captured once per boot; a restarted daemon must not capture our own overrides. */
    private fun loadOrSaveOriginals(current: List<Pair<String, Int>>): List<Trip> {
        sys.mkdirs(STATE_DIRECTORY)
        val originalsFile = "$STATE_DIRECTORY/trips.orig"
        val savedText = sys.read(originalsFile)
        if (savedText != null) {
            val saved = savedText.lines().mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size == 2) parts[0] to parts[1].toIntOrNull() else null
            }.filter { it.second != null }.associate { it.first to it.second!! }
            if (saved.isNotEmpty()) return current.map { Trip(it.first, saved[it.first] ?: it.second.takeIf { original -> original < DISABLED_TEMP } ?: 60000) }
        }
        val list = current.map { Trip(it.first, if (it.second >= DISABLED_TEMP) 60000 else it.second) }
        sys.write(originalsFile, list.joinToString("\n") { "${it.path} ${it.original}" } + "\n")
        return list
    }

    val available: Boolean get() = pwmPath != null

    fun setKernelControl(enabled: Boolean) {
        if (trips.isEmpty()) {
            kernelMode = enabled
            return
        }
        for (trip in trips) sys.write(trip.path, if (enabled) trip.original.toString() else DISABLED_TEMP.toString())
        kernelMode = enabled
        Timber.i("fan: kernel control ${if (enabled) "restored" else "disabled"}")
    }

    private fun readMax(paths: List<String>): Float =
        paths.mapNotNull { sys.readInt(it) }.maxOrNull()?.let { it / 1000f } ?: 0f

    /**
     * Called once per second. With the screen off (lid closed) the fan stops in every profile:
     * games pause and the clocks drop, so the chip cools on its own. It only keeps running if
     * something still heats the chip or the charging port past [SCREEN_OFF_MAX_TEMP] /
     * [SCREEN_OFF_MAX_USB_TEMP]; then the selected profile (and the 90 °C safety net) apply.
     */
    fun tick(profile: FanProfile, screenOn: Boolean) {
        val pwm = pwmPath ?: return
        val temperature = readMax(controlSensors)
        controlTemp = temperature

        // Hysteresis: once heat brought the fan back with the screen off, it runs until well below.
        val usbTemperature = readMax(usbSensors)
        screenOffHot = !screenOn && (temperature >= SCREEN_OFF_MAX_TEMP || usbTemperature >= SCREEN_OFF_MAX_USB_TEMP ||
            (screenOffHot && (temperature >= SCREEN_OFF_MAX_TEMP - 7f || usbTemperature >= SCREEN_OFF_MAX_USB_TEMP - 3f)))
        if (!screenOn && !screenOffHot) {
            if (kernelMode) {
                setKernelControl(false)
                releaseKernelTargets()
            }
            if (sys.readInt(pwm) != 0) sys.write(pwm, "0")
            lastWritten = 0
            currentPwm = 0
            filtered = temperature
            return
        }

        if (profile.isAuto) {
            if (!kernelMode) setKernelControl(true)
            currentPwm = sys.readInt(pwm) ?: 0
            lastWritten = -1
            filtered = temperature
            return
        }
        if (kernelMode) {
            setKernelControl(false)
            releaseKernelTargets()
        }

        // Per-core sensors spike for a second at a time (app launches, JIT). Rise fairly quickly,
        // fall slowly, so the fan neither bursts on spikes nor hunts around a curve knee.
        filtered = if (filtered == 0f) temperature else filtered + (temperature - filtered) * (if (temperature >= filtered) 0.35f else 0.12f)
        controlTemp = filtered
        var percent = profile.percentAt(filtered)

        // Safety nets, independent of the selected curve, which decides everywhere else: the chip
        // at 90 °C, and the charging port (the stock zones already spin the fan from 43 °C there;
        // normal charging sits around 45 °C, so the curve keeps the say until the port is hotter).
        if (readMax(usbSensors) >= PORT_SAFETY_TEMP) percent = max(percent, PORT_SAFETY_PERCENT)
        if (filtered >= SAFETY_TEMP || temperature >= SAFETY_TEMP + 8f) percent = 100f

        val desired = FanSpeed.pwmFor(percent)
        var target = desired
        if (target < currentPwm) target = max(target, currentPwm - MAX_STEP_DOWN)
        // Never park the fan in its stall band while ramping down: either min spin or off.
        if (target in 1 until FanSpeed.MIN_SPIN_PWM) target = if (desired == 0) 0 else FanSpeed.MIN_SPIN_PWM

        // If the kernel keeps overwriting our PWM, its governor has latched targets; release them.
        val actual = sys.readInt(pwm)
        if (lastWritten >= 0 && actual != null && actual != lastWritten) {
            overriddenTicks++
            if (overriddenTicks >= 2 && clock.elapsedRealtime() - lastRelease > 10_000) {
                Timber.w("fan: kernel keeps overriding pwm ($actual instead of $lastWritten)")
                releaseKernelTargets()
                overriddenTicks = 0
            }
        } else {
            overriddenTicks = 0
        }
        if (sys.readInt(pwm) != target) sys.write(pwm, target.toString())
        lastWritten = target
        currentPwm = target
    }

    private val zoneDirectories: List<String> by lazy { trips.map { it.path.substringBeforeLast('/') }.distinct() }
    private var lastWritten = -1
    private var overriddenTicks = 0
    private var lastRelease = 0L

    /**
     * The step_wise governor only lowers a fan target when it sees a temperature *drop*. Once
     * the fan trips are moved out of reach those zones stop interrupting, so whatever targets
     * were active at takeover stay latched and get re-applied over our PWM (the fan "sticks" at
     * a level). Zero the fan state, then feed every fan zone one cold emulated reading: every
     * instance then sees a drop below its level and returns to no-target. Real readings resume
     * right after.
     */
    private fun releaseKernelTargets() {
        val pwm = pwmPath ?: return
        val emulatedTemps = zoneDirectories.map { "$it/emul_temp" }.filter { sys.exists(it) }
        sys.write(pwm, "0")
        if (emulatedTemps.isEmpty()) return
        emulatedTemps.forEach { sys.write(it, "20000") }
        try {
            Thread.sleep(300)
        } finally {
            emulatedTemps.forEach { sys.write(it, "0") }
        }
        lastRelease = clock.elapsedRealtime()
        Timber.i("fan: released latched kernel fan targets in ${emulatedTemps.size} zones")
    }

    /** Last reading of pwm1, used for stats. */
    fun readPwm(): Int = pwmPath?.let { sys.readInt(it) } ?: 0

    companion object {
        private const val HWMON = "/sys/class/hwmon"
        private const val THERMAL = "/sys/class/thermal"
        private val COOLING_DEVICE_LINK = Regex("cdev\\d+")

        private fun String.name() = substringAfterLast('/')

        const val STATE_DIRECTORY = "/dev/thorctl"
        const val DISABLED_TEMP = 150000
        const val SAFETY_TEMP = 90f
        const val SCREEN_OFF_MAX_TEMP = 62f
        const val SCREEN_OFF_MAX_USB_TEMP = 48f
        const val PORT_SAFETY_TEMP = 50f
        const val PORT_SAFETY_PERCENT = 40f
        const val MAX_STEP_DOWN = 12
    }
}
