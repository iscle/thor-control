package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.awaitUntil
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LedRestoreTaskTest {
    private val node = "/sys/class/leds/left:stick:0"
    private val sys = FakeSys().apply {
        this["$node/brightness"] = 0
        this["$node/multi_intensity"] = "0 0 0"
    }
    private val clock = FakeClock()
    private val leds = LedController(sys)
    private val task = LedRestoreTask(leds, clock)

    @AfterTest
    fun stop() = leds.interrupt()

    private fun startSolid() {
        leds.apply(LedState(mode = LedMode.SOLID, left = 0x112233, brightness = 90))
        leds.start()
        awaitUntil("the lights written") { sys["$node/multi_intensity"] == "17 34 51" && sys["$node/brightness"] == "90" }
    }

    @Test
    fun putsOurLightsBackAfterAnotherAppChangedThem() {
        startSolid()
        sys["$node/multi_intensity"] = "255 0 0" // AynParts' restore
        task.run()
        awaitUntil("ours back") { sys["$node/multi_intensity"] == "17 34 51" }
    }

    @Test
    fun stopsLookingAfterTheFirstMinutes() {
        startSolid()
        clock.now += 181_000
        sys["$node/multi_intensity"] = "255 0 0"
        task.run()
        Thread.sleep(100)
        assertEquals("255 0 0", sys["$node/multi_intensity"])
    }
}
