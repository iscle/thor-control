package com.thorctl.ui.recents

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Strong ease in-out, no overshoot: every zoom between a full-screen app and its card, and the
 * keyboard sliding in and out.
 */
internal val StrongInOut = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)
