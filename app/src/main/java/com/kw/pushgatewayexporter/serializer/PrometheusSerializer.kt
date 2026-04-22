package com.kw.pushgatewayexporter.serializer

import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample

/**
 * Serializes MetricFamily lists into Prometheus text exposition format.
 * Produces deterministic output: families sorted by name, samples in declaration order.
 * Uses LF (\n) line endings as required by Prometheus.
 */
object PrometheusSerializer {

    /**
     * Serialize a list of metric families to Prometheus text format.
     * Includes HELP, TYPE, and sample lines.
     * Ends with a trailing LF.
     */
    fun serialize(families: List<MetricFamily>): String {
        val sb = StringBuilder()
        // Merge families that share a name so HELP/TYPE are emitted once per metric.
        val merged = families
            .groupBy { it.name }
            .map { (_, group) ->
                val first = group.first()
                MetricFamily(
                    name = first.name,
                    help = first.help,
                    type = first.type,
                    samples = group.flatMap { it.samples }
                )
            }
        // Sort families by name for deterministic output
        val sorted = merged.sortedBy { it.name }
        for (family in sorted) {
            sb.append("# HELP ").append(family.name).append(' ')
                .append(escapeHelp(family.help)).append('\n')
            sb.append("# TYPE ").append(family.name).append(' ')
                .append(family.type.toPrometheusString()).append('\n')
            for (sample in family.samples) {
                sb.append(formatSample(sample)).append('\n')
            }
        }
        return sb.toString()
    }

    private fun formatSample(sample: MetricSample): String {
        val sb = StringBuilder()
        sb.append(sample.name)
        if (sample.labels.isNotEmpty()) {
            sb.append('{')
            val entries = sample.labels.entries.toList()
            for (i in entries.indices) {
                if (i > 0) sb.append(',')
                sb.append(entries[i].key)
                sb.append("=\"")
                sb.append(escapeLabelValue(entries[i].value))
                sb.append('"')
            }
            sb.append('}')
        }
        sb.append(' ')
        sb.append(formatValue(sample.value))
        return sb.toString()
    }

    private fun formatValue(value: Double): String {
        if (value == Double.POSITIVE_INFINITY) return "+Inf"
        if (value == Double.NEGATIVE_INFINITY) return "-Inf"
        if (value.isNaN()) return "NaN"
        // Use integer representation if value is a whole number
        if (value == value.toLong().toDouble() && !value.isInfinite()) {
            return value.toLong().toString()
        }
        return value.toString()
    }

    /**
     * Escape HELP text: backslash and newline.
     */
    private fun escapeHelp(text: String): String {
        return text.replace("\\", "\\\\").replace("\n", "\\n")
    }

    /**
     * Escape label values: backslash, double-quote, newline.
     */
    private fun escapeLabelValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    /**
     * URL-encode a label value for use in Pushgateway grouping key URL paths.
     * Handles special characters per RFC 3986.
     */
    fun urlEncodeLabelValue(value: String): String {
        return try {
            java.net.URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
        } catch (e: Exception) {
            value
        }
    }
}
