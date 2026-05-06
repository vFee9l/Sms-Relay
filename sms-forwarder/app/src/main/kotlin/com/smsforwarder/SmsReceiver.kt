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

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val from = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val ts   = messages[0].timestampMillis

        val prefs = PreferencesManager(context)

        // ── Wallpaper watchdog (independent of forwarding toggle) ─────────
        prefs.lastSmsReceivedTime = System.currentTimeMillis()
        if (prefs.wallpaperEnabled) {
            WallpaperWatchdog.onSmsReceived(context)
        }

        // ── SMS forwarding ────────────────────────────────────────────────
        if (!prefs.isForwardingEnabled) return

        val subscriptionId = getSubscriptionId(intent)
        val simSlot        = getSimSlot(context, subscriptionId)
        val sim            = "SIM${simSlot + 1}"

        Log.d(TAG, "SMS from=$from  sim=$sim")
        prefs.addSyslog("[SMS] from=$from on $sim")

        val webhooks = WebhookRepository(context).matchingWebhooks(simSlot)
        if (webhooks.isEmpty()) {
            prefs.addSyslog("[FILTER] No enabled webhook for $sim — SMS from $from dropped")
            return
        }

        for (wh in webhooks) {
            val fwd = Intent(context, ForwarderService::class.java).apply {
                action = ForwarderService.ACTION_FORWARD_SMS
                putExtra(ForwarderService.EXTRA_FROM,       from)
                putExtra(ForwarderService.EXTRA_MESSAGE,    body)
                putExtra(ForwarderService.EXTRA_TIMESTAMP,  ts)
                putExtra(ForwarderService.EXTRA_SIM_SLOT,   simSlot)
                putExtra(ForwarderService.EXTRA_WEBHOOK_ID, wh.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(fwd)
            else
                context.startService(fwd)
        }
    }

    private fun getSubscriptionId(intent: Intent): Int {
        var id = intent.getIntExtra("subscription", -1)
        if (id == -1) id = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        return id
    }

    private fun getSimSlot(context: Context, subscriptionId: Int): Int {
        if (subscriptionId == -1) return 0
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
            } else 0
        } catch (e: SecurityException) { 0 }
    }

    companion object { private const val TAG = "SmsReceiver" }
}
