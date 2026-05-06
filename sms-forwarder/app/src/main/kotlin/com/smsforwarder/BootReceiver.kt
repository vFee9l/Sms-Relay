package com.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (action !in validActions) return

        val prefs = PreferencesManager(context)
        if (prefs.isForwardingEnabled) {
            Log.d(TAG, "Boot '$action' — auto-starting ForwarderService")
            prefs.addSyslog("[BOOT] '$action' — auto-starting service")
            start(context)
        } else {
            Log.d(TAG, "Boot '$action' — forwarding disabled, skipping start")
            prefs.addSyslog("[BOOT] '$action' — forwarding disabled, service not started")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        fun start(context: Context) {
            val intent = Intent(context, ForwarderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
