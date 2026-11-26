package com.thorctl.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.repository.BatteryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** From the sticky ACTION_BATTERY_CHANGED broadcast (no receiver stays registered). */
@Inject
@ContributesBinding(AppScope::class)
class StickyBatteryRepository(private val context: Context) : BatteryRepository {
    override fun current(): BatteryStatus {
        val batteryIntent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryStatus.UNKNOWN
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return BatteryStatus(level, isCharging(status, plugged))
    }

    companion object {
        /** Charging, or full while still plugged in. */
        fun isCharging(status: Int, plugged: Boolean): Boolean =
            status == BatteryManager.BATTERY_STATUS_CHARGING || (status == BatteryManager.BATTERY_STATUS_FULL && plugged)
    }
}
