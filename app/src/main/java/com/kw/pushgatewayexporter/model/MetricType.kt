package com.kw.pushgatewayexporter.model

enum class MetricType {
    GAUGE,
    COUNTER,
    UNTYPED,
    INFO,
    SUMMARY,
    HISTOGRAM;

    fun toPrometheusString(): String = name.lowercase()
}
