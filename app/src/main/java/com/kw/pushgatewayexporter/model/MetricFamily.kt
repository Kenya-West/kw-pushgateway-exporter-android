package com.kw.pushgatewayexporter.model

/**
 * A Prometheus metric family: a HELP line, TYPE line, and one or more samples.
 */
data class MetricFamily(
    val name: String,
    val help: String,
    val type: MetricType,
    val samples: List<MetricSample>
) {
    init {
        require(samples.all { it.name == name || it.name.startsWith("${name}_") }) {
            "All samples must belong to the metric family '$name'"
        }
    }
}
