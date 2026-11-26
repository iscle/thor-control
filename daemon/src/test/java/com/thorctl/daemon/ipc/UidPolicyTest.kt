package com.thorctl.daemon.ipc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UidPolicyTest {
    @Test
    fun rootSystemShellAndTheApp() {
        var lookups = 0
        val policy = UidPolicy {
            lookups++
            10_100
        }
        listOf(0, 1000, 2000, 10_100).forEach { assertTrue(policy.isAllowed(it)) }
        assertFalse(policy.isAllowed(10_200))
        assertTrue(policy.isApp(10_100))
        // The app's uid is looked up again only for a stranger (the app may have been reinstalled).
        val before = lookups
        policy.isApp(10_100)
        assertEquals(before, lookups)
    }
}
