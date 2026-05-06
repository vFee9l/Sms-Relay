package com.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when the SMS-inactivity threshold alarm triggers.
 *
 * Checks whether the threshold has actually been exceeded (an SMS may have
 * arrived just before the alarm) and sets the appropriate wallpaper.
 * If still within the threshold, reschedules the alarm for the remaining time.
 */
class WallpaperAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        if (!prefs.wallpaperEnabled) return

        val now      = System.currentTimeMillis()
        val lastSms  = prefs.lastSmsReceivedTime
        val threshMs = prefs.wallpaperThresholdMinutes * 60_000L
        val elapsed  = now - lastSms

        Log.d(TAG, "Alarm fired — elapsed=${elapsed / 60_000} min  threshold=${prefs.wallpaperThresholdMinutes} min")

        if (lastSms == 0L || elapsed >= threshMs) {
            // Threshold exceeded → switch to delay wallpaper
            WallpaperWatchdog.applyWallpaper(context, R.drawable.wallpaper_delay)
            prefs.addSyslog("[WALLPAPER] Alarm → threshold exceeded → There is Delay wallpaper")
            // No reschedule: next SMS will reset the timer
        } else {
            // SMS arrived recently — reschedule for remaining time
            WallpaperWatchdog.scheduleAlarm(context)
            Log.d(TAG, "SMS arrived recently, rescheduled alarm")
        }
    }

    companion object { private const val TAG = "WallpaperAlarmReceiver" }
}
