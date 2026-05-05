package com.smsforwarder

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── SIM 1 ─────────────────────────────────────────────────────────────────

    var sim1Enabled: Boolean
        get() = prefs.getBoolean(KEY_SIM1_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_SIM1_ENABLED, value).apply() }

    var sim1WebhookUrl: String
        get() = prefs.getString(KEY_SIM1_WEBHOOK_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM1_WEBHOOK_URL, value).apply() }

    var sim1Secret: String
        get() = prefs.getString(KEY_SIM1_SECRET, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM1_SECRET, value).apply() }

    var sim1DeviceId: String
        get() = prefs.getString(KEY_SIM1_DEVICE_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM1_DEVICE_ID, value).apply() }

    var sim1PhoneNumber: String
        get() = prefs.getString(KEY_SIM1_PHONE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM1_PHONE, value).apply() }

    /** Per-SIM 1 allowed senders. Empty = forward all. */
    var sim1AllowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_SIM1_ALLOWED_SENDERS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_SIM1_ALLOWED_SENDERS, value).apply() }

    // ── SIM 2 ─────────────────────────────────────────────────────────────────

    var sim2Enabled: Boolean
        get() = prefs.getBoolean(KEY_SIM2_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_SIM2_ENABLED, value).apply() }

    var sim2WebhookUrl: String
        get() = prefs.getString(KEY_SIM2_WEBHOOK_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM2_WEBHOOK_URL, value).apply() }

    var sim2Secret: String
        get() = prefs.getString(KEY_SIM2_SECRET, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM2_SECRET, value).apply() }

    var sim2DeviceId: String
        get() = prefs.getString(KEY_SIM2_DEVICE_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM2_DEVICE_ID, value).apply() }

    var sim2PhoneNumber: String
        get() = prefs.getString(KEY_SIM2_PHONE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SIM2_PHONE, value).apply() }

    /** Per-SIM 2 allowed senders. Empty = forward all. */
    var sim2AllowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_SIM2_ALLOWED_SENDERS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_SIM2_ALLOWED_SENDERS, value).apply() }

    // ── Per-slot helpers ──────────────────────────────────────────────────────

    fun isSimEnabled(slot: Int)          = if (slot == 1) sim2Enabled          else sim1Enabled
    fun webhookUrlForSlot(slot: Int)     = if (slot == 1) sim2WebhookUrl       else sim1WebhookUrl
    fun secretForSlot(slot: Int)         = if (slot == 1) sim2Secret           else sim1Secret
    fun deviceIdForSlot(slot: Int)       = if (slot == 1) sim2DeviceId         else sim1DeviceId
    fun allowedSendersForSlot(slot: Int) = if (slot == 1) sim2AllowedSenders   else sim1AllowedSenders
    fun phoneForSlot(slot: Int)          = if (slot == 1)
        sim2PhoneNumber.ifBlank { "SIM2" }
    else
        sim1PhoneNumber.ifBlank { "SIM1" }

    // ── Global service flag ───────────────────────────────────────────────────

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, value).apply() }

    // ── Activity log ──────────────────────────────────────────────────────────

    fun addLog(entry: String) {
        val logs = getLogs().toMutableList()
        logs.add(0, entry)
        if (logs.size > 50) logs.subList(50, logs.size).clear()
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

        private const val KEY_SIM1_ENABLED          = "sim1_enabled"
        private const val KEY_SIM1_WEBHOOK_URL       = "sim1_webhook_url"
        private const val KEY_SIM1_SECRET            = "sim1_secret"
        private const val KEY_SIM1_DEVICE_ID         = "sim1_device_id"
        private const val KEY_SIM1_PHONE             = "sim1_phone"
        private const val KEY_SIM1_ALLOWED_SENDERS   = "sim1_allowed_senders"

        private const val KEY_SIM2_ENABLED          = "sim2_enabled"
        private const val KEY_SIM2_WEBHOOK_URL       = "sim2_webhook_url"
        private const val KEY_SIM2_SECRET            = "sim2_secret"
        private const val KEY_SIM2_DEVICE_ID         = "sim2_device_id"
        private const val KEY_SIM2_PHONE             = "sim2_phone"
        private const val KEY_SIM2_ALLOWED_SENDERS   = "sim2_allowed_senders"

        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_LOGS               = "logs"
        private const val LOG_SEPARATOR          = "\n||||\n"
    }
}
