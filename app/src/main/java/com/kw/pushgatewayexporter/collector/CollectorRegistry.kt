package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.util.Log
import com.kw.pushgatewayexporter.config.AppConfig
import com.kw.pushgatewayexporter.model.MetricFamily

/**
 * Central registry of all metric collectors.
 * Runs each enabled collector independently; a failure in one does not affect others.
 */
class CollectorRegistry(private val context: Context, private val config: AppConfig) {

    companion object {
        private const val TAG = "CollectorRegistry"
    }

    private fun buildCollectors(): List<Pair<Boolean, Collector>> = listOf(
        config.enableExporterSelf to ExporterSelfCollector(context),
        config.enableDeviceInfo to DeviceInfoCollector(),
        config.enableUptime to UptimeCollector(),
        config.enableMemory to MemoryCollector(context),
        config.enableStorage to StorageCollector(context),
        config.enableBattery to BatteryCollector(context),
        config.enableNetwork to NetworkCollector(context, config),
        config.enableWifi to WifiCollector(context, config),
        config.enableTelephony to TelephonyCollector(context),
        config.enableTraffic to TrafficCollector(context),
        config.enableDisplay to DisplayCollector(context),
        config.enableFeatures to FeatureCollector(context),
        config.enableSensors to SensorCollector(context)
    )

    /**
     * Run all enabled collectors. Each collector runs independently.
     * Returns a pair of (collected families, list of collector errors).
     */
    fun collectAll(): Pair<List<MetricFamily>, List<String>> {
        val allFamilies = mutableListOf<MetricFamily>()
        val errors = mutableListOf<String>()

        for ((enabled, collector) in buildCollectors()) {
            if (!enabled) continue
            try {
                val families = collector.collect()
                allFamilies.addAll(families)
                if (config.logLevel >= 4) {
                    Log.d(TAG, "Collector '${collector.name}' produced ${families.size} families")
                }
            } catch (e: Exception) {
                val msg = "Collector '${collector.name}' failed: ${e.message}"
                errors.add(msg)
                if (config.logLevel >= 2) {
                    Log.w(TAG, msg, e)
                }
            }
        }

        return allFamilies to errors
    }
}
