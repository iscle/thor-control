package com.thorctl.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.thorctl.ui.controller.ControllerScreen
import com.thorctl.ui.fan.FanScreen
import com.thorctl.ui.game.GameScreen
import com.thorctl.ui.gamepad.GamepadScreen
import com.thorctl.ui.lights.LightsScreen
import com.thorctl.ui.menu.MainPage

private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Going deeper: the page grows in from the right on an expressive spring. */
private fun forward(): ContentTransform =
    (
        slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 6 } +
            scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = 0.94f) +
            fadeIn(tween(160))
        ).togetherWith(fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.97f))

/** Going back: the page slides away quickly. */
private fun backward(): ContentTransform =
    fadeIn(tween(120)).togetherWith(slideOutHorizontally(tween(180, easing = EmphasizedAccelerate)) { it / 6 } + fadeOut(tween(150)))

/**
 * The menu: the main page (tiles, sliders, stats) and its detail pages. [entered] runs the main
 * page's drop-in animation; [onOpenApp] (null inside the app itself) opens Thor Control.
 */
@Composable
fun MenuPanel(entered: Boolean, onOpenApp: (() -> Unit)?, modifier: Modifier = Modifier) {
    val navigator = remember { MenuNavigator() }
    NavDisplay(
        backStack = navigator.backStack,
        modifier = modifier,
        onBack = { navigator.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { backward() },
        entryProvider = entryProvider {
            entry<MainKey> { MainPage(entered = entered, onNavigate = navigator::open, onOpenApp = onOpenApp) }
            entry<FanKey> { FanScreen(onBack = { navigator.back() }) }
            entry<LightsKey> { LightsScreen(onBack = { navigator.back() }) }
            entry<ControllerKey> { ControllerScreen(onBack = { navigator.back() }, onGamepad = { navigator.open(GamepadKey) }) }
            entry<GamepadKey> { GamepadScreen(onBack = { navigator.back() }) }
            entry<GameKey> { GameScreen(onBack = { navigator.back() }) }
        },
    )
}
