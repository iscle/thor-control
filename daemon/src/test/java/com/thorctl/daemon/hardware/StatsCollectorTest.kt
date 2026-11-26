package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeSys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsCollectorTest {
    @Test
    fun cpuTimesIncludeIowaitAsIdle() {
        val stat = "cpu  100 20 30 400 50 6 7 8 9 10\ncpu0 1 2 3 4 5 6 7 8"
        assertEquals((100L + 20 + 30 + 400 + 50 + 6 + 7 + 8) to 450L, StatsCollector.parseCpuTimes(stat))
        assertNull(StatsCollector.parseCpuTimes("cpu 1 2"))
        assertNull(StatsCollector.parseCpuTimes(null))
    }

    @Test
    fun meminfoInMegabytes() {
        val text = "MemTotal:        7864320 kB\nMemFree:          100 kB\nMemAvailable:    2097152 kB\n"
        assertEquals(7680 to 2048, StatsCollector.parseMeminfo(text))
        assertEquals(0 to 0, StatsCollector.parseMeminfo(null))
    }

    @Test
    fun collectsFromSysfs() {
        val sys = FakeSys().apply {
            this["/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq"] = 1_800_000
            this["/sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq"] = 3_000_000
            this["/sys/devices/system/cpu/cpufreq/policy2/scaling_cur_freq"] = 2_400_000
            this["/sys/class/thermal/thermal_zone1/type"] = "cpu-0-1"
            this["/sys/class/thermal/thermal_zone1/temp"] = 61_500
            this["/sys/class/thermal/thermal_zone2/type"] = "cpuss-2"
            this["/sys/class/thermal/thermal_zone2/temp"] = 900_000 // bogus reading: ignored
            this["/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"] = "37 %"
            this["/sys/class/kgsl/kgsl-3d0/gpuclk"] = 680_000_000
            this["/proc/meminfo"] = "MemTotal: 8388608 kB\nMemAvailable: 4194304 kB"
            this["/sys/class/power_supply/battery/voltage_now"] = 4_000_000
            this["/sys/class/power_supply/battery/current_now"] = -1_500_000
            this["/sys/class/power_supply/battery/capacity"] = 77
            this["/sys/class/power_supply/battery/status"] = "Discharging"
            this["/sys/class/power_supply/battery/temp"] = 312
            this["/sys/class/power_supply/usb/online"] = 0
        }
        val s = StatsCollector(sys, FanController(sys, FakeClock())).collect()
        assertEquals(listOf(1800, 2400, 3000), s.clusterFreqsMhz) // by policy number
        assertEquals(3000, s.cpuFreqMhz)
        assertEquals(61.5f, s.cpuTemp)
        assertEquals(0.37f, s.gpuLoad)
        assertEquals(680, s.gpuFreqMhz)
        assertEquals(8192, s.ramTotalMb)
        assertEquals(6f, s.batteryWatts, 1e-4f)
        assertEquals(0f, s.inputWatts)
        assertEquals(31.2f, s.batteryTemp)
        assertEquals(77, s.batteryLevel)
    }
}
