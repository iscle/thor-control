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
        val dt = total - lastTotal
        val di = idle - lastIdle
        lastTotal = total
        lastIdle = idle
        return if (dt <= 0) 0f else ((dt - di).toFloat() / dt).coerceIn(0f, 1f)
    }

    private fun meminfo(): Pair<Int, Int> = parseMeminfo(sys.read("/proc/meminfo"))

    /** Called from the stats tick and from binder threads (a new subscriber): CPU load is a delta since the last call. */
    @Synchronized
    fun collect(): Stats {
        val freqs = policies.map { (sys.readInt("$it/scaling_cur_freq") ?: 0) / 1000 }
        val gpuBusy = sys.read("$KGSL/gpu_busy_percentage")?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val gpuClk = (sys.readLong("$KGSL/gpuclk") ?: 0L) / 1_000_000
        val gpuTemp = sys.readInt("$KGSL/temp")?.let { it / 1000f } ?: maxTemp(gpuZones)
        val (total, avail) = meminfo()

        val bv = (sys.readLong("$BAT/voltage_now") ?: 0L) / 1e6
        val bi = (sys.readLong("$BAT/current_now") ?: 0L) / 1e6
        val usbOnline = sys.readInt("$USB/online") == 1
        val uv = if (usbOnline) (sys.readLong("$USB/voltage_now") ?: 0L) / 1e6 else 0.0
        val ui = if (usbOnline) (sys.readLong("$USB/current_now") ?: 0L) / 1e6 else 0.0

        return Stats(
            cpuLoad = cpuLoad(),
            cpuFreqMhz = freqs.maxOrNull() ?: 0,
            clusterFreqsMhz = freqs,
            cpuTemp = maxTemp(cpuZones),
            gpuLoad = gpuBusy / 100f,
            gpuFreqMhz = gpuClk.toInt(),
            gpuTemp = gpuTemp,
            ramTotalMb = total,
            ramAvailMb = avail,
            batteryLevel = sys.readInt("$BAT/capacity") ?: 0,
            batteryStatus = sys.read("$BAT/status") ?: "Unknown",
            batteryWatts = abs(bv * bi).toFloat(),
            inputWatts = abs(uv * ui).toFloat(),
            batteryTemp = (sys.readInt("$BAT/temp") ?: 0) / 10f,
            fanPwm = fan.readPwm(),
            fanTemp = fan.controlTemp,
        )
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** (total, idle) jiffies from the aggregate "cpu" line of /proc/stat; idle includes iowait. */
        fun parseCpuTimes(procStat: String?): Pair<Long, Long>? {
            val line = procStat?.lineSequence()?.firstOrNull() ?: return null
            val v = line.split(WHITESPACE).drop(1).mapNotNull { it.toLongOrNull() }
            if (v.size < 5) return null
            return v.take(8).sum() to v[3] + v[4]
        }

        /** (MemTotal, MemAvailable) in MB from /proc/meminfo; 0 for what's missing. */
        fun parseMeminfo(meminfo: String?): Pair<Int, Int> {
            var total = 0
            var avail = 0
            meminfo?.lineSequence()?.forEach { l ->
                when {
                    l.startsWith("MemTotal:") -> total = (l.split(WHITESPACE).getOrNull(1)?.toIntOrNull() ?: 0) / 1024
                    l.startsWith("MemAvailable:") -> avail = (l.split(WHITESPACE).getOrNull(1)?.toIntOrNull() ?: 0) / 1024
                }
            }
            return total to avail
        }

        const val KGSL = "/sys/class/kgsl/kgsl-3d0"
        const val BAT = "/sys/class/power_supply/battery"
        const val USB = "/sys/class/power_supply/usb"
    }
}
