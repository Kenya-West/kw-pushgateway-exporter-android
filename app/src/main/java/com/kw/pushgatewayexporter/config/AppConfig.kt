package com.kw.pushgatewayexporter.config

/**
 * All configurable parameters for the exporter.
 * Persisted via SharedPreferences.
 */
data class AppConfig(
    // Pushgateway connection
    val pushgatewayUrl: String = "",
    val basicAuthUsername: String = "",
    val basicAuthPassword: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val insecureTls: Boolean = false,

    // Identity and grouping
    val jobName: String = "android_device_exporter",
    val instanceLabel: String = "instance_id",
    val includeDeviceLabels: Boolean = true,
    val additionalGroupingLabels: Map<String, String> = emptyMap(),

    // Scheduling
    val schedulingEnabled: Boolean = false,
    val pushIntervalMinutes: Int = 15,
    val requireUnmeteredNetwork: Boolean = false,
    val requireCharging: Boolean = false,
    val persistAcrossReboot: Boolean = true,

    // Timeouts and retry
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 30,
    val writeTimeoutSeconds: Int = 30,
    val maxRetries: Int = 3,
    val retryBackoffBaseSeconds: Int = 5,

    // Log level: 0=OFF, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG, 5=VERBOSE
    val logLevel: Int = 3,

    // Metric families enable/disable
    val enableExporterSelf: Boolean = true,
    val enableDeviceInfo: Boolean = true,
    val enableUptime: Boolean = true,
    val enableMemory: Boolean = true,
    val enableStorage: Boolean = true,
    val enableBattery: Boolean = true,
    val enableNetwork: Boolean = true,
    val enableWifi: Boolean = true,
    val enableTelephony: Boolean = true,
    val enableTraffic: Boolean = true,
    val enableDisplay: Boolean = true,
    val enableFeatures: Boolean = true,
    val enableSensors: Boolean = true,

    // Privacy
    val enableSensitiveWifiLabels: Boolean = false,
    val enableSensitiveNetworkLabels: Boolean = false,

    // Push method
    val usePutMethod: Boolean = true, // true=PUT (full replace), false=POST (partial)

    // Dry-run mode
    val dryRunMode: Boolean = false
) {
    val isConfigured: Boolean
        get() = pushgatewayUrl.isNotBlank()
}
