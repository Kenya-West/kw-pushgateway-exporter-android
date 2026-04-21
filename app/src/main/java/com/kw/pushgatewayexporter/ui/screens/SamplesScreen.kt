package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplesScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Package Structure", "Sample Payload", "Sample Request", "Prometheus Config")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samples & Reference") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }

            val content = when (selectedTab) {
                0 -> PACKAGE_STRUCTURE
                1 -> SAMPLE_PAYLOAD
                2 -> SAMPLE_REQUEST
                3 -> PROMETHEUS_CONFIG
                else -> ""
            }

            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        }
    }
}

private val PACKAGE_STRUCTURE = """
com.kw.pushgatewayexporter/
├── PushgatewayExporterApp.kt          # Application class
├── MainActivity.kt                     # Single-activity Compose host
│
├── model/
│   ├── MetricType.kt                  # GAUGE, COUNTER, etc.
│   ├── MetricSample.kt               # Single metric line
│   ├── MetricFamily.kt               # HELP + TYPE + samples
│   └── PushResult.kt                 # Push operation result
│
├── config/
│   ├── AppConfig.kt                   # All configuration fields
│   └── ConfigRepository.kt           # SharedPreferences persistence
│
├── identity/
│   └── InstanceIdentity.kt           # Installation UUID management
│
├── collector/
│   ├── Collector.kt                   # Collector interface
│   ├── CollectorRegistry.kt          # Runs all enabled collectors
│   ├── ExporterSelfCollector.kt       # 1. Exporter app metadata
│   ├── DeviceInfoCollector.kt         # 2. Build/device info
│   ├── UptimeCollector.kt            # 3. Uptime/boot time
│   ├── MemoryCollector.kt            # 4. RAM metrics
│   ├── StorageCollector.kt           # 5. Filesystem metrics
│   ├── BatteryCollector.kt           # 6. Battery/power
│   ├── NetworkCollector.kt           # 7. Connectivity
│   ├── WifiCollector.kt              # 8. Wi-Fi
│   ├── TelephonyCollector.kt         # 9. Telephony
│   ├── TrafficCollector.kt           # 10. Traffic counters
│   ├── DisplayCollector.kt           # 11. Display
│   ├── FeatureCollector.kt           # 12. Hardware features
│   └── SensorCollector.kt            # 13. Sensor inventory
│
├── serializer/
│   └── PrometheusSerializer.kt       # Text exposition format
│
├── transport/
│   └── PushgatewayClient.kt          # HTTP(S) Pushgateway client
│
├── scheduler/
│   ├── MetricsJobService.kt          # JobScheduler service
│   ├── BootReceiver.kt               # BOOT_COMPLETED receiver
│   └── SchedulerManager.kt           # Schedule management
│
└── ui/
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   └── Theme.kt
    ├── navigation/
    │   └── AppNavigation.kt
    ├── viewmodel/
    │   ├── MainViewModel.kt
    │   └── ConfigViewModel.kt
    └── screens/
        ├── MainScreen.kt             # Dashboard
        ├── ConfigScreen.kt           # Settings
        ├── PreviewScreen.kt          # Payload preview
        ├── MetricCatalogScreen.kt    # Metric documentation
        └── SamplesScreen.kt          # Samples & reference
""".trimIndent()

