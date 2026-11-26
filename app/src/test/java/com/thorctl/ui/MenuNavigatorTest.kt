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
        val navigator = MenuNavigator()
        assertEquals(MainKey, navigator.current)
        assertFalse(navigator.back())
        assertEquals(listOf(MainKey), navigator.backStack.toList())
        assertEquals(listOf(MainKey), MenuNavigator(emptyList()).backStack.toList())
    }

    @Test
    fun gamepadGoesBackToTheControllerPage() {
        val navigator = MenuNavigator()
        navigator.open(ControllerKey)
        navigator.open(GamepadKey)
        assertTrue(navigator.back())
        assertEquals(ControllerKey, navigator.current)
        assertTrue(navigator.back())
        assertEquals(MainKey, navigator.current)
    }

    @Test
    fun openingThePageShownChangesNothing() {
        val navigator = MenuNavigator()
        navigator.open(FanKey)
        navigator.open(FanKey)
        assertEquals(listOf(MainKey, FanKey), navigator.backStack.toList())
    }
}
