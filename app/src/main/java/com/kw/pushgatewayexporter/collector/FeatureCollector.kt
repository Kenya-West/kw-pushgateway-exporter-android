package com.kw.pushgatewayexporter.collector

import android.content.Context
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects device feature availability metrics from PackageManager.
 */
class FeatureCollector(private val context: Context) : Collector {

    companion object {
        private const val MAX_FEATURES = 200
    }

    override val name: String = "features"

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val pm = context.packageManager
            val features = pm.systemAvailableFeatures ?: return emptyList()

            val featureSamples = mutableListOf<MetricSample>()
            var glesVersion: String? = null

            var count = 0
            for (feature in features) {
                if (count >= MAX_FEATURES) break

                if (feature.name != null) {
                    featureSamples.add(
                        MetricSample(
                            name = "android_feature_present",
                            labels = mapOf("name" to feature.name),
                            value = 1.0
                        )
                    )
                    count++
                } else {
                    // Feature with null name carries the GLES version
                    val major = feature.reqGlEsVersion shr 16
                    val minor = feature.reqGlEsVersion and 0xFFFF
                    glesVersion = "$major.$minor"
                }
            }

            if (featureSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_feature_present",
                        help = "Whether a system feature is available (1=present)",
                        type = MetricType.GAUGE,
                        samples = featureSamples
                    )
                )
            }

            if (glesVersion != null) {
                families.add(
                    MetricFamily(
                        name = "android_gles_version",
                        help = "Required OpenGL ES version",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_gles_version",
                                labels = mapOf("version" to glesVersion),
                                value = 1.0
                            )
                        )
                    )
                )
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }
}
