package com.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = PreferencesManager(context)
        if (!prefs.isForwardingEnabled) {
            Log.d(TAG, "Forwarding disabled — ignoring incoming SMS")
            return
        }

        // --- Dual SIM: detect which SIM slot received this message ---
        val subscriptionId = getSubscriptionId(intent)
        val simSlot = getSimSlotIndex(context, subscriptionId)
        Log.d(TAG, "SMS intent — subscriptionId=$subscriptionId  simSlot=$simSlot")

        // --- Parse the SMS messages ---
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "getMessagesFromIntent returned empty — skipping")
            return
        }

        val from = messages[0].displayOriginatingAddress ?: run {
            Log.w(TAG, "No originating address — skipping")
            return
        }

        // Concatenate multi-part SMS bodies into one string
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        Log.d(TAG, "SMS from=$from  simSlot=$simSlot  len=${body.length}")

        // --- Allowed sender filter (always active when list is non-empty) ---
        val allowed = prefs.allowedSenders
        if (allowed.isNotEmpty()) {
            val normalizedFrom = from.trim()
            val match = allowed.any { it.trim().equals(normalizedFrom, ignoreCase = true) }
            if (!match) {
                Log.d(TAG, "Sender '$from' not in allowed list — skipping")
                return
            }
        }

        // --- Delegate forwarding to the ForegroundService ---
        val forwardIntent = Intent(context, ForwarderService::class.java).apply {
            action = ForwarderService.ACTION_FORWARD_SMS
            putExtra(ForwarderService.EXTRA_FROM, from)
            putExtra(ForwarderService.EXTRA_MESSAGE, body)
            putExtra(ForwarderService.EXTRA_TIMESTAMP, timestamp)
            putExtra(ForwarderService.EXTRA_SIM_SLOT, simSlot)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(forwardIntent)
        } else {
            context.startService(forwardIntent)
        }
    }

    // --- subscription ID helpers ---

    private fun getSubscriptionId(intent: Intent): Int {
        var subId = intent.getIntExtra("subscription", -1)
        if (subId == -1) subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        if (subId == -1) subId = intent.getIntExtra("slot", -1)
        return subId
    }

    private fun getSimSlotIndex(context: Context, subscriptionId: Int): Int {
        if (subscriptionId == -1) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager
                sm?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_PHONE_STATE denied — defaulting to slot 0: ${e.message}")
                0
            }
        }
        return 0
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
