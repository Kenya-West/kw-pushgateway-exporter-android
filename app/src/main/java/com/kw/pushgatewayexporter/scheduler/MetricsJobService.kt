package com.kw.pushgatewayexporter.scheduler

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.collector.CollectorRegistry
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType
import com.kw.pushgatewayexporter.model.PushResult
import com.kw.pushgatewayexporter.serializer.PrometheusSerializer
import com.kw.pushgatewayexporter.transport.PushgatewayClient

/**
 * JobService that collects metrics and pushes them to Pushgateway.
 * Uses JobScheduler (API 21+) for periodic background execution.
 *
 * Prevents overlapping pushes using a simple volatile flag.
 */
class MetricsJobService : JobService() {

    companion object {
        private const val TAG = "MetricsJobService"

        @Volatile
        private var isRunning = false
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        if (isRunning) {
            Log.w(TAG, "Previous push still running, skipping this cycle")
            return false // Already running, don't overlap
        }

        val app = application as? PushgatewayExporterApp ?: return false
        val config = app.configRepository.getConfig()

        if (!config.isConfigured) {
            Log.w(TAG, "Pushgateway URL not configured, skipping push")
            return false
        }

        isRunning = true

        // Run collection + push on a background thread
        Thread {
            try {
                executePush(app)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during push", e)
            } finally {
                isRunning = false
                jobFinished(params, false) // false = don't reschedule, JobScheduler handles periodic
            }
        }.start()

        return true // Work is happening asynchronously
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Job was stopped before completion (e.g. constraints no longer met)
        Log.w(TAG, "Job stopped by system")
        isRunning = false
        return true // true = reschedule
    }

    private fun executePush(app: PushgatewayExporterApp) {
        val config = app.configRepository.getConfig()
        val instanceId = app.instanceIdentity.installationId

        val collectStart = System.currentTimeMillis()

        // Collect all metrics
        val registry = CollectorRegistry(this, config)
        val (families, collectorErrors) = registry.collectAll()

        val collectDuration = (System.currentTimeMillis() - collectStart) / 1000.0

        // Add exporter operational metrics
        val operationalFamilies = buildOperationalMetrics(
            app, collectDuration, collectorErrors, families.size
        )
        val allFamilies = families + operationalFamilies

        // Serialize to Prometheus text format
        val payload = PrometheusSerializer.serialize(allFamilies)

        if (config.dryRunMode) {
            if (config.logLevel >= 3) {
                Log.i(TAG, "DRY RUN - would push ${payload.length} bytes, ${allFamilies.size} families")
            }
            val result = PushResult(
                success = true,
                httpStatusCode = 0,
                errorMessage = "Dry run mode - not actually pushed",
                durationMillis = System.currentTimeMillis() - collectStart,
                payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
                metricsCount = allFamilies.sumOf { it.samples.size }
            )
            app.configRepository.saveLastPushResult(result)
            return
        }

        // Push to Pushgateway
        val client = PushgatewayClient(config)
        val result = client.push(payload, instanceId)
        app.configRepository.saveLastPushResult(result)
        // Heartbeat for reliability self-tests and reminder heuristics.
        if (result.success) app.reliabilityPreferences.markPushTimestamp()

        if (config.logLevel >= 3) {
            if (result.success) {
                Log.i(TAG, "Push successful: ${result.httpStatusCode}, " +
                    "${result.payloadSizeBytes} bytes, ${result.durationMillis}ms")
            } else {
                Log.w(TAG, "Push failed: ${result.httpStatusCode} - ${result.errorMessage}")
            }
        }

        // Cleanup stale previous identity if exists
        val previousId = app.instanceIdentity.getPreviousId()
        if (previousId != null && previousId != instanceId) {
            try {
                val deleteResult = client.delete(previousId)
                if (deleteResult.success && config.logLevel >= 3) {
                    Log.i(TAG, "Cleaned up stale group for previous instance: $previousId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up stale group: ${e.message}")
            }
        }
    }

    private fun buildOperationalMetrics(
        app: PushgatewayExporterApp,
        collectDuration: Double,
        errors: List<String>,
        familyCount: Int
    ): List<MetricFamily> {
        val families = mutableListOf<MetricFamily>()
        val config = app.configRepository.getConfig()

        families.add(MetricFamily(
            name = "android_exporter_collect_duration_seconds",
            help = "Time spent collecting all metrics in seconds",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample("android_exporter_collect_duration_seconds", value = collectDuration))
        ))

        families.add(MetricFamily(
            name = "android_exporter_last_collect_success_unixtime",
            help = "Unix timestamp of last successful metric collection",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample(
                "android_exporter_last_collect_success_unixtime",
                value = System.currentTimeMillis() / 1000.0
            ))
        ))

        families.add(MetricFamily(
            name = "android_exporter_metrics_count",
            help = "Number of metric families in this export",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample("android_exporter_metrics_count", value = familyCount.toDouble()))
        ))

        families.add(MetricFamily(
            name = "android_exporter_collector_errors_total",
            help = "Number of collectors that failed during this export",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample("android_exporter_collector_errors_total", value = errors.size.toDouble()))
        ))

        val lastSuccessTime = app.configRepository.getLastSuccessTime()
        if (lastSuccessTime > 0) {
            families.add(MetricFamily(
                name = "android_exporter_last_push_success_unixtime",
                help = "Unix timestamp of last successful push to Pushgateway",
                type = MetricType.GAUGE,
                samples = listOf(MetricSample(
                    "android_exporter_last_push_success_unixtime",
                    value = lastSuccessTime / 1000.0
                ))
            ))
        }

        val lastFailureTime = app.configRepository.getLastFailureTime()
        if (lastFailureTime > 0) {
            families.add(MetricFamily(
                name = "android_exporter_last_push_failure_unixtime",
                help = "Unix timestamp of last failed push to Pushgateway",
                type = MetricType.GAUGE,
                samples = listOf(MetricSample(
                    "android_exporter_last_push_failure_unixtime",
                    value = lastFailureTime / 1000.0
                ))
            ))
        }

        families.add(MetricFamily(
            name = "android_exporter_push_attempts_total",
            help = "Total number of push attempts since app install",
            type = MetricType.COUNTER,
            samples = listOf(MetricSample(
                "android_exporter_push_attempts_total",
                value = app.configRepository.getPushAttemptsTotal().toDouble()
            ))
        ))

        families.add(MetricFamily(
            name = "android_exporter_push_failures_total",
            help = "Total number of failed push attempts since app install",
            type = MetricType.COUNTER,
            samples = listOf(MetricSample(
                "android_exporter_push_failures_total",
                value = app.configRepository.getPushFailuresTotal().toDouble()
            ))
        ))

        families.add(MetricFamily(
            name = "android_exporter_job_scheduled",
            help = "Whether the periodic push job is currently scheduled (0 or 1)",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample(
                "android_exporter_job_scheduled",
                value = if (config.schedulingEnabled) 1.0 else 0.0
            ))
        ))

        return families
    }
}
