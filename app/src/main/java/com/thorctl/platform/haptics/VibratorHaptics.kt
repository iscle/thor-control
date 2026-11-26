package com.thorctl.platform.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
class VibratorHaptics(private val context: Context) : Haptics {
    override fun tick() = vibrate(VibrationEffect.EFFECT_TICK)

    override fun heavyClick() = vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)

    private fun vibrate(effect: Int) {
        try {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(effect))
        } catch (_: Exception) {
        }
    }
}
