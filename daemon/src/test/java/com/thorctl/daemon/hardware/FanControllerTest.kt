package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeSys
import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanControllerTest {
    private val pwm = "/sys/class/hwmon/hwmon2/pwm1"
    private val cpuZone = "/sys/class/thermal/thermal_zone5"
    private val usbZone = "/sys/class/thermal/thermal_zone9"
    private val cpuTrip = "$cpuZone/trip_point_1_temp"
    private val usbTrip = "$usbZone/trip_point_0_temp"

    /** A Thor-like fan: one CPU zone and the USB port's zone bound to the pwm-fan cooling device. */
    private val sys = FakeSys().apply {
        this["/sys/class/hwmon/hwmon0/name"] = "battery"
        this["/sys/class/hwmon/hwmon2/name"] = "pwmfan"
        this[pwm] = 0
        this["/sys/class/thermal/cooling_device0/type"] = "thermal-cpufreq"
        this["/sys/class/thermal/cooling_device3/type"] = "pwm-fan"
        this["$cpuZone/type"] = "cpu-1-9"
        this["$cpuZone/temp"] = 50_000
        this["${cpuZone}/cdev0_trip_point"] = 1
        this[cpuTrip] = 55_000
        links["$cpuZone/cdev0"] = "/sys/class/thermal/cooling_device3"
        this["$usbZone/type"] = "usb-therm"
        this["$usbZone/temp"] = 30_000
        this["$usbZone/cdev1_trip_point"] = 0
        this[usbTrip] = 47_000
        links["$usbZone/cdev1"] = "/sys/class/thermal/cooling_device3"
    }
    private val clock = FakeClock()

    /** 0 % up to 40 °C, 100 % at 80 °C. */
    private val linear = FanProfile("custom-x", "Linear", listOf(FanPoint(40f, 0f), FanPoint(80f, 100f)))
    private val auto = FanProfile.BUILTINS.first()

    private fun temp(cpuCelsius: Int, usb: Int = 30) {
        sys["$cpuZone/temp"] = cpuCelsius * 1000
        sys["$usbZone/temp"] = usb * 1000
    }

    private fun pwm() = sys[pwm]!!.toInt()

    @Test
    fun findsTheFanAndSavesTheKernelTripsOnce() {
        val fan = FanController(sys, clock)
        assertTrue(fan.available)
        assertEquals("$cpuTrip 55000\n$usbTrip 47000\n", sys["/dev/thorctl/trips.orig"])
        // A restarted daemon keeps the first daemon's originals, even after it raised the trips.
        sys[cpuTrip] = FanController.DISABLED_TEMP
        FanController(sys, clock).setKernelControl(true)
        assertEquals("55000", sys[cpuTrip])
    }

    @Test
    fun aCurveTakesOverFromTheKernel() {
        val fan = FanController(sys, clock)
        temp(60)
        fan.tick(linear, screenOn = true)
        assertFalse(fan.kernelMode)
        assertEquals(FanController.DISABLED_TEMP.toString(), sys[cpuTrip])
        assertEquals(FanController.DISABLED_TEMP.toString(), sys[usbTrip])
        assertEquals(128, pwm()) // 50 % of 255
    }

    @Test
    fun autoHandsTheFanBack() {
        val fan = FanController(sys, clock)
        fan.tick(linear, screenOn = true)
        fan.tick(auto, screenOn = true)
        assertTrue(fan.kernelMode)
        assertEquals("55000", sys[cpuTrip])
        assertEquals("47000", sys[usbTrip])
    }

    @Test
    fun ninetyDegreesIsFullSpeedWhateverTheCurve() {
        val quiet = FanProfile("custom-q", "Quiet", listOf(FanPoint(40f, 0f), FanPoint(100f, 10f)))
        val fan = FanController(sys, clock)
        temp(91)
        fan.tick(quiet, screenOn = true)
        assertEquals(255, pwm())
    }

    @Test
    fun neverParksInTheStallBand() {
        val fan = FanController(sys, clock)
        temp(42) // 5 % = pwm 13, too slow to spin
        fan.tick(linear, screenOn = true)
        assertEquals(FanController.MIN_SPIN_PWM, pwm())
    }

    @Test
    fun rampsDownGently() {
        val fan = FanController(sys, clock)
        temp(80)
        fan.tick(linear, screenOn = true)
        assertEquals(255, pwm())
        temp(40)
        fan.tick(linear, screenOn = true)
        assertEquals(255 - FanController.MAX_STEP_DOWN, pwm())
    }

    @Test
    fun aHotChargingPortKeepsSomeAir() {
        val fan = FanController(sys, clock)
        temp(30, usb = 46)
        fan.tick(linear, screenOn = true)
        assertEquals(102, pwm()) // 40 %
    }

    @Test
    fun screenOffStopsTheFanUnlessItIsHot() {
        val fan = FanController(sys, clock)
        temp(70)
        fan.tick(linear, screenOn = true)
        assertTrue(pwm() > 0)
        temp(55)
        fan.tick(linear, screenOn = false)
        assertEquals(0, pwm())
        assertFalse(fan.kernelMode)
        // Too hot to rest: the curve runs again, and keeps running until 7 °C below the limit.
        temp(63)
        fan.tick(linear, screenOn = false)
        assertTrue(pwm() > 0)
        temp(56)
        fan.tick(linear, screenOn = false)
        assertTrue(pwm() > 0)
        temp(54)
        fan.tick(linear, screenOn = false)
        assertEquals(0, pwm())
    }

    @Test
    fun withoutAFanNothingIsWritten() {
        sys.files.remove("/sys/class/hwmon/hwmon2/name")
        val fan = FanController(sys, clock)
        sys.writes.clear()
        fan.tick(linear, screenOn = true)
        assertFalse(fan.available)
        assertTrue(sys.writes.isEmpty())
    }
}
