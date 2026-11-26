package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.Stats
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.abs

/** Live telemetry from sysfs / procfs, read once per second while a client wants it. */
@Inject
@SingleIn(DaemonScope::class)
class StatsCollector(private val sys: Sys, private val fan: FanController) {
    private var lastTotal = 0L
    private var lastIdle = 0L

    private val policies: List<String> = sys.list("/sys/devices/system/cpu/cpufreq")
        .filter { it.substringAfterLast('/').startsWith("policy") }
        .sortedBy { it.substringAfterLast('/').removePrefix("policy").toIntOrNull() ?: 0 }

    private val cpuZones: List<String>
    private val gpuZones: List<String>

    init {
        val cpu = ArrayList<String>()
        val gpu = ArrayList<String>()
        sys.list("/sys/class/thermal").filter { it.substringAfterLast('/').startsWith("thermal_zone") }.forEach {
            val type = sys.read("$it/type") ?: return@forEach
            when {
                type.startsWith("cpu-") || type.startsWith("cpuss-") -> cpu += "$it/temp"
                type.startsWith("gpuss-") -> gpu += "$it/temp"
            }
        }
        cpuZones = cpu
        gpuZones = gpu
    }

    private fun maxTemp(paths: List<String>): Float =
        paths.mapNotNull { sys.readInt(it) }.filter { it in 1..200000 }.maxOrNull()?.let { it / 1000f } ?: 0f

    private fun cpuLoad(): Float {
        val (total, idle) = parseCpuTimes(sys.read("/proc/stat")) ?: return 0f
        val totalDelta = total - lastTotal
        val idleDelta = idle - lastIdle
        lastTotal = total
        lastIdle = idle
        return if (totalDelta <= 0) 0f else ((totalDelta - idleDelta).toFloat() / totalDelta).coerceIn(0f, 1f)
    }

    private fun meminfo(): Pair<Int, Int> = parseMeminfo(sys.read("/proc/meminfo"))

    /** Called from the stats tick and from binder threads (a new subscriber): CPU load is a delta since the last call. */
    @Synchronized
    fun collect(): Stats {
        val clusterFrequencies = policies.map { (sys.readInt("$it/scaling_cur_freq") ?: 0) / 1000 }
        val gpuBusy = sys.read("$KGSL/gpu_busy_percentage")?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val gpuClockMhz = (sys.readLong("$KGSL/gpuclk") ?: 0L) / 1_000_000
        val gpuTemp = sys.readInt("$KGSL/temp")?.let { it / 1000f } ?: maxTemp(gpuZones)
        val (total, available) = meminfo()

        val batteryVolts = (sys.readLong("$BATTERY/voltage_now") ?: 0L) / 1e6
        val batteryAmps = (sys.readLong("$BATTERY/current_now") ?: 0L) / 1e6
        val usbOnline = sys.readInt("$USB/online") == 1
        val usbVolts = if (usbOnline) (sys.readLong("$USB/voltage_now") ?: 0L) / 1e6 else 0.0
        val usbAmps = if (usbOnline) (sys.readLong("$USB/current_now") ?: 0L) / 1e6 else 0.0

        return Stats(
            cpuLoad = cpuLoad(),
            cpuFreqMhz = clusterFrequencies.maxOrNull() ?: 0,
            clusterFreqsMhz = clusterFrequencies,
            cpuTemp = maxTemp(cpuZones),
            gpuLoad = gpuBusy / 100f,
            gpuFreqMhz = gpuClockMhz.toInt(),
            gpuTemp = gpuTemp,
            ramTotalMb = total,
            ramAvailMb = available,
            batteryLevel = sys.readInt("$BATTERY/capacity") ?: 0,
            batteryStatus = sys.read("$BATTERY/status") ?: "Unknown",
            batteryWatts = abs(batteryVolts * batteryAmps).toFloat(),
            inputWatts = abs(usbVolts * usbAmps).toFloat(),
            batteryTemp = (sys.readInt("$BATTERY/temp") ?: 0) / 10f,
            fanPwm = fan.readPwm(),
            fanTemp = fan.controlTemp,
        )
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** (total, idle) jiffies from the aggregate "cpu" line of /proc/stat; idle includes iowait. */
        fun parseCpuTimes(procStat: String?): Pair<Long, Long>? {
            val line = procStat?.lineSequence()?.firstOrNull() ?: return null
            val jiffies = line.split(WHITESPACE).drop(1).mapNotNull { it.toLongOrNull() }
            if (jiffies.size < 5) return null
            return jiffies.take(8).sum() to jiffies[3] + jiffies[4]
        }

        /** (MemTotal, MemAvailable) in MB from /proc/meminfo; 0 for what's missing. */
        fun parseMeminfo(meminfo: String?): Pair<Int, Int> {
            var total = 0
            var available = 0
            meminfo?.lineSequence()?.forEach { line ->
                when {
                    line.startsWith("MemTotal:") -> total = (line.split(WHITESPACE).getOrNull(1)?.toIntOrNull() ?: 0) / 1024
                    line.startsWith("MemAvailable:") -> available = (line.split(WHITESPACE).getOrNull(1)?.toIntOrNull() ?: 0) / 1024
                }
            }
            return total to available
        }

        const val KGSL = "/sys/class/kgsl/kgsl-3d0"
        const val BATTERY = "/sys/class/power_supply/battery"
        const val USB = "/sys/class/power_supply/usb"
    }
}
