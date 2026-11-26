package com.thorctl.ui.keyboard

import androidx.lifecycle.ViewModel
import com.thorctl.platform.keyboard.KeyboardActions
import com.thorctl.platform.keyboard.KeyboardEditor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/** The keyboard's state and keys, for the bottom-screen panel and the upright inline keyboard. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class KeyboardViewModel(editor: KeyboardEditor) : ViewModel(), KeyboardActions by editor {
    val ui = editor.ui
    val visible = editor.visible
}