private val SAMPLE_PAYLOAD = """
# HELP android_battery_capacity_percent Battery capacity from BatteryManager
# TYPE android_battery_capacity_percent gauge
android_battery_capacity_percent 85
# HELP android_battery_charge_counter_microampere_hours Battery remaining charge counter in microampere-hours
# TYPE android_battery_charge_counter_microampere_hours gauge
android_battery_charge_counter_microampere_hours 2850000
# HELP android_battery_current_average_microamperes Battery average current in microamperes
# TYPE android_battery_current_average_microamperes gauge
android_battery_current_average_microamperes -245000
# HELP android_battery_current_now_microamperes Battery instantaneous current in microamperes
# TYPE android_battery_current_now_microamperes gauge
android_battery_current_now_microamperes -312000
# HELP android_battery_health Battery health numeric code
# TYPE android_battery_health gauge
android_battery_health 2
# HELP android_battery_health_info Battery health as labeled info metric
# TYPE android_battery_health_info gauge
android_battery_health_info{health="good"} 1
# HELP android_battery_level_percent Battery level as percentage
# TYPE android_battery_level_percent gauge
android_battery_level_percent 85
# HELP android_battery_level_ratio Battery level as 0.0-1.0 ratio
# TYPE android_battery_level_ratio gauge
android_battery_level_ratio 0.85
# HELP android_battery_plugged Battery plugged state
# TYPE android_battery_plugged gauge
android_battery_plugged 2
# HELP android_battery_present Whether battery is present
# TYPE android_battery_present gauge
android_battery_present 1
# HELP android_battery_scale Battery level scale
# TYPE android_battery_scale gauge
android_battery_scale 100
# HELP android_battery_status Battery charge status numeric code
# TYPE android_battery_status gauge
android_battery_status 2
# HELP android_battery_status_info Battery charge status as labeled info metric
# TYPE android_battery_status_info gauge
android_battery_status_info{status="charging"} 1
# HELP android_battery_technology_info Battery technology
# TYPE android_battery_technology_info gauge
android_battery_technology_info{technology="Li-ion"} 1
# HELP android_battery_temperature_celsius Battery temperature in Celsius
# TYPE android_battery_temperature_celsius gauge
android_battery_temperature_celsius 28.5
# HELP android_battery_voltage_millivolts Battery voltage in millivolts
# TYPE android_battery_voltage_millivolts gauge
android_battery_voltage_millivolts 4125
# HELP android_device_boot_time_seconds Estimated device boot time as unix timestamp
# TYPE android_device_boot_time_seconds gauge
android_device_boot_time_seconds 1.7137728e+09
# HELP android_device_elapsed_realtime_milliseconds Milliseconds since boot including deep sleep
# TYPE android_device_elapsed_realtime_milliseconds gauge
android_device_elapsed_realtime_milliseconds 8.64e+07
# HELP android_device_info Device and OS build metadata
# TYPE android_device_info gauge
android_device_info{manufacturer="Google",model="Pixel 6",brand="google",device="oriole",product="oriole",hardware="oriole",build_id="AP1A.240405.002",build_type="user",android_release="14",sdk_int="34",supported_abis="arm64-v8a,armeabi-v7a,armeabi"} 1
# HELP android_device_uptime_milliseconds Milliseconds since boot excluding deep sleep
# TYPE android_device_uptime_milliseconds gauge
android_device_uptime_milliseconds 7.2e+07
# HELP android_display_density_dpi Display density in DPI
# TYPE android_display_density_dpi gauge
android_display_density_dpi 420
# HELP android_display_height_pixels Display height in pixels
# TYPE android_display_height_pixels gauge
android_display_height_pixels 2400
# HELP android_display_rotation Display rotation (0=0°, 1=90°, 2=180°, 3=270°)
# TYPE android_display_rotation gauge
android_display_rotation 0
# HELP android_display_width_pixels Display width in pixels
# TYPE android_display_width_pixels gauge
android_display_width_pixels 1080
# HELP android_exporter_collect_duration_seconds Time spent collecting all metrics in seconds
# TYPE android_exporter_collect_duration_seconds gauge
android_exporter_collect_duration_seconds 0.342
# HELP android_exporter_info Exporter app metadata
# TYPE android_exporter_info gauge
android_exporter_info{version_name="1.0.0",version_code="1",package_name="com.kw.pushgatewayexporter",sdk_int="34",target_sdk="34",build_type="release"} 1
# HELP android_exporter_job_scheduled Whether the periodic push job is currently scheduled
# TYPE android_exporter_job_scheduled gauge
android_exporter_job_scheduled 1
# HELP android_exporter_metrics_count Number of metric families in this export
# TYPE android_exporter_metrics_count gauge
android_exporter_metrics_count 48
# HELP android_exporter_push_attempts_total Total number of push attempts since app install
# TYPE android_exporter_push_attempts_total counter
android_exporter_push_attempts_total 142
# HELP android_exporter_push_failures_total Total number of failed push attempts since app install
# TYPE android_exporter_push_failures_total counter
android_exporter_push_failures_total 3
# HELP android_memory_available_bytes Available RAM in bytes
# TYPE android_memory_available_bytes gauge
android_memory_available_bytes 3.221225472e+09
# HELP android_memory_low Whether the system considers memory low
# TYPE android_memory_low gauge
android_memory_low 0
# HELP android_memory_threshold_bytes Low memory threshold in bytes
# TYPE android_memory_threshold_bytes gauge
android_memory_threshold_bytes 2.68435456e+08
# HELP android_memory_total_bytes Total RAM in bytes
# TYPE android_memory_total_bytes gauge
android_memory_total_bytes 7.516192768e+09
# HELP android_network_total_receive_bytes Total network bytes received since boot
# TYPE android_network_total_receive_bytes counter
android_network_total_receive_bytes 1.523482624e+09
# HELP android_network_total_transmit_bytes Total network bytes transmitted since boot
# TYPE android_network_total_transmit_bytes counter
android_network_total_transmit_bytes 2.56901632e+08
# HELP android_storage_available_bytes Available storage in bytes
# TYPE android_storage_available_bytes gauge
android_storage_available_bytes{storage_scope="internal_data"} 4.5097156608e+10
# HELP android_storage_total_bytes Total storage in bytes
# TYPE android_storage_total_bytes gauge
android_storage_total_bytes{storage_scope="internal_data"} 1.1274289152e+11
# HELP android_wifi_connected Whether Wi-Fi is connected
# TYPE android_wifi_connected gauge
android_wifi_connected 1
# HELP android_wifi_enabled Whether Wi-Fi is enabled
# TYPE android_wifi_enabled gauge
android_wifi_enabled 1
# HELP android_wifi_link_speed_mbps Wi-Fi link speed in Mbps
# TYPE android_wifi_link_speed_mbps gauge
android_wifi_link_speed_mbps 866
# HELP android_wifi_rssi_dbm Wi-Fi signal strength in dBm
# TYPE android_wifi_rssi_dbm gauge
android_wifi_rssi_dbm -42
""".trimIndent()

