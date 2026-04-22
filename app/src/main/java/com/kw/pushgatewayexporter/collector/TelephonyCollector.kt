package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects telephony metrics from TelephonyManager.
 * Only uses non-privileged APIs that do not require runtime permissions.
 */
class TelephonyCollector(private val context: Context) : Collector {

    override val name: String = "telephony"

    @Suppress("DEPRECATION")
    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()

            // Check if telephony feature exists
            val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

            if (!hasTelephony) {
                families.add(
                    MetricFamily(
                        name = "android_telephony_present",
                        help = "Whether the device has telephony hardware (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(name = "android_telephony_present", value = 0.0)
                        )
                    )
                )
                return families
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null) {
                families.add(
                    MetricFamily(
                        name = "android_telephony_present",
                        help = "Whether the device has telephony hardware (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(name = "android_telephony_present", value = 0.0)
                        )
                    )
                )
                return families
            }

            families.add(
                MetricFamily(
                    name = "android_telephony_present",
                    help = "Whether the device has telephony hardware (1=yes, 0=no)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_telephony_present", value = 1.0)
                    )
                )
            )

            // Phone type
            families.add(
                MetricFamily(
                    name = "android_telephony_phone_type",
                    help = "Phone type (0=none, 1=GSM, 2=CDMA, 3=SIP)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_telephony_phone_type", value = tm.phoneType.toDouble())
                    )
                )
            )

            // Network type (may require READ_PHONE_STATE on some API levels)
            try {
                val networkType = tm.networkType
                families.add(
                    MetricFamily(
                        name = "android_telephony_network_type",
                        help = "Current network type as a numeric code",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(name = "android_telephony_network_type", value = networkType.toDouble())
                        )
                    )
                )
            } catch (_: SecurityException) {
                // READ_PHONE_STATE not granted; omit this metric
            }

            // Data state
            families.add(
                MetricFamily(
                    name = "android_telephony_data_state",
                    help = "Current data connection state (0=disconnected, 1=connecting, 2=connected, 3=suspended)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_telephony_data_state", value = tm.dataState.toDouble())
                    )
                )
            )

            // Call state
            families.add(
                MetricFamily(
                    name = "android_telephony_call_state",
                    help = "Current call state (0=idle, 1=ringing, 2=offhook)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_telephony_call_state", value = tm.callState.toDouble())
                    )
                )
            )

            // Operator info (non-privileged on API 21)
            val operatorName = tm.networkOperatorName ?: "unknown"
            val operatorNumeric = tm.networkOperator ?: "unknown"
            val countryIso = tm.networkCountryIso ?: "unknown"
            val simCountryIso = tm.simCountryIso ?: "unknown"

            families.add(
                MetricFamily(
                    name = "android_telephony_operator_info",
                    help = "Network operator information",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_telephony_operator_info",
                            labels = mapOf(
                                "operator_name" to operatorName,
                                "operator_numeric" to operatorNumeric,
                                "country_iso" to countryIso,
                                "sim_country_iso" to simCountryIso
                            ),
                            value = 1.0
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
