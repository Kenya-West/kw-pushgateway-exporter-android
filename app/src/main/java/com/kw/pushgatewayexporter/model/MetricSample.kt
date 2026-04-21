package com.kw.pushgatewayexporter.model

/**
 * A single Prometheus metric sample line.
 * Example: android_battery_level_ratio{status="charging"} 0.85
 */
data class MetricSample(
    val name: String,
    val labels: Map<String, String> = emptyMap(),
    val value: Double
)
