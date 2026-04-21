package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.net.wifi.WifiManager
import com.kw.pushgatewayexporter.config.AppConfig
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects Wi-Fi connectivity metrics from WifiManager.
 */
class WifiCollector(
    private val context: Context,
    private val config: AppConfig
) : Collector {

    override val name: String = "wifi"

    @Suppress("DEPRECATION")
    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return emptyList()

            // Wi-Fi enabled state
            families.add(
                MetricFamily(
                    name = "android_wifi_enabled",
                    help = "Whether Wi-Fi is enabled (1=yes, 0=no)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_wifi_enabled",
                            value = if (wifiManager.isWifiEnabled) 1.0 else 0.0
                        )
                    )
                )
            )

            // Wi-Fi state (0=DISABLING, 1=DISABLED, 2=ENABLING, 3=ENABLED, 4=UNKNOWN)
            families.add(
                MetricFamily(
                    name = "android_wifi_state",
                    help = "Current Wi-Fi adapter state (0=disabling, 1=disabled, 2=enabling, 3=enabled, 4=unknown)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_wifi_state",
                            value = wifiManager.wifiState.toDouble()
                        )
                    )
                )
            )

            // Connection info (only if Wi-Fi is enabled)
            if (wifiManager.isWifiEnabled) {
                try {
                    val connectionInfo = wifiManager.connectionInfo
                    if (connectionInfo != null) {
                        val networkId = connectionInfo.networkId
                        val isConnected = networkId != -1

                        families.add(
                            MetricFamily(
                                name = "android_wifi_connected",
                                help = "Whether Wi-Fi is connected to a network (1=yes, 0=no)",
                                type = MetricType.GAUGE,
                                samples = listOf(
                                    MetricSample(
                                        name = "android_wifi_connected",
                                        value = if (isConnected) 1.0 else 0.0
                                    )
                                )
                            )
                        )

                        families.add(
                            MetricFamily(
                                name = "android_wifi_rssi_dbm",
                                help = "Current Wi-Fi signal strength in dBm",
                                type = MetricType.GAUGE,
                                samples = listOf(
                                    MetricSample(
                                        name = "android_wifi_rssi_dbm",
                                        value = connectionInfo.rssi.toDouble()
                                    )
                                )
                            )
                        )

                        families.add(
                            MetricFamily(
                                name = "android_wifi_link_speed_mbps",
                                help = "Current Wi-Fi link speed in Mbps",
                                type = MetricType.GAUGE,
                                samples = listOf(
                                    MetricSample(
                                        name = "android_wifi_link_speed_mbps",
                                        value = connectionInfo.linkSpeed.toDouble()
                                    )
                                )
                            )
                        )

                        if (networkId >= 0) {
                            families.add(
                                MetricFamily(
                                    name = "android_wifi_network_id",
                                    help = "Current Wi-Fi network ID",
                                    type = MetricType.GAUGE,
                                    samples = listOf(
                                        MetricSample(
                                            name = "android_wifi_network_id",
                                            value = networkId.toDouble()
                                        )
                                    )
                                )
                            )
                        }

                        // Sensitive labels (SSID, BSSID)
                        if (config.enableSensitiveWifiLabels) {
                            val rawSsid = connectionInfo.ssid ?: "unknown"
                            // Strip surrounding quotes from SSID
                            val ssid = rawSsid.removeSurrounding("\"")
                            val bssid = connectionInfo.bssid ?: "unknown"

                            families.add(
                                MetricFamily(
                                    name = "android_wifi_info",
                                    help = "Current Wi-Fi connection details",
                                    type = MetricType.GAUGE,
                                    samples = listOf(
                                        MetricSample(
                                            name = "android_wifi_info",
                                            labels = mapOf(
                                                "ssid" to ssid,
                                                "bssid" to bssid
                                            ),
                                            value = 1.0
                                        )
                                    )
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Connection info may not be available
                }

                // DHCP info
                try {
                    val dhcpInfo = wifiManager.dhcpInfo
                    if (dhcpInfo != null) {
                        if (dhcpInfo.leaseDuration > 0) {
                            families.add(
                                MetricFamily(
                                    name = "android_wifi_dhcp_lease_duration_seconds",
                                    help = "DHCP lease duration in seconds",
                                    type = MetricType.GAUGE,
                                    samples = listOf(
                                        MetricSample(
                                            name = "android_wifi_dhcp_lease_duration_seconds",
                                            value = dhcpInfo.leaseDuration.toDouble()
                                        )
                                    )
                                )
                            )
                        }

                        // Sensitive DHCP info (gateway, IP addresses)
                        if (config.enableSensitiveWifiLabels) {
                            val gateway = intToIp(dhcpInfo.gateway)
                            val ipAddress = intToIp(dhcpInfo.ipAddress)
                            val netmask = intToIp(dhcpInfo.netmask)
                            val dns1 = intToIp(dhcpInfo.dns1)
                            val dns2 = intToIp(dhcpInfo.dns2)
                            val serverAddress = intToIp(dhcpInfo.serverAddress)

                            families.add(
                                MetricFamily(
                                    name = "android_wifi_dhcp_info",
                                    help = "DHCP configuration details",
                                    type = MetricType.GAUGE,
                                    samples = listOf(
                                        MetricSample(
                                            name = "android_wifi_dhcp_info",
                                            labels = mapOf(
                                                "gateway" to gateway,
                                                "ip_address" to ipAddress,
                                                "netmask" to netmask,
                                                "dns1" to dns1,
                                                "dns2" to dns2,
                                                "server_address" to serverAddress
                                            ),
                                            value = 1.0
                                        )
                                    )
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // DHCP info may not be available
                }
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun intToIp(value: Int): String {
        return ((value and 0xFF).toString() + "." +
                ((value shr 8) and 0xFF) + "." +
                ((value shr 16) and 0xFF) + "." +
                ((value shr 24) and 0xFF))
    }
}
