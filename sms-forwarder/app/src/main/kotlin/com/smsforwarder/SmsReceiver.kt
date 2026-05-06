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
            Log.d(TAG, "Forwarding disabled globally — ignoring SMS")
            return
        }

        val subscriptionId = getSubscriptionId(intent)
        val simSlot        = getSimSlotIndex(context, subscriptionId)
        val simLabel       = "SIM${simSlot + 1}"
        Log.d(TAG, "SMS — subscriptionId=$subscriptionId  simSlot=$simSlot")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val from  = messages[0].displayOriginatingAddress ?: return
        val body  = messages.joinToString("") { it.messageBody ?: "" }
        val ts    = messages[0].timestampMillis

        Log.d(TAG, "SMS from=$from  sim=$simLabel  len=${body.length}")
        prefs.addSyslog("[SMS] Received from $from on $simLabel")

        // Find all webhooks that match this SIM
        val repo     = WebhookRepository(context)
        val webhooks = repo.matchingWebhooks(simSlot)

        if (webhooks.isEmpty()) {
            Log.d(TAG, "No enabled webhooks matching $simLabel — dropping SMS")
            prefs.addSyslog("[FILTER] No webhook configured for $simLabel — SMS from $from dropped")
            return
        }

        for (webhook in webhooks) {
            // Per-webhook sender filter
            if (webhook.allowedSenders.isNotEmpty()) {
                val norm  = from.trim()
                val match = webhook.allowedSenders.any { it.trim().equals(norm, ignoreCase = true) }
                if (!match) {
                    Log.d(TAG, "Sender '$from' not in '${webhook.name}' allowed list — skipping")
                    prefs.addSyslog("[FILTER] '${webhook.name}' — sender '$from' not in whitelist, skipped")
                    continue
                }
            }

            val forwardIntent = Intent(context, ForwarderService::class.java).apply {
                action = ForwarderService.ACTION_FORWARD_SMS
                putExtra(ForwarderService.EXTRA_FROM,       from)
                putExtra(ForwarderService.EXTRA_MESSAGE,    body)
                putExtra(ForwarderService.EXTRA_TIMESTAMP,  ts)
                putExtra(ForwarderService.EXTRA_SIM_SLOT,   simSlot)
                putExtra(ForwarderService.EXTRA_WEBHOOK_ID, webhook.id)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(forwardIntent)
            } else {
                context.startService(forwardIntent)
            }
        }
    }

    private fun getSubscriptionId(intent: Intent): Int {
        var id = intent.getIntExtra("subscription", -1)
        if (id == -1) id = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        if (id == -1) id = intent.getIntExtra("slot", -1)
        return id
    }

    private fun getSimSlotIndex(context: Context, subscriptionId: Int): Int {
        if (subscriptionId == -1) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
            } catch (e: SecurityException) { 0 }
        }
        return 0
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
