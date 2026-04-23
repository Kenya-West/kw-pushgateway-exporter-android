package com.kw.pushgatewayexporter.reliability

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.kw.pushgatewayexporter.MainActivity
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.R
import com.kw.pushgatewayexporter.collector.CollectorRegistry
import com.kw.pushgatewayexporter.model.PushResult
import com.kw.pushgatewayexporter.serializer.PrometheusSerializer
import com.kw.pushgatewayexporter.transport.PushgatewayClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent foreground service that runs the exporter push loop on its own thread.
 *
 * This is the strongest background-continuity option available to a non-privileged app:
 *  - On stock AOSP / Android One / Samsung / Pixel, a foreground service with a visible
 *    notification survives screen-off and memory pressure indefinitely.
 *  - On aggressive OEM firmware it is still the most reliable mechanism, but the user must
 *    also complete the OEM-specific checklist steps.
 *
 * Service lifecycle:
 *  - Starts itself `sticky`, restarts if killed.
 *  - Uses a single worker thread with a `@Volatile` stop flag.
 *  - Does NOT duplicate JobScheduler work — when foreground mode is enabled, we cancel the
 *    periodic job in [ForegroundServiceController.start].
 */
class ExporterForegroundService : Service() {

    @Volatile private var stopRequested = false
    private var workerThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification(initialStatus = "Starting exporter loop…")

        if (workerThread?.isAlive == true) {
            // Already running; just refresh notification.
            return START_STICKY
        }
        stopRequested = false
        workerThread = Thread({ runLoop() }, "ExporterForegroundLoop").also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        workerThread?.interrupt()
        workerThread = null
        super.onDestroy()
    }

    private fun runLoop() {
        val app = application as? PushgatewayExporterApp ?: return
        val log = app.reliabilityLog
        log.i(TAG, "Foreground push loop started")
        while (!stopRequested && !Thread.currentThread().isInterrupted) {
            val config = app.configRepository.getConfig()
            val intervalMs = config.pushIntervalMinutes.coerceAtLeast(1).toLong() * 60L * 1000L
            val sleepMs = try {
                runOneCycle(app)
                intervalMs
            } catch (t: Throwable) {
                log.e(TAG, "Cycle failed", t)
                // Short backoff on error, capped by normal interval.
                minOf(intervalMs, 30_000L)
            }
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                break
            }
        }
        log.i(TAG, "Foreground push loop exiting")
    }

    private fun runOneCycle(app: PushgatewayExporterApp) {
        val config = app.configRepository.getConfig()
        if (!config.isConfigured) {
            updateNotification("Exporter not configured — waiting")
            return
        }
        val instanceId = app.instanceIdentity.installationId
        val registry = CollectorRegistry(this, config)
        val (families, _) = registry.collectAll()
        val payload = PrometheusSerializer.serialize(families)
        val result: PushResult = if (config.dryRunMode) {
            PushResult(
                success = true,
                httpStatusCode = 0,
                errorMessage = "Dry run",
                payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
                metricsCount = families.sumOf { it.samples.size }
            )
        } else {
            PushgatewayClient(config).push(payload, instanceId)
        }
        app.configRepository.saveLastPushResult(result)
        app.reliabilityPreferences.markPushTimestamp()
        updateNotification(
            if (result.success) "Last push: success at ${nowFmt()}"
            else "Last push failed: ${result.errorMessage ?: "unknown"}"
        )
    }

    private fun startForegroundWithNotification(initialStatus: String) {
        ensureChannel(this)
        val notification = buildNotification(this, initialStatus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // dataSync type matches the exporter's job (periodic external sync).
            // Required on API 34+ when the manifest declares a foregroundServiceType.
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(this, status))
    }

    private fun nowFmt(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    companion object {
        private const val TAG = "ExporterFgService"
        const val CHANNEL_ID = "exporter_foreground"
        const val NOTIFICATION_ID = 4711

        internal fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Exporter reliability mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while the exporter runs in foreground mode to keep pushing metrics continuously."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        internal fun buildNotification(context: Context, status: String): Notification {
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = pendingIntent(context, contentIntent)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            return builder
                .setContentTitle("Pushgateway Exporter active")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_exporter_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build()
        }

        private fun pendingIntent(context: Context, intent: Intent): PendingIntent {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getActivity(context, 0, intent, flags)
        }
    }
}
