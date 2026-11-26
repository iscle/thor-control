package com.thorctl.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** The menu's back stack. The main page is always at the bottom. */
class MenuNavigator(initial: List<MenuKey> = listOf(MainKey)) {
    val backStack: SnapshotStateList<MenuKey> = mutableStateListOf<MenuKey>().apply { addAll(initial.ifEmpty { listOf(MainKey) }) }

    val current: MenuKey get() = backStack.last()

    fun open(key: MenuKey) {
        if (backStack.last() != key) backStack.add(key)
    }

    /** False when already on the main page. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
