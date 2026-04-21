package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.os.Build
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects self-referential metrics about the exporter itself:
 * version info, install time, and last update time.
 */
class ExporterSelfCollector(private val context: Context) : Collector {

    override val name: String = "exporter_self"

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(context.packageName, 0)
            val appInfo = context.applicationInfo

            val versionName = pkgInfo.versionName ?: "unknown"
            @Suppress("DEPRECATION")
            val versionCode = pkgInfo.versionCode.toLong()
            val packageName = context.packageName
            val sdkInt = Build.VERSION.SDK_INT
            @Suppress("DEPRECATION")
            val targetSdk = appInfo.targetSdkVersion
            val buildType = if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release"

            families.add(
                MetricFamily(
                    name = "android_exporter_info",
                    help = "Exporter build and runtime information",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_exporter_info",
                            labels = mapOf(
                                "version_name" to versionName,
                                "version_code" to versionCode.toString(),
                                "package_name" to packageName,
                                "sdk_int" to sdkInt.toString(),
                                "target_sdk" to targetSdk.toString(),
                                "build_type" to buildType
                            ),
                            value = 1.0
                        )
                    )
                )
            )

            val firstInstallSeconds = pkgInfo.firstInstallTime / 1000.0
            families.add(
                MetricFamily(
                    name = "android_exporter_first_install_time_seconds",
                    help = "Unix timestamp of the first install time in seconds",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_exporter_first_install_time_seconds",
                            value = firstInstallSeconds
                        )
                    )
                )
            )

            val lastUpdateSeconds = pkgInfo.lastUpdateTime / 1000.0
            families.add(
                MetricFamily(
                    name = "android_exporter_last_update_time_seconds",
                    help = "Unix timestamp of the last update time in seconds",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_exporter_last_update_time_seconds",
                            value = lastUpdateSeconds
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
