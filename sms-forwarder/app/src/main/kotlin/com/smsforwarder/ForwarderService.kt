package com.smsforwarder

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ForwarderService : Service() {

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Running — waiting for SMS"))
        PreferencesManager(this).addSyslog("[SERVICE] Started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORWARD_SMS  -> handleSms(intent)
            ACTION_STOP_SERVICE -> {
                PreferencesManager(this).addSyslog("[SERVICE] Stopped by user")
                stopForeground(true); stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferencesManager(this).addSyslog("[SERVICE] Destroyed — scheduling restart")
        sendBroadcast(Intent(ACTION_RESTART).setPackage(packageName))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────── handling

    private fun handleSms(intent: Intent) {
        val from      = intent.getStringExtra(EXTRA_FROM)       ?: return
        val message   = intent.getStringExtra(EXTRA_MESSAGE)    ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val simSlot   = intent.getIntExtra(EXTRA_SIM_SLOT, 0)
        val webhookId = intent.getStringExtra(EXTRA_WEBHOOK_ID) ?: return

        val prefs   = PreferencesManager(this)
        val webhook = WebhookRepository(this).findById(webhookId) ?: run {
            Log.w(TAG, "Webhook $webhookId not found — skipping")
            return
        }

        val timeStr = timeFmt.format(Date(timestamp))
        val sim     = webhook.simLabel

        updateNotification("[${webhook.name}] Forwarding from $from …")

        WebhookManager.send(
            webhookUrl    = webhook.url,
            bodyTemplate  = webhook.bodyTemplate,
            from          = from,
            message       = message,
            sentTimestamp = timestamp,
            customHeaders = webhook.customHeaders,
            disableSsl    = webhook.disableSsl,
            onSuccess = {
                val entry = "OK   $timeStr | $sim | ${webhook.name} | from: $from"
                prefs.addLog(entry)
                updateNotification("[${webhook.name}] ✓ $from at $timeStr")
                broadcastUi()
            },
            onError = { err ->
                val entry = "ERR  $timeStr | $sim | ${webhook.name} | from: $from | $err"
                prefs.addLog(entry)
                updateNotification("[${webhook.name}] ✗ Error from $from")
                broadcastUi()
            }
        )
    }

    // ─────────────────────────────────────────── notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "T2-SMS-forwarding", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ForwarderService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("T2-SMS-forwarding")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(s: String) =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(s))

    private fun broadcastUi() = sendBroadcast(Intent(ACTION_UPDATE_UI).setPackage(packageName))

    companion object {
        const val ACTION_FORWARD_SMS  = "com.smsforwarder.FORWARD_SMS"
        const val ACTION_STOP_SERVICE = "com.smsforwarder.STOP_SERVICE"
        const val ACTION_RESTART      = "com.smsforwarder.RESTART_SERVICE"
        const val ACTION_UPDATE_UI    = "com.smsforwarder.UPDATE_UI"

        const val EXTRA_FROM       = "extra_from"
        const val EXTRA_MESSAGE    = "extra_message"
        const val EXTRA_TIMESTAMP  = "extra_timestamp"
        const val EXTRA_SIM_SLOT   = "extra_sim_slot"
        const val EXTRA_WEBHOOK_ID = "extra_webhook_id"

        private const val NOTIF_ID   = 1001
        private const val CHANNEL_ID = "t2_sms_channel"
        private const val TAG        = "ForwarderService"
    }
}
