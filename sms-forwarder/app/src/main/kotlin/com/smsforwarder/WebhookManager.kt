package com.smsforwarder

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object WebhookManager {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun send(
        webhookUrl: String,
        secret: String,
        from: String,
        message: String,
        sentTimestamp: Long,
        sentTo: String,
        deviceId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (webhookUrl.isBlank()) {
            onError("Webhook URL is not configured")
            return
        }

        val payload = JSONObject().apply {
            put("secret", secret)
            put("from", from)
            put("message", message)
            put("sent_timestamp", sentTimestamp)
            put("sent_to", sentTo)
            put("message_id", from)
            put("device_id", deviceId)
        }

        Log.d(TAG, "Sending payload: $payload")

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed: ${e.message}", e)
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Success — HTTP ${response.code}")
                        onSuccess()
                    } else {
                        val msg = "HTTP ${response.code}: ${response.message}"
                        Log.e(TAG, "Failed: $msg")
                        onError(msg)
                    }
                }
            }
        })
    }

    private const val TAG = "WebhookManager"
}
