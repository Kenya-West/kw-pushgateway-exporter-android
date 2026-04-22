package com.kw.pushgatewayexporter.reliability

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Starts / stops [ExporterForegroundService] safely and checks whether it is currently running.
 *
 * Notes:
 *  - [ActivityManager.getRunningServices] is deprecated for third-party inspection of other
 *    apps' services but is fully allowed and reliable for detecting your own service.
 *  - On API 26+ we use [Context.startForegroundService]; older APIs use the classic
 *    [Context.startService]. Either way the service must call `startForeground()` within the
 *    platform-mandated window — [ExporterForegroundService.onStartCommand] does so immediately.
 */
class ForegroundServiceController(
    private val context: Context,
    private val log: ReliabilityLog,
    private val prefs: ReliabilityPreferences
) {

    fun isRunning(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            @Suppress("DEPRECATION")
            am.getRunningServices(Int.MAX_VALUE).any { info ->
                info.service.className == ExporterForegroundService::class.java.name
            }
        } catch (t: Throwable) {
            log.w(TAG, "isRunning check failed: ${t.message}")
            false
        }
    }

    fun start(): Boolean {
        // Prepare channel before starting so the first startForeground() call is valid on O+.
        ExporterForegroundService.ensureChannel(context)
        val intent = Intent(context, ExporterForegroundService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            prefs.setForegroundModeEnabled(true)
            log.i(TAG, "Foreground service start requested")
            true
        } catch (t: Throwable) {
            log.e(TAG, "Failed to start foreground service", t)
            false
        }
    }

    fun stop(): Boolean {
        val intent = Intent(context, ExporterForegroundService::class.java)
        return try {
            context.stopService(intent)
            prefs.setForegroundModeEnabled(false)
            log.i(TAG, "Foreground service stop requested")
            true
        } catch (t: Throwable) {
            log.e(TAG, "Failed to stop foreground service", t)
            false
        }
    }

    companion object {
        private const val TAG = "FgServiceController"
    }
}
