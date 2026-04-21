package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.kw.pushgatewayexporter.config.AppConfig
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects network connectivity metrics from ConnectivityManager.
 */
class NetworkCollector(
    private val context: Context,
    private val config: AppConfig
) : Collector {

    override val name: String = "network"

    @Suppress("DEPRECATION")
    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return emptyList()

            // Active network info (deprecated but necessary for API 21)
            val activeInfo = cm.activeNetworkInfo
            if (activeInfo != null) {
                families.add(
                    MetricFamily(
                        name = "android_network_active_connected",
                        help = "Whether the active network is connected (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_network_active_connected",
                                value = if (activeInfo.isConnected) 1.0 else 0.0
                            )
                        )
                    )
                )

                families.add(
                    MetricFamily(
                        name = "android_network_active_roaming",
                        help = "Whether the active network is roaming (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_network_active_roaming",
                                value = if (activeInfo.isRoaming) 1.0 else 0.0
                            )
                        )
                    )
                )

                val typeName = activeInfo.typeName ?: "unknown"
                val subtypeName = activeInfo.subtypeName ?: ""
                val extraInfo = activeInfo.extraInfo ?: ""

                families.add(
                    MetricFamily(
                        name = "android_network_active_info",
                        help = "Active network type information",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_network_active_info",
                                labels = mapOf(
                                    "type_name" to typeName,
                                    "subtype_name" to subtypeName,
                                    "extra" to extraInfo
                                ),
                                value = 1.0
                            )
                        )
                    )
                )
            }

            // All networks via getAllNetworks() (API 21+)
            val networks = cm.allNetworks
            families.add(
                MetricFamily(
                    name = "android_network_count",
                    help = "Total number of available networks",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_network_count",
                            value = networks.size.toDouble()
                        )
                    )
                )
            )

            val upSamples = mutableListOf<MetricSample>()
            val dnsSamples = mutableListOf<MetricSample>()
            val routesSamples = mutableListOf<MetricSample>()
            val linkAddrSamples = mutableListOf<MetricSample>()
            val proxySamples = mutableListOf<MetricSample>()

            for (network in networks) {
                val linkProps: LinkProperties? = cm.getLinkProperties(network)
                val caps: NetworkCapabilities? = cm.getNetworkCapabilities(network)

                val iface = linkProps?.interfaceName ?: "unknown"

                // Network is up if it has capabilities and NOT_SUSPENDED
                val isUp = caps != null
                upSamples.add(
                    MetricSample(
                        name = "android_network_up",
                        labels = mapOf("iface" to iface),
                        value = if (isUp) 1.0 else 0.0
                    )
                )

                if (linkProps != null) {
                    dnsSamples.add(
                        MetricSample(
                            name = "android_network_dns_servers_count",
                            labels = mapOf("iface" to iface),
                            value = linkProps.dnsServers.size.toDouble()
                        )
                    )

                    routesSamples.add(
                        MetricSample(
                            name = "android_network_routes_count",
                            labels = mapOf("iface" to iface),
                            value = linkProps.routes.size.toDouble()
                        )
                    )

                    linkAddrSamples.add(
                        MetricSample(
                            name = "android_network_link_addresses_count",
                            labels = mapOf("iface" to iface),
                            value = linkProps.linkAddresses.size.toDouble()
                        )
                    )

                    proxySamples.add(
                        MetricSample(
                            name = "android_network_proxy_configured",
                            labels = mapOf("iface" to iface),
                            value = if (linkProps.httpProxy != null) 1.0 else 0.0
                        )
                    )
                }
            }

            if (upSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_network_up",
                        help = "Whether the network interface is up (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = upSamples
                    )
                )
            }

            if (dnsSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_network_dns_servers_count",
                        help = "Number of DNS servers configured for the network interface",
                        type = MetricType.GAUGE,
                        samples = dnsSamples
                    )
                )
            }

            if (routesSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_network_routes_count",
                        help = "Number of routes configured for the network interface",
                        type = MetricType.GAUGE,
                        samples = routesSamples
                    )
                )
            }

            if (linkAddrSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_network_link_addresses_count",
                        help = "Number of link addresses configured for the network interface",
                        type = MetricType.GAUGE,
                        samples = linkAddrSamples
                    )
                )
            }

            if (proxySamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_network_proxy_configured",
                        help = "Whether an HTTP proxy is configured for the network interface (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = proxySamples
                    )
                )
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }
}
