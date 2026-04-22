package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.PushgatewayExporterApp

/**
 * On device boot (or on app cold-start after process death), brings the reliability state
 * back to what the user expected:
 *  - re-registers the periodic JobScheduler job if scheduling was enabled,
 *  - restarts the foreground service if foreground mode was enabled,
 *  - logs a "boot recovered" event.
 */
class BootRecoveryCoordinator(private val app: PushgatewayExporterApp) {

    fun runAfterBoot() {
        val log = app.reliabilityLog
        val prefs = app.reliabilityPreferences
        val config = app.configRepository.getConfig()

        log.i(TAG, "Running boot recovery")

        if (config.schedulingEnabled && config.persistAcrossReboot) {
            try {
                app.schedulerManager.schedulePeriodicJob(config)
                log.i(TAG, "Periodic job re-registered after boot")
            } catch (t: Throwable) {
                log.e(TAG, "Failed to re-register periodic job after boot", t)
            }
        }

        if (prefs.isForegroundModeEnabled()) {
            try {
                app.foregroundServiceController.start()
                log.i(TAG, "Foreground service restarted after boot")
            } catch (t: Throwable) {
                log.e(TAG, "Failed to restart foreground service after boot", t)
            }
        }
    }

    /**
     * Called on every app open. Detects inconsistencies (e.g. user expected scheduling but
     * process was killed and never recovered) and reports them without repairing — the UI
     * offers a one-tap repair via [ReliabilityManager.runSelfHealing].
     */
    fun detectInconsistencies(): List<String> {
        val issues = mutableListOf<String>()
        val prefs = app.reliabilityPreferences
        val config = app.configRepository.getConfig()

        if (config.schedulingEnabled && !app.schedulerManager.isJobScheduled()) {
            issues += "Scheduling is enabled but no periodic job is registered."
        }
        if (prefs.isForegroundModeEnabled() && !app.foregroundServiceController.isRunning()) {
            issues += "Foreground mode is enabled but the service is not running."
        }
        val lastPush = prefs.lastPushTimestamp()
        if (config.schedulingEnabled && lastPush > 0) {
            val gap = System.currentTimeMillis() - lastPush
            val intervalMs = config.pushIntervalMinutes.toLong() * 60_000L
            if (gap > intervalMs * LONG_GAP_FACTOR) {
                issues += "Last successful push was more than ${gap / 60_000L} minutes ago."
            }
        }
        return issues
    }

    companion object {
        private const val TAG = "BootRecovery"
        private const val LONG_GAP_FACTOR = 4L
    }
}
