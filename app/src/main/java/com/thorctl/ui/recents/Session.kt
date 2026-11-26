package com.thorctl.ui.recents

import androidx.compose.runtime.Immutable

@Immutable
data class Session(val id: Int, val intro: Intro, val start: Int, val originKey: Int)
