package com.thorctl.ui

import com.thorctl.ui.navigation.ControllerKey
import com.thorctl.ui.navigation.FanKey
import com.thorctl.ui.navigation.GamepadKey
import com.thorctl.ui.navigation.MainKey
import com.thorctl.ui.navigation.MenuNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuNavigatorTest {
    @Test
    fun theMainPageIsAlwaysAtTheBottom() {
        val nav = MenuNavigator()
        assertEquals(MainKey, nav.current)
        assertFalse(nav.back())
        assertEquals(listOf(MainKey), nav.backStack.toList())
        assertEquals(listOf(MainKey), MenuNavigator(emptyList()).backStack.toList())
    }

    @Test
    fun gamepadGoesBackToTheControllerPage() {
        val nav = MenuNavigator()
        nav.open(ControllerKey)
        nav.open(GamepadKey)
        assertTrue(nav.back())
        assertEquals(ControllerKey, nav.current)
        assertTrue(nav.back())
        assertEquals(MainKey, nav.current)
    }

    @Test
    fun openingThePageShownChangesNothing() {
        val nav = MenuNavigator()
        nav.open(FanKey)
        nav.open(FanKey)
        assertEquals(listOf(MainKey, FanKey), nav.backStack.toList())
    }
}
