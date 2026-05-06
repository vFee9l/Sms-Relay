package com.smsforwarder

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Webhook(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Webhook",
    var url: String = "",
    var secret: String = "",
    var deviceId: String = "",
    var phone: String = "",
    /** -1 = any SIM, 0 = SIM 1, 1 = SIM 2 */
    var simSlot: Int = SIM_ANY,
    var allowedSenders: List<String> = emptyList(),
    var customHeaders: Map<String, String> = emptyMap(),
    var extraBody: Map<String, String> = emptyMap(),
    var disableSsl: Boolean = false,
    var enabled: Boolean = true
) {
    val simLabel: String
        get() = when (simSlot) {
            SIM_1  -> "SIM 1"
            SIM_2  -> "SIM 2"
            else   -> "Any SIM"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id",       id)
        put("name",     name)
        put("url",      url)
        put("secret",   secret)
        put("deviceId", deviceId)
        put("phone",    phone)
        put("simSlot",  simSlot)
        put("disableSsl", disableSsl)
        put("enabled",  enabled)
        put("allowedSenders", JSONArray().also { arr -> allowedSenders.forEach { arr.put(it) } })
        put("customHeaders",  JSONObject().also { obj -> customHeaders.forEach { (k, v) -> obj.put(k, v) } })
        put("extraBody",      JSONObject().also { obj -> extraBody.forEach { (k, v) -> obj.put(k, v) } })
    }

    companion object {
        const val SIM_ANY = -1
        const val SIM_1   = 0
        const val SIM_2   = 1

        fun fromJson(o: JSONObject): Webhook {
            val sendersArr = o.optJSONArray("allowedSenders") ?: JSONArray()
            val senders    = (0 until sendersArr.length()).map { sendersArr.getString(it) }

            val headersObj = o.optJSONObject("customHeaders") ?: JSONObject()
            val headers    = headersObj.keys().asSequence().associateWith { headersObj.getString(it) }

            val bodyObj    = o.optJSONObject("extraBody") ?: JSONObject()
            val body       = bodyObj.keys().asSequence().associateWith { bodyObj.getString(it) }

            return Webhook(
                id             = o.optString("id",       UUID.randomUUID().toString()),
                name           = o.optString("name",     "Webhook"),
                url            = o.optString("url",      ""),
                secret         = o.optString("secret",   ""),
                deviceId       = o.optString("deviceId", ""),
                phone          = o.optString("phone",    ""),
                simSlot        = o.optInt("simSlot",     SIM_ANY),
                allowedSenders = senders,
                customHeaders  = headers,
                extraBody      = body,
                disableSsl     = o.optBoolean("disableSsl", false),
                enabled        = o.optBoolean("enabled",    true)
            )
        }
    }
}
