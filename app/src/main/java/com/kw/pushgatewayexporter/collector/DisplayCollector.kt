package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType

/**
 * Collects display metrics from WindowManager and DisplayMetrics.
 */
class DisplayCollector(private val context: Context) : Collector {

    override val name: String = "display"

    @Suppress("DEPRECATION")
    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return emptyList()

            val display = wm.defaultDisplay ?: return emptyList()
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)

            families.add(
                MetricFamily(
                    name = "android_display_width_pixels",
                    help = "Display width in pixels",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_width_pixels", value = metrics.widthPixels.toDouble())
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_display_height_pixels",
                    help = "Display height in pixels",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_height_pixels", value = metrics.heightPixels.toDouble())
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_display_density_dpi",
                    help = "Display density in dots per inch",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_density_dpi", value = metrics.densityDpi.toDouble())
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_display_xdpi",
                    help = "Exact physical pixels per inch of the display in the X dimension",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_xdpi", value = metrics.xdpi.toDouble())
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_display_ydpi",
                    help = "Exact physical pixels per inch of the display in the Y dimension",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_ydpi", value = metrics.ydpi.toDouble())
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_display_rotation",
                    help = "Display rotation (0=0°, 1=90°, 2=180°, 3=270°)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(name = "android_display_rotation", value = display.rotation.toDouble())
                    )
                )
            )

            // Display name (available on API 17+)
            try {
                val displayName = display.name ?: "unknown"
                families.add(
                    MetricFamily(
                        name = "android_display_info",
                        help = "Display information",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_display_info",
                                labels = mapOf("name" to displayName),
                                value = 1.0
                            )
                        )
                    )
                )
            } catch (_: Exception) {
                // Display.getName() may not be available
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }
}
