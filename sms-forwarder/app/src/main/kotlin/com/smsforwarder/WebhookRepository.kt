package com.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class WebhookRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Webhook> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Webhook.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(webhook: Webhook) {
        val list = getAll().toMutableList()
        val idx  = list.indexOfFirst { it.id == webhook.id }
        if (idx >= 0) list[idx] = webhook else list.add(webhook)
        persist(list)
    }

    fun delete(id: String) = persist(getAll().filter { it.id != id })

    /** Returns all enabled webhooks whose SIM filter matches [simSlot]. */
    fun matchingWebhooks(simSlot: Int): List<Webhook> =
        getAll().filter {
            it.enabled && it.url.isNotBlank() &&
                (it.simSlot == Webhook.SIM_ANY || it.simSlot == simSlot)
        }

    fun findById(id: String): Webhook? = getAll().firstOrNull { it.id == id }

    private fun persist(list: List<Webhook>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "webhook_repository"
        private const val KEY        = "webhooks"
    }
}
