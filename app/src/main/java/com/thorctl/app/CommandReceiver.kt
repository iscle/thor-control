package com.thorctl.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thorctl.protocol.Proto

/** Fallback the daemon uses for the AYN button while the app isn't connected yet. */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Proto.ACTION_TOGGLE_MENU -> context.graph.menu.toggle()
            Proto.ACTION_HIDE_MENU -> context.graph.menu.hide()
        }
    }
}
