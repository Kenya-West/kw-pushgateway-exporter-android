package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class MetricDoc(
    val name: String,
    val help: String,
    val type: String,
    val labels: List<String> = emptyList()
)

data class CollectorSection(
    val title: String,
    val description: String,
    val metrics: List<MetricDoc>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricCatalogScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metric Catalog") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(catalogSections) { section ->
                ExpandableSection(section)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ExpandableSection(section: CollectorSection) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.description, style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                section.metrics.forEach { metric ->
                    MetricDocRow(metric)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricDocRow(metric: MetricDoc) {
    Column {
        Text(
            metric.name,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text("${metric.type} — ${metric.help}", style = MaterialTheme.typography.bodySmall)
        if (metric.labels.isNotEmpty()) {
            Text(
                "Labels: ${metric.labels.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val catalogSections = listOf(
    CollectorSection(
        title = "1. Exporter Self-Metrics",
        description = "Metrics about the exporter app itself",
        metrics = listOf(
            MetricDoc("android_exporter_info", "Exporter app metadata", "gauge",
                listOf("version_name", "version_code", "package_name", "sdk_int", "target_sdk", "build_type")),
            MetricDoc("android_exporter_first_install_time_seconds", "First install time as unix timestamp", "gauge"),
            MetricDoc("android_exporter_last_update_time_seconds", "Last update time as unix timestamp", "gauge"),
            MetricDoc("android_exporter_last_collect_success_unixtime", "Timestamp of last successful collection", "gauge"),
            MetricDoc("android_exporter_last_push_success_unixtime", "Timestamp of last successful push", "gauge"),
            MetricDoc("android_exporter_last_push_failure_unixtime", "Timestamp of last failed push", "gauge"),
            MetricDoc("android_exporter_collect_duration_seconds", "Time spent collecting metrics", "gauge"),
            MetricDoc("android_exporter_push_duration_seconds", "Time spent pushing metrics", "gauge"),
            MetricDoc("android_exporter_payload_size_bytes", "Size of last payload in bytes", "gauge"),
            MetricDoc("android_exporter_metrics_count", "Number of metric families exported", "gauge"),
            MetricDoc("android_exporter_push_attempts_total", "Total push attempts", "counter"),
            MetricDoc("android_exporter_push_failures_total", "Total failed push attempts", "counter"),
            MetricDoc("android_exporter_job_scheduled", "Whether periodic job is scheduled (0/1)", "gauge"),
            MetricDoc("android_exporter_collector_errors_total", "Number of collectors that failed", "gauge")
        )
    ),
    CollectorSection(
        title = "2. Device / OS / Build Info",
        description = "Static device and OS metadata",
        metrics = listOf(
            MetricDoc("android_device_info", "Device and OS build metadata", "gauge",
                listOf("manufacturer", "model", "brand", "device", "product", "hardware",
                    "fingerprint", "build_id", "display_id", "build_type", "build_tags",
                    "android_release", "android_codename", "android_incremental", "sdk_int",
                    "supported_abis", "supported_32_bit_abis", "supported_64_bit_abis"))
        )
    ),
    CollectorSection(
        title = "3. Time / Uptime",
        description = "System uptime and boot time",
        metrics = listOf(
            MetricDoc("android_device_elapsed_realtime_milliseconds", "Milliseconds since boot including sleep", "gauge"),
            MetricDoc("android_device_uptime_milliseconds", "Milliseconds since boot excluding sleep", "gauge"),
            MetricDoc("android_device_boot_time_seconds", "Estimated boot time as unix timestamp", "gauge")
        )
    ),
    CollectorSection(
        title = "4. Memory",
        description = "System memory information",
        metrics = listOf(
            MetricDoc("android_memory_available_bytes", "Available RAM in bytes", "gauge"),
            MetricDoc("android_memory_total_bytes", "Total RAM in bytes", "gauge"),
            MetricDoc("android_memory_threshold_bytes", "Low memory threshold in bytes", "gauge"),
            MetricDoc("android_memory_low", "Whether system considers memory low (0/1)", "gauge"),
            MetricDoc("android_device_low_ram", "Whether device is a low-RAM device (0/1)", "gauge")
        )
    ),
    CollectorSection(
        title = "5. Storage",
        description = "Filesystem storage metrics per mount point",
        metrics = listOf(
            MetricDoc("android_storage_total_bytes", "Total storage in bytes", "gauge", listOf("storage_scope")),
            MetricDoc("android_storage_available_bytes", "Available storage in bytes", "gauge", listOf("storage_scope")),
            MetricDoc("android_storage_free_bytes", "Free storage in bytes", "gauge", listOf("storage_scope")),
            MetricDoc("android_storage_block_size_bytes", "Block size in bytes", "gauge", listOf("storage_scope")),
            MetricDoc("android_storage_external_state", "External storage state", "gauge", listOf("state")),
            MetricDoc("android_storage_external_emulated", "Whether external storage is emulated (0/1)", "gauge"),
            MetricDoc("android_storage_external_removable", "Whether external storage is removable (0/1)", "gauge")
        )
    ),
    CollectorSection(
        title = "6. Battery / Power",
        description = "Battery state and power metrics",
        metrics = listOf(
            MetricDoc("android_battery_level_ratio", "Battery level as 0.0-1.0 ratio", "gauge"),
            MetricDoc("android_battery_level_percent", "Battery level percentage", "gauge"),
            MetricDoc("android_battery_scale", "Battery level scale", "gauge"),
            MetricDoc("android_battery_status", "Battery status numeric code", "gauge"),
            MetricDoc("android_battery_status_info", "Battery status as labeled info", "gauge", listOf("status")),
            MetricDoc("android_battery_health", "Battery health numeric code", "gauge"),
            MetricDoc("android_battery_health_info", "Battery health as labeled info", "gauge", listOf("health")),
            MetricDoc("android_battery_plugged", "Plugged state (0=none, 1=AC, 2=USB, 4=wireless)", "gauge"),
            MetricDoc("android_battery_present", "Whether battery is present (0/1)", "gauge"),
            MetricDoc("android_battery_temperature_celsius", "Battery temperature in Celsius", "gauge"),
            MetricDoc("android_battery_voltage_millivolts", "Battery voltage in millivolts", "gauge"),
            MetricDoc("android_battery_technology_info", "Battery technology", "gauge", listOf("technology")),
            MetricDoc("android_battery_charge_counter_microampere_hours", "Remaining charge counter", "gauge"),
            MetricDoc("android_battery_current_now_microamperes", "Instantaneous current", "gauge"),
            MetricDoc("android_battery_current_average_microamperes", "Average current", "gauge"),
            MetricDoc("android_battery_energy_nano_watt_hours", "Remaining energy", "gauge"),
            MetricDoc("android_battery_capacity_percent", "Battery capacity from BatteryManager", "gauge")
        )
    ),
    CollectorSection(
        title = "7. Connectivity / Network",
        description = "Network topology and connectivity",
        metrics = listOf(
            MetricDoc("android_network_active_connected", "Active network connected (0/1)", "gauge"),
            MetricDoc("android_network_active_roaming", "Active network roaming (0/1)", "gauge"),
            MetricDoc("android_network_active_info", "Active network metadata", "gauge",
                listOf("type_name", "subtype_name")),
            MetricDoc("android_network_count", "Total number of networks", "gauge"),
            MetricDoc("android_network_up", "Network interface up (0/1)", "gauge", listOf("iface")),
            MetricDoc("android_network_dns_servers_count", "DNS servers count per interface", "gauge", listOf("iface")),
            MetricDoc("android_network_routes_count", "Routes count per interface", "gauge", listOf("iface")),
            MetricDoc("android_network_link_addresses_count", "Link addresses count per interface", "gauge", listOf("iface")),
            MetricDoc("android_network_proxy_configured", "Whether proxy is configured (0/1)", "gauge", listOf("iface"))
        )
    ),
    CollectorSection(
        title = "8. Wi-Fi",
        description = "Wi-Fi connection metrics",
        metrics = listOf(
            MetricDoc("android_wifi_enabled", "Wi-Fi enabled (0/1)", "gauge"),
            MetricDoc("android_wifi_state", "Wi-Fi state numeric", "gauge"),
            MetricDoc("android_wifi_connected", "Wi-Fi connected (0/1)", "gauge"),
            MetricDoc("android_wifi_rssi_dbm", "Signal strength in dBm", "gauge"),
            MetricDoc("android_wifi_link_speed_mbps", "Link speed in Mbps", "gauge"),
            MetricDoc("android_wifi_network_id", "Network ID", "gauge"),
            MetricDoc("android_wifi_info", "Wi-Fi metadata (sensitive: SSID/BSSID)", "gauge",
                listOf("ssid", "bssid")),
            MetricDoc("android_wifi_dhcp_lease_duration_seconds", "DHCP lease duration", "gauge")
        )
    ),
    CollectorSection(
        title = "9. Telephony",
        description = "Non-privileged telephony metrics",
        metrics = listOf(
            MetricDoc("android_telephony_present", "Whether telephony feature exists (0/1)", "gauge"),
            MetricDoc("android_telephony_phone_type", "Phone type (0=none, 1=GSM, 2=CDMA, 3=SIP)", "gauge"),
            MetricDoc("android_telephony_network_type", "Network type numeric", "gauge"),
            MetricDoc("android_telephony_data_state", "Data state (0-3)", "gauge"),
            MetricDoc("android_telephony_call_state", "Call state (0-2)", "gauge"),
            MetricDoc("android_telephony_operator_info", "Operator metadata", "gauge",
                listOf("operator_name", "operator_numeric", "country_iso", "sim_country_iso"))
        )
    ),
    CollectorSection(
        title = "10. Traffic Counters",
        description = "Network traffic statistics from TrafficStats",
        metrics = listOf(
            MetricDoc("android_network_total_receive_bytes", "Total bytes received", "counter"),
            MetricDoc("android_network_total_transmit_bytes", "Total bytes transmitted", "counter"),
            MetricDoc("android_network_total_receive_packets", "Total packets received", "counter"),
            MetricDoc("android_network_total_transmit_packets", "Total packets transmitted", "counter"),
            MetricDoc("android_network_mobile_receive_bytes", "Mobile bytes received", "counter"),
            MetricDoc("android_network_mobile_transmit_bytes", "Mobile bytes transmitted", "counter"),
            MetricDoc("android_network_mobile_receive_packets", "Mobile packets received", "counter"),
            MetricDoc("android_network_mobile_transmit_packets", "Mobile packets transmitted", "counter"),
            MetricDoc("android_exporter_uid_receive_bytes", "Exporter app bytes received", "counter"),
            MetricDoc("android_exporter_uid_transmit_bytes", "Exporter app bytes transmitted", "counter"),
            MetricDoc("android_exporter_uid_receive_packets", "Exporter app packets received", "counter"),
            MetricDoc("android_exporter_uid_transmit_packets", "Exporter app packets transmitted", "counter")
        )
    ),
    CollectorSection(
        title = "11. Display",
        description = "Display metrics",
        metrics = listOf(
            MetricDoc("android_display_width_pixels", "Display width in pixels", "gauge"),
            MetricDoc("android_display_height_pixels", "Display height in pixels", "gauge"),
            MetricDoc("android_display_density_dpi", "Display density in DPI", "gauge"),
            MetricDoc("android_display_xdpi", "Exact X DPI", "gauge"),
            MetricDoc("android_display_ydpi", "Exact Y DPI", "gauge"),
            MetricDoc("android_display_rotation", "Display rotation (0-3)", "gauge"),
            MetricDoc("android_display_info", "Display metadata", "gauge", listOf("name"))
        )
    ),
    CollectorSection(
        title = "12. Hardware Features",
        description = "System feature inventory",
        metrics = listOf(
            MetricDoc("android_feature_present", "Hardware/software feature present (1)", "gauge", listOf("name")),
            MetricDoc("android_gles_version", "OpenGL ES version", "gauge", listOf("version"))
        )
    ),
    CollectorSection(
        title = "13. Sensors",
        description = "Sensor inventory metadata",
        metrics = listOf(
            MetricDoc("android_sensor_info", "Sensor metadata", "gauge",
                listOf("name", "vendor", "type", "type_name", "version", "wake_up")),
            MetricDoc("android_sensor_power_milliamps", "Sensor power usage", "gauge",
                listOf("name", "type")),
            MetricDoc("android_sensor_resolution", "Sensor resolution", "gauge",
                listOf("name", "type")),
            MetricDoc("android_sensor_max_range", "Sensor maximum range", "gauge",
                listOf("name", "type")),
            MetricDoc("android_sensor_min_delay_microseconds", "Sensor minimum delay", "gauge",
                listOf("name", "type"))
        )
    )
)
