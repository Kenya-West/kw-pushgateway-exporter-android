package com.kw.pushgatewayexporter.collector

import android.app.ActivityManager
import android.content.Context
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects memory metrics from ActivityManager and MemoryInfo.
 */
class MemoryCollector(private val context: Context) : Collector {

    override val name: String = "memory"

    override fun collect(): List<MetricFamily> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return emptyList()

            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)

            val families = mutableListOf<MetricFamily>()

            families.add(
                MetricFamily(
                    name = "android_memory_available_bytes",
                    help = "Available memory in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_memory_available_bytes",
                            value = memInfo.availMem.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_memory_total_bytes",
                    help = "Total memory in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_memory_total_bytes",
                            value = memInfo.totalMem.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_memory_threshold_bytes",
                    help = "Memory threshold at which the system considers memory low in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_memory_threshold_bytes",
                            value = memInfo.threshold.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_memory_low",
                    help = "Whether the system is in a low memory condition (1=yes, 0=no)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_memory_low",
                            value = if (memInfo.lowMemory) 1.0 else 0.0
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_device_low_ram",
                    help = "Whether the device is classified as a low-RAM device (1=yes, 0=no)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_device_low_ram",
                            value = if (am.isLowRamDevice) 1.0 else 0.0
                        )
                    )
                )
            )

            families
        } catch (_: Exception) {
            emptyList()
        }
    }
}
