package com.thorctl.daemon.control

import com.thorctl.daemon.FakeSys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemUpkeepTest {
    @Test
    fun restartsNothingThatIsStopped() {
        val sys = FakeSys().apply { properties["init.svc.cameraserver"] = "running"; properties["init.svc.wifidisplayhalservice"] = "stopped" }
        SystemUpkeep(sys).keepServicesStopped()
        assertEquals(listOf("stop cameraserver"), sys.commands)
    }

    @Test
    fun theModuleCanBeTurnedOff() {
        val sys = FakeSys()
        val upkeep = SystemUpkeep(sys)
        assertEquals(false, upkeep.moduleTurnedOff())
        sys["${SystemUpkeep.MODULE_DIRECTORY}/disable"] = ""
        assertTrue(upkeep.moduleTurnedOff())
    }
}
