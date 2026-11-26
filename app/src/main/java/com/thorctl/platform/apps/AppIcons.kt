package com.thorctl.platform.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Launcher icons as bitmaps (recents cards). */
@Inject
@SingleIn(AppScope::class)
class AppIcons(private val context: Context) {
    /** [pkg]'s icon, [sizeDp] square; null if the package is unknown. */
    fun icon(pkg: String, sizeDp: Int = 40): Bitmap? = try {
        val d = context.packageManager.getApplicationIcon(pkg)
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(b))
        b
    } catch (_: Exception) {
        null
    }
}
