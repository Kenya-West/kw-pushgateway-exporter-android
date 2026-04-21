package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects battery metrics from the sticky BATTERY_CHANGED broadcast
 * and the BatteryManager system service.
 */
class BatteryCollector(private val context: Context) : Collector {

    override val name: String = "battery"

    companion object {
        private val STATUS_MAP = mapOf(
            BatteryManager.BATTERY_STATUS_UNKNOWN to "unknown",
            BatteryManager.BATTERY_STATUS_CHARGING to "charging",
            BatteryManager.BATTERY_STATUS_DISCHARGING to "discharging",
            BatteryManager.BATTERY_STATUS_NOT_CHARGING to "not_charging",
            BatteryManager.BATTERY_STATUS_FULL to "full"
        )

        private val HEALTH_MAP = mapOf(
            BatteryManager.BATTERY_HEALTH_UNKNOWN to "unknown",
            BatteryManager.BATTERY_HEALTH_GOOD to "good",
            BatteryManager.BATTERY_HEALTH_OVERHEAT to "overheat",
            BatteryManager.BATTERY_HEALTH_DEAD to "dead",
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE to "over_voltage",
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE to "unspecified_failure",
            BatteryManager.BATTERY_HEALTH_COLD to "cold"
        )
    }

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()

            // Sticky broadcast for battery info
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (intent != null) {
                collectFromIntent(intent, families)
            }

            // BatteryManager service for advanced properties
            collectFromBatteryManager(families)

            families
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectFromIntent(intent: Intent, families: MutableList<MetricFamily>) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val present = intent.extras?.getBoolean(BatteryManager.EXTRA_PRESENT, false) ?: false
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        // Battery level ratio and percent
        if (level >= 0 && scale > 0) {
            val levelRatio = level.toDouble() / scale.toDouble()
            families.add(
                MetricFamily(
                    name = "android_battery_level_ratio",
                    help = "Battery charge level as a ratio (0.0-1.0)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_level_ratio", value = levelRatio)
                    )
                )
            )
            families.add(
                MetricFamily(
                    name = "android_battery_level_percent",
                    help = "Battery charge level as a percentage (0-100)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_level_percent", value = levelRatio * 100.0)
                    )
                )
            )
            families.add(
                MetricFamily(
                    name = "android_battery_scale",
                    help = "Battery scale factor from the battery manager",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_scale", value = scale.toDouble())
                    )
                )
            )
        }

        // Status
        if (status >= 0) {
            families.add(
                MetricFamily(
                    name = "android_battery_status",
                    help = "Battery status as a numeric code",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_status", value = status.toDouble())
                    )
                )
            )
            val statusName = STATUS_MAP[status] ?: "unknown"
            families.add(
                MetricFamily(
                    name = "android_battery_status_info",
                    help = "Battery status as a human-readable label",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_battery_status_info",
                            labels = mapOf("status" to statusName),
                            value = 1.0
                        )
                    )
                )
            )
        }

        // Health
        if (health >= 0) {
            families.add(
                MetricFamily(
                    name = "android_battery_health",
                    help = "Battery health as a numeric code",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_health", value = health.toDouble())
                    )
                )
            )
            val healthName = HEALTH_MAP[health] ?: "unknown"
            families.add(
                MetricFamily(
                    name = "android_battery_health_info",
                    help = "Battery health as a human-readable label",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_battery_health_info",
                            labels = mapOf("health" to healthName),
                            value = 1.0
                        )
                    )
                )
            )
        }

        // Plugged
        if (plugged >= 0) {
            families.add(
                MetricFamily(
                    name = "android_battery_plugged",
                    help = "Battery plugged status (0=none, 1=AC, 2=USB, 4=wireless)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_plugged", value = plugged.toDouble())
                    )
                )
            )
        }

        // Present
        families.add(
            MetricFamily(
                name = "android_battery_present",
                help = "Whether a battery is present (1=yes, 0=no)",
                type = MetricType.GAUGE,
                samples = listOf(
                    MetricSample(name = "android_battery_present", value = if (present) 1.0 else 0.0)
                )
            )
        )

        // Temperature
        if (temperature >= 0) {
            families.add(
                MetricFamily(
                    name = "android_battery_temperature_celsius",
                    help = "Battery temperature in degrees Celsius",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_temperature_celsius", value = temperature / 10.0)
                    )
                )
            )
        }

        // Voltage
        if (voltage >= 0) {
            families.add(
                MetricFamily(
                    name = "android_battery_voltage_millivolts",
                    help = "Battery voltage in millivolts",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_battery_voltage_millivolts", value = voltage.toDouble())
                    )
                )
            )
        }

        // Technology
        families.add(
            MetricFamily(
                name = "android_battery_technology_info",
                help = "Battery technology type",
                type = MetricType.GAUGE,
                samples = listOf(
                    MetricSample(
                        name = "android_battery_technology_info",
                        labels = mapOf("technology" to technology),
                        value = 1.0
                    )
                )
            )
        )
    }

    private fun collectFromBatteryManager(families: MutableList<MetricFamily>) {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return

            // Charge counter (microampere-hours)
            val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (chargeCounter != Int.MIN_VALUE) {
                families.add(
                    MetricFamily(
                        name = "android_battery_charge_counter_microampere_hours",
                        help = "Battery charge counter in microampere-hours",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_battery_charge_counter_microampere_hours",
                                value = chargeCounter.toDouble()
                            )
                        )
                    )
                )
            }

            // Current now (microamperes)
            val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (currentNow != Int.MIN_VALUE) {
                families.add(
                    MetricFamily(
                        name = "android_battery_current_now_microamperes",
                        help = "Instantaneous battery current in microamperes",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_battery_current_now_microamperes",
                                value = currentNow.toDouble()
                            )
                        )
                    )
                )
            }

            // Current average (microamperes)
            val currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (currentAvg != Int.MIN_VALUE) {
                families.add(
                    MetricFamily(
                        name = "android_battery_current_average_microamperes",
                        help = "Average battery current in microamperes",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_battery_current_average_microamperes",
                                value = currentAvg.toDouble()
                            )
                        )
                    )
                )
            }

            // Energy counter (nano-watt-hours)
            val energyCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            if (energyCounter != Long.MIN_VALUE) {
                families.add(
                    MetricFamily(
                        name = "android_battery_energy_nano_watt_hours",
                        help = "Battery energy counter in nano-watt-hours",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_battery_energy_nano_watt_hours",
                                value = energyCounter.toDouble()
                            )
                        )
                    )
                )
            }

            // Capacity percent
            val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity != Int.MIN_VALUE) {
                families.add(
                    MetricFamily(
                        name = "android_battery_capacity_percent",
                        help = "Battery remaining capacity as a percentage",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_battery_capacity_percent",
                                value = capacity.toDouble()
                            )
                        )
                    )
                )
            }
        } catch (_: Exception) {
            // BatteryManager properties may not be available
        }
    }
}
