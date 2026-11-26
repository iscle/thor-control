package com.thorctl.daemon.feature.recents

/** Apps visible on another screen when recents opened, by display: that screen is captured for fresh previews of them. */
data class PreviewRequest(val tasksByDisplay: Map<Int, List<Int>>)
