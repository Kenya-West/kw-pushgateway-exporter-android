package com.kw.pushgatewayexporter.collector

import android.os.SystemClock
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects device uptime and boot time metrics from SystemClock.
 */
class UptimeCollector : Collector {

    override val name: String = "uptime"

    override fun collect(): List<MetricFamily> {
        return try {
            val elapsedRealtime = SystemClock.elapsedRealtime().toDouble()
            val uptimeMillis = SystemClock.uptimeMillis().toDouble()
            val bootTimeSeconds = System.currentTimeMillis() / 1000.0 - elapsedRealtime / 1000.0

            listOf(
                MetricFamily(
                    name = "android_device_elapsed_realtime_milliseconds",
                    help = "Milliseconds since device boot including sleep time",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_device_elapsed_realtime_milliseconds",
                            value = elapsedRealtime
                        )
                    )
                ),
                MetricFamily(
                    name = "android_device_uptime_milliseconds",
                    help = "Milliseconds since device boot excluding sleep time",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_device_uptime_milliseconds",
                            value = uptimeMillis
                        )
                    )
                ),
                MetricFamily(
                    name = "android_device_boot_time_seconds",
                    help = "Unix timestamp of device boot time in seconds",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_device_boot_time_seconds",
                            value = bootTimeSeconds
                        )
                    )
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
