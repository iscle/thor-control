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
    /** [packageName]'s icon, [sizeDp] square; null if the package is unknown. */
    fun icon(packageName: String, sizeDp: Int = 40): Bitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val sizePixels = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePixels, sizePixels, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePixels, sizePixels)
        drawable.draw(Canvas(bitmap))
        bitmap
    } catch (_: Exception) {
        null
    }
}
