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

            val totalSamples = mutableListOf<MetricSample>()
            val availableSamples = mutableListOf<MetricSample>()
            val freeSamples = mutableListOf<MetricSample>()
            val blockSizeSamples = mutableListOf<MetricSample>()

            // Internal data storage
            collectStorageMetrics(Environment.getDataDirectory(), "internal_data",
                totalSamples, availableSamples, freeSamples, blockSizeSamples)

            // Internal cache
            collectStorageMetrics(context.cacheDir, "internal_cache",
                totalSamples, availableSamples, freeSamples, blockSizeSamples)

            // External storage (only if mounted)
            try {
                val externalState = Environment.getExternalStorageState()
                if (externalState == Environment.MEDIA_MOUNTED ||
                    externalState == Environment.MEDIA_MOUNTED_READ_ONLY
                ) {
                    collectStorageMetrics(Environment.getExternalStorageDirectory(), "external_storage",
                        totalSamples, availableSamples, freeSamples, blockSizeSamples)
                }
            } catch (_: Exception) {
                // External storage may not be available
            }

            if (totalSamples.isNotEmpty()) {
                families.add(MetricFamily("android_storage_total_bytes",
                    "Total storage capacity in bytes", MetricType.GAUGE, totalSamples))
            }
            if (availableSamples.isNotEmpty()) {
                families.add(MetricFamily("android_storage_available_bytes",
                    "Available storage in bytes", MetricType.GAUGE, availableSamples))
            }
            if (freeSamples.isNotEmpty()) {
                families.add(MetricFamily("android_storage_free_bytes",
                    "Free storage in bytes (including reserved blocks)", MetricType.GAUGE, freeSamples))
            }
            if (blockSizeSamples.isNotEmpty()) {
                families.add(MetricFamily("android_storage_block_size_bytes",
                    "File system block size in bytes", MetricType.GAUGE, blockSizeSamples))
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
        totalSamples: MutableList<MetricSample>,
        availableSamples: MutableList<MetricSample>,
        freeSamples: MutableList<MetricSample>,
        blockSizeSamples: MutableList<MetricSample>
    ) {
        if (path == null || !path.exists()) return

        try {
            val statFs = StatFs(path.absolutePath)

            val blockSize = statFs.blockSizeLong
            val blockCount = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong
            val freeBlocks = statFs.freeBlocksLong

            val labels = mapOf("storage_scope" to scope)

            totalSamples.add(MetricSample("android_storage_total_bytes", labels,
                (blockSize * blockCount).toDouble()))
            availableSamples.add(MetricSample("android_storage_available_bytes", labels,
                (blockSize * availableBlocks).toDouble()))
            freeSamples.add(MetricSample("android_storage_free_bytes", labels,
                (blockSize * freeBlocks).toDouble()))
            blockSizeSamples.add(MetricSample("android_storage_block_size_bytes", labels,
                blockSize.toDouble()))
        } catch (_: Exception) {
            // StatFs may fail for some paths
        }
    }
}
