package com.thorctl.daemon.control

import com.thorctl.daemon.FakeSys
import com.thorctl.protocol.Proto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemUpkeepTest {
    @Test
    fun addsTheAccessibilityServiceOnce() {
        assertEquals(Proto.A11Y_SERVICE, SystemUpkeep.withService("null"))
        assertEquals(Proto.A11Y_SERVICE, SystemUpkeep.withService(""))
        assertEquals("a/b:${Proto.A11Y_SERVICE}", SystemUpkeep.withService("a/b"))
        assertNull(SystemUpkeep.withService("a/b:${Proto.A11Y_SERVICE}"))
    }

    @Test
    fun restartsNothingThatIsStopped() {
        val sys = FakeSys().apply { props["init.svc.cameraserver"] = "running"; props["init.svc.wifidisplayhalservice"] = "stopped" }
        SystemUpkeep(sys).keepServicesStopped()
        assertEquals(listOf("stop cameraserver"), sys.commands)
    }

    @Test
    fun reenablesTheServiceWhenItIsMissing() {
        val sys = FakeSys().apply {
            this["/data/data/${Proto.PACKAGE}/x"] = ""
            shell = { if (it.startsWith("cmd settings get")) "other/svc" else "" }
        }
        SystemUpkeep(sys).ensureAccessibility()
        assertTrue(sys.commands.any { it == "cmd settings put secure enabled_accessibility_services 'other/svc:${Proto.A11Y_SERVICE}'" })
    }

    @Test
    fun theModuleCanBeTurnedOff() {
        val sys = FakeSys()
        val upkeep = SystemUpkeep(sys)
        assertEquals(false, upkeep.moduleTurnedOff())
        sys["${SystemUpkeep.MODULE_DIR}/disable"] = ""
        assertTrue(upkeep.moduleTurnedOff())
    }
}
