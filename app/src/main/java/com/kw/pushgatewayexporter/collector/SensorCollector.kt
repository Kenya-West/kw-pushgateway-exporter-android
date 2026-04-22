package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects sensor information metrics from SensorManager.
 */
class SensorCollector(private val context: Context) : Collector {

    companion object {
        private const val MAX_SENSORS = 100
    }

    override val name: String = "sensor"

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return emptyList()

            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            if (sensors.isNullOrEmpty()) return emptyList()

            val limitedSensors = sensors.take(MAX_SENSORS)

            val infoSamples = mutableListOf<MetricSample>()
            val powerSamples = mutableListOf<MetricSample>()
            val resolutionSamples = mutableListOf<MetricSample>()
            val maxRangeSamples = mutableListOf<MetricSample>()
            val minDelaySamples = mutableListOf<MetricSample>()
            val fifoReservedSamples = mutableListOf<MetricSample>()
            val fifoMaxSamples = mutableListOf<MetricSample>()

            for (sensor in limitedSensors) {
                val sensorName = sensor.name ?: "unknown"
                val vendor = sensor.vendor ?: "unknown"
                val type = sensor.type.toString()
                val typeName = try {
                    sensor.stringType ?: type
                } catch (_: Exception) {
                    type
                }
                val version = sensor.version.toString()

                // Determine wake_up label (API 21+)
                val isWakeUp = try {
                    sensor.isWakeUpSensor
                } catch (_: Exception) {
                    false
                }

                val baseLabels = mapOf(
                    "name" to sensorName,
                    "type" to type
                )

                val infoLabels = baseLabels + mapOf(
                    "vendor" to vendor,
                    "type_name" to typeName,
                    "version" to version,
                    "wake_up" to isWakeUp.toString()
                )

                infoSamples.add(
                    MetricSample(
                        name = "android_sensor_info",
                        labels = infoLabels,
                        value = 1.0
                    )
                )

                powerSamples.add(
                    MetricSample(
                        name = "android_sensor_power_milliamps",
                        labels = baseLabels,
                        value = sensor.power.toDouble()
                    )
                )

                resolutionSamples.add(
                    MetricSample(
                        name = "android_sensor_resolution",
                        labels = baseLabels,
                        value = sensor.resolution.toDouble()
                    )
                )

                maxRangeSamples.add(
                    MetricSample(
                        name = "android_sensor_max_range",
                        labels = baseLabels,
                        value = sensor.maximumRange.toDouble()
                    )
                )

                minDelaySamples.add(
                    MetricSample(
                        name = "android_sensor_min_delay_microseconds",
                        labels = baseLabels,
                        value = sensor.minDelay.toDouble()
                    )
                )

                // FIFO metrics (only if > 0)
                val fifoReserved = sensor.fifoReservedEventCount
                if (fifoReserved > 0) {
                    fifoReservedSamples.add(
                        MetricSample(
                            name = "android_sensor_fifo_reserved_event_count",
                            labels = baseLabels,
                            value = fifoReserved.toDouble()
                        )
                    )
                }

                val fifoMax = sensor.fifoMaxEventCount
                if (fifoMax > 0) {
                    fifoMaxSamples.add(
                        MetricSample(
                            name = "android_sensor_fifo_max_event_count",
                            labels = baseLabels,
                            value = fifoMax.toDouble()
                        )
                    )
                }
            }

            if (infoSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_info",
                        help = "Sensor information including name, vendor, type, and version",
                        type = MetricType.GAUGE,
                        samples = infoSamples
                    )
                )
            }

            if (powerSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_power_milliamps",
                        help = "Sensor power consumption in milliamps",
                        type = MetricType.GAUGE,
                        samples = powerSamples
                    )
                )
            }

            if (resolutionSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_resolution",
                        help = "Sensor resolution in the sensor's unit of measurement",
                        type = MetricType.GAUGE,
                        samples = resolutionSamples
                    )
                )
            }

            if (maxRangeSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_max_range",
                        help = "Sensor maximum range in the sensor's unit of measurement",
                        type = MetricType.GAUGE,
                        samples = maxRangeSamples
                    )
                )
            }

            if (minDelaySamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_min_delay_microseconds",
                        help = "Sensor minimum delay between events in microseconds",
                        type = MetricType.GAUGE,
                        samples = minDelaySamples
                    )
                )
            }

            if (fifoReservedSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_fifo_reserved_event_count",
                        help = "Number of events reserved for this sensor in the FIFO",
                        type = MetricType.GAUGE,
                        samples = fifoReservedSamples
                    )
                )
            }

            if (fifoMaxSamples.isNotEmpty()) {
                families.add(
                    MetricFamily(
                        name = "android_sensor_fifo_max_event_count",
                        help = "Maximum number of events this sensor can store in the FIFO",
                        type = MetricType.GAUGE,
                        samples = fifoMaxSamples
                    )
                )
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }
}
