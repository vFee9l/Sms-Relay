package com.smsforwarder

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
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
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS").apply { init(null, tm, SecureRandom()) }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(ctx.socketFactory, tm[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * Sends an HTTP POST to [webhookUrl].
     *
     * The [bodyTemplate] is a raw JSON string that may contain the placeholders:
     *   %from%          → replaced with the sender's phone number / name
     *   %text%          → replaced with the SMS message body
     *   %receivedStamp% → replaced with the received epoch timestamp (ms, no quotes in JSON)
     *
     * Custom headers are merged on top of the default Content-Type / Accept headers.
     */
    fun send(
        webhookUrl: String,
        bodyTemplate: String,
        from: String,
        message: String,
        sentTimestamp: Long,
        customHeaders: Map<String, String> = emptyMap(),
        disableSsl: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (webhookUrl.isBlank()) { onError("Webhook URL is not configured"); return }

        // Substitute placeholders — escape values for JSON safety
        val resolvedBody = bodyTemplate
            .replace("%from%",          escapeJson(from))
            .replace("%text%",          escapeJson(message))
            .replace("%receivedStamp%", sentTimestamp.toString())

        Log.d(TAG, "POST → $webhookUrl  ssl_skip=$disableSsl")

        val reqBuilder = Request.Builder()
            .url(webhookUrl)
            .post(resolvedBody.toRequestBody(JSON_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept",       "application/json")
            .header("User-Agent",   "T2-SMS-forwarding/1.0")

        customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val client = if (disableSsl) trustAllClient else defaultClient

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed: ${e.message}")
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

    /** Escapes a value so it is safe to embed inside a JSON string literal. */
    private fun escapeJson(v: String): String = v
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private const val TAG = "WebhookManager"
}
