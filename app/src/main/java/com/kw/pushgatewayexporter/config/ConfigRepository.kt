package com.kw.pushgatewayexporter.config

import android.content.Context
import android.content.SharedPreferences
import com.kw.pushgatewayexporter.model.PushResult

/**
 * Persists AppConfig and last push status to SharedPreferences.
 * SharedPreferences is available on API 1+ — safe for API 21.
 */
class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): AppConfig = AppConfig(
        pushgatewayUrl = prefs.getString(KEY_URL, "") ?: "",
        basicAuthUsername = prefs.getString(KEY_AUTH_USER, "") ?: "",
        basicAuthPassword = prefs.getString(KEY_AUTH_PASS, "") ?: "",
        insecureTls = prefs.getBoolean(KEY_INSECURE_TLS, false),
        jobName = prefs.getString(KEY_JOB_NAME, "android_device_exporter") ?: "android_device_exporter",
        includeDeviceLabels = prefs.getBoolean(KEY_INCLUDE_DEVICE_LABELS, true),
        schedulingEnabled = prefs.getBoolean(KEY_SCHEDULING_ENABLED, false),
        pushIntervalMinutes = prefs.getInt(KEY_PUSH_INTERVAL, 15),
        requireUnmeteredNetwork = prefs.getBoolean(KEY_REQUIRE_UNMETERED, false),
        requireCharging = prefs.getBoolean(KEY_REQUIRE_CHARGING, false),
        persistAcrossReboot = prefs.getBoolean(KEY_PERSIST_REBOOT, true),
        connectTimeoutSeconds = prefs.getInt(KEY_CONNECT_TIMEOUT, 15),
        readTimeoutSeconds = prefs.getInt(KEY_READ_TIMEOUT, 30),
        writeTimeoutSeconds = prefs.getInt(KEY_WRITE_TIMEOUT, 30),
        maxRetries = prefs.getInt(KEY_MAX_RETRIES, 3),
        retryBackoffBaseSeconds = prefs.getInt(KEY_RETRY_BACKOFF, 5),
        logLevel = prefs.getInt(KEY_LOG_LEVEL, 3),
        enableExporterSelf = prefs.getBoolean(KEY_ENABLE_EXPORTER_SELF, true),
        enableDeviceInfo = prefs.getBoolean(KEY_ENABLE_DEVICE_INFO, true),
        enableUptime = prefs.getBoolean(KEY_ENABLE_UPTIME, true),
        enableMemory = prefs.getBoolean(KEY_ENABLE_MEMORY, true),
        enableStorage = prefs.getBoolean(KEY_ENABLE_STORAGE, true),
        enableBattery = prefs.getBoolean(KEY_ENABLE_BATTERY, true),
        enableNetwork = prefs.getBoolean(KEY_ENABLE_NETWORK, true),
        enableWifi = prefs.getBoolean(KEY_ENABLE_WIFI, true),
        enableTelephony = prefs.getBoolean(KEY_ENABLE_TELEPHONY, true),
        enableTraffic = prefs.getBoolean(KEY_ENABLE_TRAFFIC, true),
        enableDisplay = prefs.getBoolean(KEY_ENABLE_DISPLAY, true),
        enableFeatures = prefs.getBoolean(KEY_ENABLE_FEATURES, true),
        enableSensors = prefs.getBoolean(KEY_ENABLE_SENSORS, true),
        enableSensitiveWifiLabels = prefs.getBoolean(KEY_SENSITIVE_WIFI, false),
        enableSensitiveNetworkLabels = prefs.getBoolean(KEY_SENSITIVE_NETWORK, false),
        usePutMethod = prefs.getBoolean(KEY_USE_PUT, true),
        dryRunMode = prefs.getBoolean(KEY_DRY_RUN, false)
    )

    fun saveConfig(config: AppConfig) {
        prefs.edit().apply {
            putString(KEY_URL, config.pushgatewayUrl)
            putString(KEY_AUTH_USER, config.basicAuthUsername)
            putString(KEY_AUTH_PASS, config.basicAuthPassword)
            putBoolean(KEY_INSECURE_TLS, config.insecureTls)
            putString(KEY_JOB_NAME, config.jobName)
            putBoolean(KEY_INCLUDE_DEVICE_LABELS, config.includeDeviceLabels)
            putBoolean(KEY_SCHEDULING_ENABLED, config.schedulingEnabled)
            putInt(KEY_PUSH_INTERVAL, config.pushIntervalMinutes)
            putBoolean(KEY_REQUIRE_UNMETERED, config.requireUnmeteredNetwork)
            putBoolean(KEY_REQUIRE_CHARGING, config.requireCharging)
            putBoolean(KEY_PERSIST_REBOOT, config.persistAcrossReboot)
            putInt(KEY_CONNECT_TIMEOUT, config.connectTimeoutSeconds)
            putInt(KEY_READ_TIMEOUT, config.readTimeoutSeconds)
            putInt(KEY_WRITE_TIMEOUT, config.writeTimeoutSeconds)
            putInt(KEY_MAX_RETRIES, config.maxRetries)
            putInt(KEY_RETRY_BACKOFF, config.retryBackoffBaseSeconds)
            putInt(KEY_LOG_LEVEL, config.logLevel)
            putBoolean(KEY_ENABLE_EXPORTER_SELF, config.enableExporterSelf)
            putBoolean(KEY_ENABLE_DEVICE_INFO, config.enableDeviceInfo)
            putBoolean(KEY_ENABLE_UPTIME, config.enableUptime)
            putBoolean(KEY_ENABLE_MEMORY, config.enableMemory)
            putBoolean(KEY_ENABLE_STORAGE, config.enableStorage)
            putBoolean(KEY_ENABLE_BATTERY, config.enableBattery)
            putBoolean(KEY_ENABLE_NETWORK, config.enableNetwork)
            putBoolean(KEY_ENABLE_WIFI, config.enableWifi)
            putBoolean(KEY_ENABLE_TELEPHONY, config.enableTelephony)
            putBoolean(KEY_ENABLE_TRAFFIC, config.enableTraffic)
            putBoolean(KEY_ENABLE_DISPLAY, config.enableDisplay)
            putBoolean(KEY_ENABLE_FEATURES, config.enableFeatures)
            putBoolean(KEY_ENABLE_SENSORS, config.enableSensors)
            putBoolean(KEY_SENSITIVE_WIFI, config.enableSensitiveWifiLabels)
            putBoolean(KEY_SENSITIVE_NETWORK, config.enableSensitiveNetworkLabels)
            putBoolean(KEY_USE_PUT, config.usePutMethod)
            putBoolean(KEY_DRY_RUN, config.dryRunMode)
            apply()
        }
    }

    // --- Last push result persistence ---

    fun saveLastPushResult(result: PushResult) {
        prefs.edit().apply {
            putBoolean(KEY_LAST_SUCCESS, result.success)
            putInt(KEY_LAST_HTTP_STATUS, result.httpStatusCode)
            putString(KEY_LAST_ERROR, result.errorMessage)
            putLong(KEY_LAST_PUSH_TIME, result.timestampMillis)
            putLong(KEY_LAST_PUSH_DURATION, result.durationMillis)
            putInt(KEY_LAST_PAYLOAD_SIZE, result.payloadSizeBytes)
            putInt(KEY_LAST_METRICS_COUNT, result.metricsCount)
            if (result.success) {
                putLong(KEY_LAST_SUCCESS_TIME, result.timestampMillis)
            } else {
                putLong(KEY_LAST_FAILURE_TIME, result.timestampMillis)
            }
            // Running totals
            putLong(KEY_PUSH_ATTEMPTS_TOTAL, getPushAttemptsTotal() + 1)
            if (!result.success) {
                putLong(KEY_PUSH_FAILURES_TOTAL, getPushFailuresTotal() + 1)
            }
            apply()
        }
    }

    fun getLastPushResult(): PushResult? {
        val time = prefs.getLong(KEY_LAST_PUSH_TIME, -1)
        if (time == -1L) return null
        return PushResult(
            success = prefs.getBoolean(KEY_LAST_SUCCESS, false),
            httpStatusCode = prefs.getInt(KEY_LAST_HTTP_STATUS, -1),
            errorMessage = prefs.getString(KEY_LAST_ERROR, null),
            durationMillis = prefs.getLong(KEY_LAST_PUSH_DURATION, 0),
            payloadSizeBytes = prefs.getInt(KEY_LAST_PAYLOAD_SIZE, 0),
            metricsCount = prefs.getInt(KEY_LAST_METRICS_COUNT, 0),
            timestampMillis = time
        )
    }

    fun getLastSuccessTime(): Long = prefs.getLong(KEY_LAST_SUCCESS_TIME, -1)
    fun getLastFailureTime(): Long = prefs.getLong(KEY_LAST_FAILURE_TIME, -1)
    fun getPushAttemptsTotal(): Long = prefs.getLong(KEY_PUSH_ATTEMPTS_TOTAL, 0)
    fun getPushFailuresTotal(): Long = prefs.getLong(KEY_PUSH_FAILURES_TOTAL, 0)

    companion object {
        private const val PREFS_NAME = "pushgateway_exporter_prefs"

        private const val KEY_URL = "pushgateway_url"
        private const val KEY_AUTH_USER = "auth_username"
        private const val KEY_AUTH_PASS = "auth_password"
        private const val KEY_INSECURE_TLS = "insecure_tls"
        private const val KEY_JOB_NAME = "job_name"
        private const val KEY_INCLUDE_DEVICE_LABELS = "include_device_labels"
        private const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
        private const val KEY_PUSH_INTERVAL = "push_interval_minutes"
        private const val KEY_REQUIRE_UNMETERED = "require_unmetered"
        private const val KEY_REQUIRE_CHARGING = "require_charging"
        private const val KEY_PERSIST_REBOOT = "persist_reboot"
        private const val KEY_CONNECT_TIMEOUT = "connect_timeout"
        private const val KEY_READ_TIMEOUT = "read_timeout"
        private const val KEY_WRITE_TIMEOUT = "write_timeout"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_RETRY_BACKOFF = "retry_backoff"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_ENABLE_EXPORTER_SELF = "enable_exporter_self"
        private const val KEY_ENABLE_DEVICE_INFO = "enable_device_info"
        private const val KEY_ENABLE_UPTIME = "enable_uptime"
        private const val KEY_ENABLE_MEMORY = "enable_memory"
        private const val KEY_ENABLE_STORAGE = "enable_storage"
        private const val KEY_ENABLE_BATTERY = "enable_battery"
        private const val KEY_ENABLE_NETWORK = "enable_network"
        private const val KEY_ENABLE_WIFI = "enable_wifi"
        private const val KEY_ENABLE_TELEPHONY = "enable_telephony"
        private const val KEY_ENABLE_TRAFFIC = "enable_traffic"
        private const val KEY_ENABLE_DISPLAY = "enable_display"
        private const val KEY_ENABLE_FEATURES = "enable_features"
        private const val KEY_ENABLE_SENSORS = "enable_sensors"
        private const val KEY_SENSITIVE_WIFI = "sensitive_wifi"
        private const val KEY_SENSITIVE_NETWORK = "sensitive_network"
        private const val KEY_USE_PUT = "use_put"
        private const val KEY_DRY_RUN = "dry_run"

        private const val KEY_LAST_SUCCESS = "last_push_success"
        private const val KEY_LAST_HTTP_STATUS = "last_push_http_status"
        private const val KEY_LAST_ERROR = "last_push_error"
        private const val KEY_LAST_PUSH_TIME = "last_push_time"
        private const val KEY_LAST_PUSH_DURATION = "last_push_duration"
        private const val KEY_LAST_PAYLOAD_SIZE = "last_payload_size"
        private const val KEY_LAST_METRICS_COUNT = "last_metrics_count"
        private const val KEY_LAST_SUCCESS_TIME = "last_success_time"
        private const val KEY_LAST_FAILURE_TIME = "last_failure_time"
        private const val KEY_PUSH_ATTEMPTS_TOTAL = "push_attempts_total"
        private const val KEY_PUSH_FAILURES_TOTAL = "push_failures_total"
    }
}
