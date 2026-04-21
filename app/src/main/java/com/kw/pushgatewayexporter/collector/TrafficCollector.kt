package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects network traffic statistics from TrafficStats.
 */
class TrafficCollector(private val context: Context) : Collector {

    override val name: String = "traffic"

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val myUid = Process.myUid()

            // Total traffic
            addIfSupported(families, "android_network_total_receive_bytes",
                "Total number of bytes received across all interfaces",
                MetricType.COUNTER, TrafficStats.getTotalRxBytes())

            addIfSupported(families, "android_network_total_transmit_bytes",
                "Total number of bytes transmitted across all interfaces",
                MetricType.COUNTER, TrafficStats.getTotalTxBytes())

            addIfSupported(families, "android_network_total_receive_packets",
                "Total number of packets received across all interfaces",
                MetricType.COUNTER, TrafficStats.getTotalRxPackets())

            addIfSupported(families, "android_network_total_transmit_packets",
                "Total number of packets transmitted across all interfaces",
                MetricType.COUNTER, TrafficStats.getTotalTxPackets())

            // Mobile traffic
            addIfSupported(families, "android_network_mobile_receive_bytes",
                "Total number of bytes received on mobile interface",
                MetricType.COUNTER, TrafficStats.getMobileRxBytes())

            addIfSupported(families, "android_network_mobile_transmit_bytes",
                "Total number of bytes transmitted on mobile interface",
                MetricType.COUNTER, TrafficStats.getMobileTxBytes())

            addIfSupported(families, "android_network_mobile_receive_packets",
                "Total number of packets received on mobile interface",
                MetricType.COUNTER, TrafficStats.getMobileRxPackets())

            addIfSupported(families, "android_network_mobile_transmit_packets",
                "Total number of packets transmitted on mobile interface",
                MetricType.COUNTER, TrafficStats.getMobileTxPackets())

            // UID-specific traffic (this app)
            addIfSupported(families, "android_exporter_uid_receive_bytes",
                "Number of bytes received by this app's UID",
                MetricType.COUNTER, TrafficStats.getUidRxBytes(myUid))

            addIfSupported(families, "android_exporter_uid_transmit_bytes",
                "Number of bytes transmitted by this app's UID",
                MetricType.COUNTER, TrafficStats.getUidTxBytes(myUid))

            addIfSupported(families, "android_exporter_uid_receive_packets",
                "Number of packets received by this app's UID",
                MetricType.COUNTER, TrafficStats.getUidRxPackets(myUid))

            addIfSupported(families, "android_exporter_uid_transmit_packets",
                "Number of packets transmitted by this app's UID",
                MetricType.COUNTER, TrafficStats.getUidTxPackets(myUid))

            families
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addIfSupported(
        families: MutableList<MetricFamily>,
        metricName: String,
        help: String,
        type: MetricType,
        value: Long
    ) {
        if (value != TrafficStats.UNSUPPORTED) {
            families.add(
                MetricFamily(
                    name = metricName,
                    help = help,
                    type = type,
                    samples = listOf(
                        MetricSample(name = metricName, value = value.toDouble())
                    )
                )
            )
        }
    }
}
