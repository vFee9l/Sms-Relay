package com.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the restart broadcast sent by ForwarderService.onDestroy()
 * and immediately restarts the service so it is always running.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Restart broadcast received — restarting ForwarderService")
        val prefs = PreferencesManager(context)
        if (prefs.isForwardingEnabled) {
            BootReceiver.start(context)
        }
    }

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
}
