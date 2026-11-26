package com.thorctl.testing

import com.thorctl.ui.state.DaemonUiState
import kotlin.test.assertIs

/** The content of a connected screen state; fails the test when disconnected. */
val <T> DaemonUiState<T>.content: T get() = assertIs<DaemonUiState.Connected<T>>(this).content
