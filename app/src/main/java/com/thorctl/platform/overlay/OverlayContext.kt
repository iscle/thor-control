package com.thorctl.platform.overlay

import android.content.Context

/** A context to add an overlay window with, on a display, and the window type to use. */
data class OverlayContext(val context: Context, val type: Int, val layer: OverlayLayer)
