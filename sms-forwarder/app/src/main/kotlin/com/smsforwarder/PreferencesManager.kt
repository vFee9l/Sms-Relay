package com.smsforwarder

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores global app settings and two rolling logs.
 *
 * Per-webhook configuration lives in [WebhookRepository].
 * Wallpaper watchdog settings live here.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Global service flag ──────────────────────────────────────────────────

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, v).apply() }

    // ── Wallpaper watchdog ───────────────────────────────────────────────────

    /** Whether the wallpaper watchdog is active. */
    var wallpaperEnabled: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_WALLPAPER_ENABLED, v).apply() }

    /** Threshold in minutes — if no SMS in this period → Delay wallpaper. */
    var wallpaperThresholdMinutes: Int
        get() = prefs.getInt(KEY_WALLPAPER_THRESHOLD, 30)
        set(v) { prefs.edit().putInt(KEY_WALLPAPER_THRESHOLD, v).apply() }

    /** Epoch ms of the last SMS received (0 = never). */
    var lastSmsReceivedTime: Long
        get() = prefs.getLong(KEY_LAST_SMS_TIME, 0L)
        set(v) { prefs.edit().putLong(KEY_LAST_SMS_TIME, v).apply() }

    // ── Forwarding history ───────────────────────────────────────────────────

    fun addLog(entry: String) = appendTo(KEY_LOGS, entry, 100)
    fun getLogs(): List<String> = readList(KEY_LOGS)
    fun clearLogs() { prefs.edit().remove(KEY_LOGS).apply() }

    // ── System log ───────────────────────────────────────────────────────────

    fun addSyslog(entry: String) = appendTo(KEY_SYSLOG, entry, 200)
    fun getSyslog(): List<String> = readList(KEY_SYSLOG)
    fun clearSyslog() { prefs.edit().remove(KEY_SYSLOG).apply() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun appendTo(key: String, entry: String, max: Int) {
        val list = readList(key).toMutableList()
        list.add(0, entry)
        if (list.size > max) list.subList(max, list.size).clear()
        prefs.edit().putString(key, list.joinToString(SEP)).apply()
    }

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEP)
    }

    companion object {
        private const val PREFS_NAME             = "sms_forwarder_prefs"
        private const val KEY_FORWARDING_ENABLED  = "forwarding_enabled"
        private const val KEY_WALLPAPER_ENABLED   = "wallpaper_enabled"
        private const val KEY_WALLPAPER_THRESHOLD = "wallpaper_threshold_min"
        private const val KEY_LAST_SMS_TIME       = "last_sms_time"
        private const val KEY_LOGS                = "logs"
        private const val KEY_SYSLOG              = "syslog"
        private const val SEP                     = "\n||||\n"
    }
}
