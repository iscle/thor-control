package com.thorctl.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starting the process is all that is needed; the daemon then offers its binder. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
