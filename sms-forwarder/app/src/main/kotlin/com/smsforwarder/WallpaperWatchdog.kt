package com.smsforwarder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log

/**
 * Manages the SMS-activity-based wallpaper watchdog.
 *
 * Flow:
 *   SMS received  → setActiveWallpaper() + schedule alarm at (now + threshold)
 *   Alarm fires   → if time since last SMS >= threshold → setDelayWallpaper()
 *                   else                                → reschedule for remaining time
 *   Feature off   → cancel any pending alarm
 */
object WallpaperWatchdog {

    private const val TAG          = "WallpaperWatchdog"
    private const val REQUEST_CODE = 0x57

    // ─────────────────────────── public API

    /** Called immediately when an SMS arrives. */
    fun onSmsReceived(context: Context) {
        applyWallpaper(context, R.drawable.wallpaper_active)
        scheduleAlarm(context)
        PreferencesManager(context).addSyslog("[WALLPAPER] SMS received → Active wallpaper set")
    }

    /** Enable the watchdog: evaluate current state and start the alarm. */
    fun enable(context: Context) {
        val prefs = PreferencesManager(context)
        val elapsed = System.currentTimeMillis() - prefs.lastSmsReceivedTime
        val threshMs = prefs.wallpaperThresholdMinutes * 60_000L

        if (prefs.lastSmsReceivedTime == 0L || elapsed >= threshMs) {
            applyWallpaper(context, R.drawable.wallpaper_delay)
            prefs.addSyslog("[WALLPAPER] Enabled — no recent SMS → Delay wallpaper")
        } else {
            applyWallpaper(context, R.drawable.wallpaper_active)
            prefs.addSyslog("[WALLPAPER] Enabled — recent SMS → Active wallpaper")
        }
        scheduleAlarm(context)
    }

    /** Disable the watchdog: cancel any pending alarm. */
    fun disable(context: Context) {
        cancelAlarm(context)
        PreferencesManager(context).addSyslog("[WALLPAPER] Watchdog disabled")
    }

    /**
     * Evaluate state right now — sets the correct wallpaper immediately.
     * Called from [WallpaperAlarmReceiver] and by the user tapping "Check Now".
     */
    fun checkNow(context: Context) {
        val prefs   = PreferencesManager(context)
        val elapsed = System.currentTimeMillis() - prefs.lastSmsReceivedTime
        val threshMs = prefs.wallpaperThresholdMinutes * 60_000L

        if (prefs.lastSmsReceivedTime == 0L || elapsed >= threshMs) {
            applyWallpaper(context, R.drawable.wallpaper_delay)
            prefs.addSyslog("[WALLPAPER] Check → threshold exceeded (${elapsed / 60_000} min) → Delay")
        } else {
            applyWallpaper(context, R.drawable.wallpaper_active)
            prefs.addSyslog("[WALLPAPER] Check → within threshold → Active")
        }
    }

    /** Re-schedule the alarm based on the latest threshold + last SMS time. */
    fun scheduleAlarm(context: Context) {
        val prefs    = PreferencesManager(context)
        if (!prefs.wallpaperEnabled) return

        val now       = System.currentTimeMillis()
        val lastSms   = prefs.lastSmsReceivedTime
        val threshMs  = prefs.wallpaperThresholdMinutes * 60_000L
        val triggerAt = if (lastSms > 0L) lastSms + threshMs else now + threshMs

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms())
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        Log.d(TAG, "Alarm scheduled in ${(triggerAt - now) / 60_000} min")
    }

    fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
    }

    // ─────────────────────────── wallpaper helper

    fun applyWallpaper(context: Context, drawableResId: Int) {
        Thread {
            try {
                val wm = android.app.WallpaperManager.getInstance(context)
                val bm = BitmapFactory.decodeResource(context.resources, drawableResId)
                if (bm != null) {
                    wm.setBitmap(bm)
                    bm.recycle()
                    Log.d(TAG, "Wallpaper set: $drawableResId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set wallpaper: ${e.message}")
            }
        }.start()
    }

    // ─────────────────────────── internal

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, WallpaperAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
