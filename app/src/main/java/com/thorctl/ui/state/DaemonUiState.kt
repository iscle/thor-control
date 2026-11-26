package com.thorctl.ui.state

/**
 * What a screen that controls the daemon shows: [Disconnected] (the shared offline card) or
 * [Connected] with the screen's own [content], which never has to cope with missing values.
 */
sealed interface DaemonUiState<out T> {
    data object Disconnected : DaemonUiState<Nothing>

    data class Connected<T>(val content: T) : DaemonUiState<T>
}
