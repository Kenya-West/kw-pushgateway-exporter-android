package com.kw.pushgatewayexporter.collector

import android.os.Build
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects static device hardware and build information from android.os.Build.
 */
class DeviceInfoCollector : Collector {

    override val name: String = "device_info"

    override fun collect(): List<MetricFamily> {
        return try {
            val supportedAbis = Build.SUPPORTED_ABIS.joinToString(",")
            val supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS.joinToString(",")
            val supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS.joinToString(",")

            listOf(
                MetricFamily(
                    name = "android_device_info",
                    help = "Device hardware and build information",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_device_info",
                            labels = mapOf(
                                "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
                                "model" to (Build.MODEL ?: "unknown"),
                                "brand" to (Build.BRAND ?: "unknown"),
                                "device" to (Build.DEVICE ?: "unknown"),
                                "product" to (Build.PRODUCT ?: "unknown"),
                                "hardware" to (Build.HARDWARE ?: "unknown"),
                                "fingerprint" to (Build.FINGERPRINT ?: "unknown"),
                                "build_id" to (Build.ID ?: "unknown"),
                                "display_id" to (Build.DISPLAY ?: "unknown"),
                                "build_type" to (Build.TYPE ?: "unknown"),
                                "build_tags" to (Build.TAGS ?: "unknown"),
                                "android_release" to (Build.VERSION.RELEASE ?: "unknown"),
                                "android_codename" to (Build.VERSION.CODENAME ?: "unknown"),
                                "android_incremental" to (Build.VERSION.INCREMENTAL ?: "unknown"),
                                "sdk_int" to Build.VERSION.SDK_INT.toString(),
                                "supported_abis" to supportedAbis
                            ),
                            value = 1.0
                        )
                    )
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
