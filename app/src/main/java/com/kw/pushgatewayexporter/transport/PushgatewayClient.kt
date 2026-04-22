package com.kw.pushgatewayexporter.transport

import android.util.Base64
import android.util.Log
import com.kw.pushgatewayexporter.config.AppConfig
import com.kw.pushgatewayexporter.model.PushResult
import com.kw.pushgatewayexporter.serializer.PrometheusSerializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * HTTP(S) client for pushing Prometheus metrics to a Pushgateway.
 *
 * Constructs the correct grouping-key URL path:
 *   /metrics/job/<JOB_NAME>/<LABEL_NAME>/<LABEL_VALUE>/...
 *
 * Supports PUT (full group replace) and POST (partial replace).
 * Supports DELETE for stale group cleanup.
 * Supports optional basic authentication and custom headers.
 * Supports exponential backoff retry.
 */
class PushgatewayClient(private val config: AppConfig) {

    companion object {
        private const val TAG = "PushgatewayClient"
        private val MEDIA_TYPE_PROMETHEUS: MediaType? =
            "text/plain; version=0.0.4; charset=utf-8".toMediaTypeOrNull()
    }

    private val client: OkHttpClient by lazy { buildClient() }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // We handle retries ourselves

        if (config.insecureTls) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure insecure TLS", e)
            }
        }

        return builder.build()
    }

    /**
     * Build the full Pushgateway URL with grouping key path.
     * Format: <base>/metrics/job/<job_name>/app_instance/<instance_id>{/extra_label/extra_value}
     */
    fun buildUrl(instanceId: String): String {
        val base = config.pushgatewayUrl.trimEnd('/')
        val sb = StringBuilder(base)
        sb.append("/metrics/job/")
        sb.append(PrometheusSerializer.urlEncodeLabelValue(config.jobName))
        sb.append("/app_instance/")
        sb.append(PrometheusSerializer.urlEncodeLabelValue(instanceId))

        for ((key, value) in config.additionalGroupingLabels) {
            sb.append('/')
            sb.append(PrometheusSerializer.urlEncodeLabelValue(key))
            sb.append('/')
            sb.append(PrometheusSerializer.urlEncodeLabelValue(value))
        }

        return sb.toString()
    }

    /**
     * Push metrics payload to Pushgateway with retry and backoff.
     */
    fun push(payload: String, instanceId: String): PushResult {
        val url = buildUrl(instanceId)
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null
        var lastHttpStatus = -1
        var lastResponseBody: String? = null

        for (attempt in 0..config.maxRetries) {
            if (attempt > 0) {
                val backoffMs = (config.retryBackoffBaseSeconds * 1000L) *
                    (1L shl (attempt - 1).coerceAtMost(5))
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                if (config.logLevel >= 4) {
                    Log.d(TAG, "Retry attempt $attempt after ${backoffMs}ms backoff")
                }
            }

            try {
                val body = payload.toRequestBody(MEDIA_TYPE_PROMETHEUS)
                val requestBuilder = Request.Builder()
                    .url(url)

                if (config.usePutMethod) {
                    requestBuilder.put(body)
                } else {
                    requestBuilder.post(body)
                }

                // Basic auth
                if (config.basicAuthUsername.isNotBlank()) {
                    val credentials = "${config.basicAuthUsername}:${config.basicAuthPassword}"
                    val encoded = Base64.encodeToString(
                        credentials.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    requestBuilder.header("Authorization", "Basic $encoded")
                }

                // Custom headers
                for ((key, value) in config.customHeaders) {
                    requestBuilder.header(key, value)
                }

                val response = client.newCall(requestBuilder.build()).execute()
                lastHttpStatus = response.code
                lastResponseBody = response.body?.string()

                if (response.isSuccessful) {
                    val duration = System.currentTimeMillis() - startTime
                    return PushResult(
                        success = true,
                        httpStatusCode = lastHttpStatus,
                        responseBody = lastResponseBody,
                        durationMillis = duration,
                        payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
                        metricsCount = countMetrics(payload)
                    )
                }

                // 400 = bad request, no point retrying
                if (lastHttpStatus == 400) {
                    val duration = System.currentTimeMillis() - startTime
                    return PushResult(
                        success = false,
                        httpStatusCode = lastHttpStatus,
                        errorMessage = "Bad request: $lastResponseBody",
                        responseBody = lastResponseBody,
                        durationMillis = duration,
                        payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
                        metricsCount = countMetrics(payload)
                    )
                }

                lastException = IOException("HTTP $lastHttpStatus: $lastResponseBody")

            } catch (e: IOException) {
                lastException = e
                if (config.logLevel >= 2) {
                    Log.w(TAG, "Push attempt $attempt failed: ${e.message}")
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        return PushResult(
            success = false,
            httpStatusCode = lastHttpStatus,
            errorMessage = lastException?.message ?: "Unknown error",
            responseBody = lastResponseBody,
            durationMillis = duration,
            payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
            metricsCount = countMetrics(payload)
        )
    }

    /**
     * DELETE a grouping key from Pushgateway (stale series cleanup).
     */
    fun delete(instanceId: String): PushResult {
        val url = buildUrl(instanceId)
        val startTime = System.currentTimeMillis()

        return try {
            val requestBuilder = Request.Builder().url(url).delete()

            if (config.basicAuthUsername.isNotBlank()) {
                val credentials = "${config.basicAuthUsername}:${config.basicAuthPassword}"
                val encoded = Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", "Basic $encoded")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime

            PushResult(
                success = response.isSuccessful,
                httpStatusCode = response.code,
                errorMessage = if (!response.isSuccessful) response.body?.string() else null,
                durationMillis = duration
            )
        } catch (e: IOException) {
            PushResult(
                success = false,
                errorMessage = e.message,
                durationMillis = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun countMetrics(payload: String): Int {
        return payload.lines().count { line ->
            line.isNotBlank() && !line.startsWith('#')
        }
    }
}
