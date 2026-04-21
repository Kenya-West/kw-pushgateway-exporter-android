package com.kw.pushgatewayexporter.collector

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType
import java.io.File

/**
 * Collects storage metrics from StatFs for internal and external storage paths.
 */
class StorageCollector(private val context: Context) : Collector {

    override val name: String = "storage"

    override fun collect(): List<MetricFamily> {
        return try {
            val families = mutableListOf<MetricFamily>()

            // Internal data storage
            collectStorageMetrics(Environment.getDataDirectory(), "internal_data", families)

            // Internal cache
            collectStorageMetrics(context.cacheDir, "internal_cache", families)

            // External storage (only if mounted)
            try {
                val externalState = Environment.getExternalStorageState()
                if (externalState == Environment.MEDIA_MOUNTED ||
                    externalState == Environment.MEDIA_MOUNTED_READ_ONLY
                ) {
                    collectStorageMetrics(Environment.getExternalStorageDirectory(), "external_storage", families)
                }
            } catch (_: Exception) {
                // External storage may not be available
            }

            // External storage state info
            try {
                val externalState = Environment.getExternalStorageState()
                families.add(
                    MetricFamily(
                        name = "android_storage_external_state",
                        help = "Current state of external storage",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_storage_external_state",
                                labels = mapOf("state" to externalState),
                                value = 1.0
                            )
                        )
                    )
                )

                families.add(
                    MetricFamily(
                        name = "android_storage_external_emulated",
                        help = "Whether external storage is emulated (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_storage_external_emulated",
                                value = if (Environment.isExternalStorageEmulated()) 1.0 else 0.0
                            )
                        )
                    )
                )

                families.add(
                    MetricFamily(
                        name = "android_storage_external_removable",
                        help = "Whether external storage is removable (1=yes, 0=no)",
                        type = MetricType.GAUGE,
                        samples = listOf(
                            MetricSample(
                                name = "android_storage_external_removable",
                                value = if (Environment.isExternalStorageRemovable()) 1.0 else 0.0
                            )
                        )
                    )
                )
            } catch (_: Exception) {
                // External storage info may not be available
            }

            families
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectStorageMetrics(
        path: File?,
        scope: String,
        families: MutableList<MetricFamily>
    ) {
        if (path == null || !path.exists()) return

        try {
            val statFs = StatFs(path.absolutePath)

            val blockSize = statFs.blockSizeLong
            val blockCount = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong
            val freeBlocks = statFs.freeBlocksLong

            val totalBytes = blockSize * blockCount
            val availableBytes = blockSize * availableBlocks
            val freeBytes = blockSize * freeBlocks

            families.add(
                MetricFamily(
                    name = "android_storage_total_bytes",
                    help = "Total storage capacity in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_storage_total_bytes",
                            labels = mapOf("storage_scope" to scope),
                            value = totalBytes.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_storage_available_bytes",
                    help = "Available storage in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_storage_available_bytes",
                            labels = mapOf("storage_scope" to scope),
                            value = availableBytes.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_storage_free_bytes",
                    help = "Free storage in bytes (including reserved blocks)",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_storage_free_bytes",
                            labels = mapOf("storage_scope" to scope),
                            value = freeBytes.toDouble()
                        )
                    )
                )
            )

            families.add(
                MetricFamily(
                    name = "android_storage_block_size_bytes",
                    help = "File system block size in bytes",
                    type = MetricType.GAUGE,
                    samples = listOf(
                        MetricSample(
                            name = "android_storage_block_size_bytes",
                            labels = mapOf("storage_scope" to scope),
                            value = blockSize.toDouble()
                        )
                    )
                )
            )
        } catch (_: Exception) {
            // StatFs may fail for some paths
        }
    }
}
