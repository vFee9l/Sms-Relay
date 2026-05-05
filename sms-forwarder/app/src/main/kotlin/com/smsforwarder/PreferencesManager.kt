package com.smsforwarder

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WEBHOOK_URL, value).apply() }

    var secret: String
        get() = prefs.getString(KEY_SECRET, "STC50") ?: "STC50"
        set(value) { prefs.edit().putString(KEY_SECRET, value).apply() }

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "6") ?: "6"
        set(value) { prefs.edit().putString(KEY_DEVICE_ID, value).apply() }

    var sim1Number: String
        get() = prefs.getString(KEY_SIM1_NUMBER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM1_NUMBER, value).apply() }

    var sim2Number: String
        get() = prefs.getString(KEY_SIM2_NUMBER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM2_NUMBER, value).apply() }

    var allowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_SENDERS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_ALLOWED_SENDERS, value).apply() }

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, value).apply() }

    var filterBySender: Boolean
        get() = prefs.getBoolean(KEY_FILTER_BY_SENDER, false)
        set(value) { prefs.edit().putBoolean(KEY_FILTER_BY_SENDER, value).apply() }

    fun addLog(entry: String) {
        val logs = getLogs().toMutableList()
        logs.add(0, entry)
        if (logs.size > 50) {
            logs.subList(50, logs.size).clear()
        }
        prefs.edit().putString(KEY_LOGS, logs.joinToString(LOG_SEPARATOR)).apply()
    }

    fun getLogs(): List<String> {
        val raw = prefs.getString(KEY_LOGS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(LOG_SEPARATOR)
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    companion object {
        private const val PREFS_NAME = "sms_forwarder_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_SECRET = "secret"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SIM1_NUMBER = "sim1_number"
        private const val KEY_SIM2_NUMBER = "sim2_number"
        private const val KEY_ALLOWED_SENDERS = "allowed_senders"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_FILTER_BY_SENDER = "filter_by_sender"
        private const val KEY_LOGS = "logs"
        private const val LOG_SEPARATOR = "\n||||\n"
    }
}
