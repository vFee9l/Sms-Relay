package com.smsforwarder

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object WebhookManager {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val trustAllClient: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslCtx = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * Send an SMS payload to the configured webhook.
     *
     * @param customHeaders   Extra HTTP headers to add to the request (merged with defaults).
     * @param extraBody       Extra fields appended to the JSON body.
     * @param disableSsl      When true, SSL certificate validation is skipped (self-signed certs).
     */
    fun send(
        webhookUrl: String,
        secret: String,
        from: String,
        message: String,
        sentTimestamp: Long,
        sentTo: String,
        deviceId: String,
        customHeaders: Map<String, String> = emptyMap(),
        extraBody: Map<String, String> = emptyMap(),
        disableSsl: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (webhookUrl.isBlank()) {
            onError("Webhook URL is not configured")
            return
        }

        // Build JSON body — standard fields first, then extra fields
        val payload = JSONObject().apply {
            put("secret", secret)
            put("from", from)
            put("message", message)
            put("sent_timestamp", sentTimestamp)
            put("sent_to", sentTo)
            put("message_id", UUID.randomUUID().toString())
            put("device_id", deviceId)
            extraBody.forEach { (k, v) -> put(k, v) }
        }

        Log.d(TAG, "→ $webhookUrl  ssl_skip=$disableSsl  extra_headers=${customHeaders.keys}  extra_body=${extraBody.keys}")

        // Build request — default headers first, then custom headers override
        val reqBuilder = Request.Builder()
            .url(webhookUrl)
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "T2-SMS-forwarding/1.0")

        customHeaders.forEach { (key, value) -> reqBuilder.header(key, value) }

        val client = if (disableSsl) trustAllClient else defaultClient

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
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
