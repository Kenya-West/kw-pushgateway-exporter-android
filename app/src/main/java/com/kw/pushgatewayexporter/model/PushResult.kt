package com.kw.pushgatewayexporter.model

/**
 * Result of a push operation to Pushgateway.
 */
data class PushResult(
    val success: Boolean,
    val httpStatusCode: Int = -1,
    val errorMessage: String? = null,
    val responseBody: String? = null,
    val durationMillis: Long = 0,
    val payloadSizeBytes: Int = 0,
    val metricsCount: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
)
