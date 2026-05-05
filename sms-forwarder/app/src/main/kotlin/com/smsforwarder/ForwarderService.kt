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

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Running — waiting for SMS"))
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORWARD_SMS -> handleSms(intent)
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stop requested by user")
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // START_STICKY: Android restarts this service automatically if it's killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed — broadcasting restart signal")
        // Tell ServiceRestartReceiver to bring us back up
        sendBroadcast(Intent(ACTION_RESTART).setPackage(packageName))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ SMS handling

    private fun handleSms(intent: Intent) {
        val from = intent.getStringExtra(EXTRA_FROM) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val sentTo = intent.getStringExtra(EXTRA_SENT_TO) ?: ""

        val prefs = PreferencesManager(this)
        val timeStr = timeFormat.format(Date(timestamp))

        updateNotification("Forwarding from $from …")
        Log.d(TAG, "Forwarding SMS from $from to ${prefs.webhookUrl}")

        WebhookManager.send(
            webhookUrl = prefs.webhookUrl,
            secret = prefs.secret,
            from = from,
            message = message,
            sentTimestamp = timestamp,
            sentTo = sentTo,
            deviceId = prefs.deviceId,
            onSuccess = {
                val entry = "OK  $timeStr | $from → webhook"
                prefs.addLog(entry)
                Log.d(TAG, entry)
                updateNotification("Last: $from at $timeStr ✓")
                broadcastUiUpdate()
            },
            onError = { error ->
                val entry = "ERR $timeStr | $from | $error"
                prefs.addLog(entry)
                Log.e(TAG, entry)
                updateNotification("Error from $from — $error")
                broadcastUiUpdate()
            }
        )
    }

    // ------------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS Forwarding persistent service"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ForwarderService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun broadcastUiUpdate() {
        sendBroadcast(Intent(ACTION_UPDATE_UI).setPackage(packageName))
    }

    // ------------------------------------------------------------------ companion

    companion object {
        const val ACTION_FORWARD_SMS = "com.smsforwarder.FORWARD_SMS"
        const val ACTION_STOP_SERVICE = "com.smsforwarder.STOP_SERVICE"
        const val ACTION_RESTART = "com.smsforwarder.RESTART_SERVICE"
        const val ACTION_UPDATE_UI = "com.smsforwarder.UPDATE_UI"

        const val EXTRA_FROM = "extra_from"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_SENT_TO = "extra_sent_to"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_forwarder_channel"
        private const val TAG = "ForwarderService"
    }
}
