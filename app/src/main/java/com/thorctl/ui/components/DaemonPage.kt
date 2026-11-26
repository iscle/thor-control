package com.thorctl.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thorctl.ui.state.DaemonUiState

/**
 * A menu detail page: the header (title and back button, always there) and either the page's
 * [content] or, while the service is unreachable, [DaemonOffline]. [trailing] (a page's main
 * switch, say) only shows while connected.
 */
@Composable
fun <T> DaemonPage(
    title: String,
    onBack: () -> Unit,
    state: DaemonUiState<T>,
    scrollable: Boolean = false,
    trailing: @Composable (T) -> Unit = {},
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val scroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(Modifier
        .fillMaxSize()
        .then(scroll)
        .padding(PagePadding)) {
        when (state) {
            DaemonUiState.Disconnected -> {
                DetailHeader(title, onBack)
                Spacer(Modifier.height(12.dp))
                DaemonOffline()
            }

            is DaemonUiState.Connected -> {
                DetailHeader(title, onBack) { trailing(state.content) }
                content(state.content)
            }
        }
    }
}
