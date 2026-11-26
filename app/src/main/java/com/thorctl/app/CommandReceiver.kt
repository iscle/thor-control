package com.thorctl.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thorctl.platform.overlay.OverlayMenu
import com.thorctl.protocol.Proto
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey

/** Fallback the daemon uses for the AYN button while the app isn't connected yet. */
@Inject
@BroadcastReceiverKey(CommandReceiver::class)
@ContributesIntoMap(AppScope::class, binding = binding<BroadcastReceiver>())
class CommandReceiver(private val menu: OverlayMenu) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Proto.ACTION_TOGGLE_MENU -> menu.toggle()
            Proto.ACTION_HIDE_MENU -> menu.hide()
        }
    }
}
