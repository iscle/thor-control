package com.thorctl.daemon.control

import com.thorctl.daemon.FakeSys
import com.thorctl.protocol.AppContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemUpkeepTest {
    @Test
    fun addsTheAccessibilityServiceOnce() {
        assertEquals(AppContract.ACCESSIBILITY_SERVICE, SystemUpkeep.withService("null"))
        assertEquals(AppContract.ACCESSIBILITY_SERVICE, SystemUpkeep.withService(""))
        assertEquals("a/b:${AppContract.ACCESSIBILITY_SERVICE}", SystemUpkeep.withService("a/b"))
        assertNull(SystemUpkeep.withService("a/b:${AppContract.ACCESSIBILITY_SERVICE}"))
    }

    @Test
    fun restartsNothingThatIsStopped() {
        val sys = FakeSys().apply { properties["init.svc.cameraserver"] = "running"; properties["init.svc.wifidisplayhalservice"] = "stopped" }
        SystemUpkeep(sys).keepServicesStopped()
        assertEquals(listOf("stop cameraserver"), sys.commands)
    }

    @Test
    fun reenablesTheServiceWhenItIsMissing() {
        val sys = FakeSys().apply {
            this["/data/data/${AppContract.PACKAGE}/x"] = ""
            shell = { if (it.startsWith("cmd settings get")) "other/svc" else "" }
        }
        SystemUpkeep(sys).ensureAccessibility()
        assertTrue(sys.commands.any { it == "cmd settings put secure enabled_accessibility_services 'other/svc:${AppContract.ACCESSIBILITY_SERVICE}'" })
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
