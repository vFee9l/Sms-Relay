package com.smsforwarder

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Webhook(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Webhook",
    var url: String = "",
    /** 0 = SIM 1, 1 = SIM 2 */
    var simSlot: Int = SIM_1,
    /** Raw JSON body with %from%, %text%, %receivedStamp% placeholders */
    var bodyTemplate: String = DEFAULT_BODY,
    var customHeaders: Map<String, String> = emptyMap(),
    var disableSsl: Boolean = false,
    var enabled: Boolean = true
) {
    val simLabel: String get() = if (simSlot == SIM_2) "SIM 2" else "SIM 1"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id",           id)
        put("name",         name)
        put("url",          url)
        put("simSlot",      simSlot)
        put("bodyTemplate", bodyTemplate)
        put("disableSsl",   disableSsl)
        put("enabled",      enabled)
        put("customHeaders", JSONObject().also { o -> customHeaders.forEach { (k, v) -> o.put(k, v) } })
    }

    companion object {
        const val SIM_1 = 0
        const val SIM_2 = 1

        val DEFAULT_BODY = """
{
  "secret": "",
  "from": "%from%",
  "message": "%text%",
  "sent_timestamp": %receivedStamp%,
  "sent_to": "",
  "message_id": "%from%",
  "device_id": ""
}""".trimIndent()

        fun fromJson(o: JSONObject): Webhook {
            val hObj    = o.optJSONObject("customHeaders") ?: JSONObject()
            val headers = hObj.keys().asSequence().associateWith { hObj.getString(it) }
            return Webhook(
                id           = o.optString("id",           UUID.randomUUID().toString()),
                name         = o.optString("name",         "Webhook"),
                url          = o.optString("url",          ""),
                simSlot      = o.optInt("simSlot",         SIM_1),
                bodyTemplate = o.optString("bodyTemplate", DEFAULT_BODY),
                customHeaders= headers,
                disableSsl   = o.optBoolean("disableSsl",  false),
                enabled      = o.optBoolean("enabled",     true)
            )
        }
    }
}
