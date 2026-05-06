package com.smsforwarder

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ════════════════════════════════════════════════════════════ SIM 1

    var sim1Enabled: Boolean
        get() = prefs.getBoolean(KEY_SIM1_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_SIM1_ENABLED, v).apply() }

    var sim1WebhookUrl: String
        get() = prefs.getString(KEY_SIM1_WEBHOOK_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM1_WEBHOOK_URL, v).apply() }

    var sim1Secret: String
        get() = prefs.getString(KEY_SIM1_SECRET, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM1_SECRET, v).apply() }

    var sim1DeviceId: String
        get() = prefs.getString(KEY_SIM1_DEVICE_ID, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM1_DEVICE_ID, v).apply() }

    var sim1PhoneNumber: String
        get() = prefs.getString(KEY_SIM1_PHONE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM1_PHONE, v).apply() }

    /** Allowed senders — empty = forward all */
    var sim1AllowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_SIM1_ALLOWED_SENDERS, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM1_ALLOWED_SENDERS, v).apply() }

    /** Custom HTTP headers as "key:::value" strings */
    var sim1CustomHeaders: Set<String>
        get() = prefs.getStringSet(KEY_SIM1_CUSTOM_HEADERS, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM1_CUSTOM_HEADERS, v).apply() }

    /** Extra JSON body fields as "key:::value" strings */
    var sim1ExtraBody: Set<String>
        get() = prefs.getStringSet(KEY_SIM1_EXTRA_BODY, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM1_EXTRA_BODY, v).apply() }

    var sim1DisableSsl: Boolean
        get() = prefs.getBoolean(KEY_SIM1_DISABLE_SSL, false)
        set(v) { prefs.edit().putBoolean(KEY_SIM1_DISABLE_SSL, v).apply() }

    // ════════════════════════════════════════════════════════════ SIM 2

    var sim2Enabled: Boolean
        get() = prefs.getBoolean(KEY_SIM2_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_SIM2_ENABLED, v).apply() }

    var sim2WebhookUrl: String
        get() = prefs.getString(KEY_SIM2_WEBHOOK_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM2_WEBHOOK_URL, v).apply() }

    var sim2Secret: String
        get() = prefs.getString(KEY_SIM2_SECRET, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM2_SECRET, v).apply() }

    var sim2DeviceId: String
        get() = prefs.getString(KEY_SIM2_DEVICE_ID, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM2_DEVICE_ID, v).apply() }

    var sim2PhoneNumber: String
        get() = prefs.getString(KEY_SIM2_PHONE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SIM2_PHONE, v).apply() }

    var sim2AllowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_SIM2_ALLOWED_SENDERS, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM2_ALLOWED_SENDERS, v).apply() }

    var sim2CustomHeaders: Set<String>
        get() = prefs.getStringSet(KEY_SIM2_CUSTOM_HEADERS, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM2_CUSTOM_HEADERS, v).apply() }

    var sim2ExtraBody: Set<String>
        get() = prefs.getStringSet(KEY_SIM2_EXTRA_BODY, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_SIM2_EXTRA_BODY, v).apply() }

    var sim2DisableSsl: Boolean
        get() = prefs.getBoolean(KEY_SIM2_DISABLE_SSL, false)
        set(v) { prefs.edit().putBoolean(KEY_SIM2_DISABLE_SSL, v).apply() }

    // ════════════════════════════════════════════════════════════ Per-slot helpers

    fun isSimEnabled(slot: Int)            = if (slot == 1) sim2Enabled          else sim1Enabled
    fun webhookUrlForSlot(slot: Int)       = if (slot == 1) sim2WebhookUrl       else sim1WebhookUrl
    fun secretForSlot(slot: Int)           = if (slot == 1) sim2Secret           else sim1Secret
    fun deviceIdForSlot(slot: Int)         = if (slot == 1) sim2DeviceId         else sim1DeviceId
    fun allowedSendersForSlot(slot: Int)   = if (slot == 1) sim2AllowedSenders   else sim1AllowedSenders
    fun customHeadersForSlot(slot: Int)    = if (slot == 1) sim2CustomHeaders    else sim1CustomHeaders
    fun extraBodyForSlot(slot: Int)        = if (slot == 1) sim2ExtraBody        else sim1ExtraBody
    fun disableSslForSlot(slot: Int)       = if (slot == 1) sim2DisableSsl       else sim1DisableSsl
    fun phoneForSlot(slot: Int)            = if (slot == 1)
        sim2PhoneNumber.ifBlank { "SIM2" } else sim1PhoneNumber.ifBlank { "SIM1" }

    // ════════════════════════════════════════════════════════════ Global flag

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, v).apply() }

    // ════════════════════════════════════════════════════════════ Forwarding history log

    fun addLog(entry: String) {
        val logs = getLogs().toMutableList()
        logs.add(0, entry)
        if (logs.size > 100) logs.subList(100, logs.size).clear()
        prefs.edit().putString(KEY_LOGS, logs.joinToString(LOG_SEP)).apply()
    }

    fun getLogs(): List<String> {
        val raw = prefs.getString(KEY_LOGS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(LOG_SEP)
    }

    fun clearLogs() { prefs.edit().remove(KEY_LOGS).apply() }

    // ════════════════════════════════════════════════════════════ Syslog

    fun addSyslog(entry: String) {
        val logs = getSyslog().toMutableList()
        logs.add(0, entry)
        if (logs.size > 200) logs.subList(200, logs.size).clear()
        prefs.edit().putString(KEY_SYSLOG, logs.joinToString(LOG_SEP)).apply()
    }

    fun getSyslog(): List<String> {
        val raw = prefs.getString(KEY_SYSLOG, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(LOG_SEP)
    }

    fun clearSyslog() { prefs.edit().remove(KEY_SYSLOG).apply() }

    // ════════════════════════════════════════════════════════════ KV set helpers

    companion object {
        const val KV_SEP = ":::"

        /** Parse a Set<"key:::value"> into a Map<String, String>. */
        fun parseKvSet(set: Set<String>): Map<String, String> =
            set.mapNotNull { entry ->
                val idx = entry.indexOf(KV_SEP)
                if (idx < 0) null
                else entry.substring(0, idx) to entry.substring(idx + KV_SEP.length)
            }.toMap()

        /** Encode a key+value pair for storage. */
        fun encodeKv(key: String, value: String) = "$key$KV_SEP$value"

        private const val PREFS_NAME = "sms_forwarder_prefs"

        private const val KEY_SIM1_ENABLED         = "sim1_enabled"
        private const val KEY_SIM1_WEBHOOK_URL      = "sim1_webhook_url"
        private const val KEY_SIM1_SECRET           = "sim1_secret"
        private const val KEY_SIM1_DEVICE_ID        = "sim1_device_id"
        private const val KEY_SIM1_PHONE            = "sim1_phone"
        private const val KEY_SIM1_ALLOWED_SENDERS  = "sim1_allowed_senders"
        private const val KEY_SIM1_CUSTOM_HEADERS   = "sim1_custom_headers"
        private const val KEY_SIM1_EXTRA_BODY       = "sim1_extra_body"
        private const val KEY_SIM1_DISABLE_SSL      = "sim1_disable_ssl"

        private const val KEY_SIM2_ENABLED         = "sim2_enabled"
        private const val KEY_SIM2_WEBHOOK_URL      = "sim2_webhook_url"
        private const val KEY_SIM2_SECRET           = "sim2_secret"
        private const val KEY_SIM2_DEVICE_ID        = "sim2_device_id"
        private const val KEY_SIM2_PHONE            = "sim2_phone"
        private const val KEY_SIM2_ALLOWED_SENDERS  = "sim2_allowed_senders"
        private const val KEY_SIM2_CUSTOM_HEADERS   = "sim2_custom_headers"
        private const val KEY_SIM2_EXTRA_BODY       = "sim2_extra_body"
        private const val KEY_SIM2_DISABLE_SSL      = "sim2_disable_ssl"

        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_LOGS               = "logs"
        private const val KEY_SYSLOG             = "syslog"
        private const val LOG_SEP                = "\n||||\n"
    }
}