private val SAMPLE_REQUEST = """
=== HTTP PUT Request to Pushgateway ===

PUT /metrics/job/android_device_exporter/app_instance/a1b2c3d4-e5f6-7890-abcd-ef1234567890 HTTP/1.1
Host: pushgateway.example.com:9091
Content-Type: text/plain; version=0.0.4; charset=utf-8
Authorization: Basic dXNlcjpwYXNzd29yZA==

# HELP android_exporter_info Exporter app metadata
# TYPE android_exporter_info gauge
android_exporter_info{version_name="1.0.0",version_code="1",...} 1
# HELP android_battery_level_ratio Battery level as 0.0-1.0 ratio
# TYPE android_battery_level_ratio gauge
android_battery_level_ratio 0.85
...
(full payload as shown in Sample Payload tab)


=== Grouping Key URL Structure ===

Base pattern:
  /metrics/job/<JOB_NAME>/app_instance/<INSTALLATION_UUID>

Examples:
  /metrics/job/android_device_exporter/app_instance/a1b2c3d4-...
  /metrics/job/my_phone/app_instance/a1b2c3d4-.../env/production

With additional labels:
  /metrics/job/<JOB>/label1/value1/label2/value2


=== HTTP Methods ===

PUT  - Replace ALL metrics in the group (default, recommended)
POST - Replace only metrics with matching names (partial update)
DELETE - Remove the entire group (cleanup stale instances)


=== Response Codes ===

200 OK       - Metrics accepted
202 Accepted - Metrics queued (some implementations)
400 Bad Request - Malformed payload or grouping key
              → Check response body for error details


=== DELETE Request (Stale Group Cleanup) ===

DELETE /metrics/job/android_device_exporter/app_instance/OLD_UUID HTTP/1.1
Host: pushgateway.example.com:9091
Authorization: Basic dXNlcjpwYXNzd29yZA==
""".trimIndent()

private val PROMETHEUS_CONFIG = """
=== Prometheus scrape_configs for Pushgateway ===

# prometheus.yml
scrape_configs:
  - job_name: 'pushgateway'
    honor_labels: true          # CRITICAL: preserves job/instance from pushed metrics
    scrape_interval: 30s
    static_configs:
      - targets: ['pushgateway.example.com:9091']
        labels:
          __metrics_path__: /metrics

# Notes:
# - honor_labels: true is REQUIRED so that Prometheus uses the job and
#   instance labels pushed by the Android app, rather than overwriting
#   them with the Pushgateway's own job/instance.
#
# - Pushgateway automatically adds these timestamps per group:
#     push_time_seconds{...}
#     push_failure_time_seconds{...}
#
# - The Android exporter does NOT include timestamps in individual samples.
#   Prometheus uses its own scrape timestamp, which is correct behavior
#   when using honor_labels: true with a Pushgateway.
#
# - If multiple Android devices push to the same Pushgateway, each device
#   has a unique grouping key (app_instance=<UUID>), so their metrics
#   are kept separate.


=== Alerting Example ===

# Alert if a device hasn't pushed in 30 minutes
groups:
  - name: android_exporter_alerts
    rules:
      - alert: AndroidExporterDown
        expr: |
          time() - push_time_seconds{job="android_device_exporter"} > 1800
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Android exporter {{ ${'$'}labels.app_instance }} not pushing"

      - alert: AndroidBatteryLow
        expr: android_battery_level_ratio < 0.15
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Android device battery below 15%"

      - alert: AndroidStorageLow
        expr: |
          android_storage_available_bytes{storage_scope="internal_data"}
          / android_storage_total_bytes{storage_scope="internal_data"} < 0.1
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "Android device storage below 10%"


=== Grafana Dashboard Ideas ===

Panel ideas for a Grafana dashboard:
  1. Battery level over time (line chart)
  2. Memory usage (available vs total, stacked area)
  3. Storage usage per scope (bar gauge)
  4. Network traffic rates (derivative of counters)
  5. Wi-Fi signal strength (gauge)
  6. Device info table (latest android_device_info labels)
  7. Push success rate (push_attempts - push_failures / push_attempts)
  8. Uptime since last boot
  9. Battery temperature trend
  10. Exporter collection duration
""".trimIndent()
