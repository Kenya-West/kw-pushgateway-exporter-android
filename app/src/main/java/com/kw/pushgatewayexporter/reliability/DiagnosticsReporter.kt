package com.kw.pushgatewayexporter.reliability

import android.content.Context
import android.os.Build
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a plain-text diagnostics report suitable for the user to copy into a bug
 * report. Excludes credentials and sensitive network identifiers.
 */
class DiagnosticsReporter(private val context: Context) {

    fun build(): String {
        val app = context.applicationContext as? PushgatewayExporterApp
            ?: return "[diagnostics: app not initialized]"
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val now = System.currentTimeMillis()

        sb.appendLine("=== Pushgateway Exporter diagnostics ===")
        sb.appendLine("Generated: ${fmt.format(Date(now))}")
        sb.appendLine()

        // Device
        sb.appendLine("--- Device ---")
        sb.appendLine("API level: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Brand: ${Build.BRAND}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Hardware: ${Build.HARDWARE}")
        sb.appendLine("Detected OEM profile: ${app.reliabilityManager.oemProfile.displayName} " +
            "(id=${app.reliabilityManager.oemProfile.id})")
        sb.appendLine()

        // Exporter state
        val config = app.configRepository.getConfig()
        sb.appendLine("--- Exporter state ---")
        sb.appendLine("Configured: ${config.isConfigured}")
        sb.appendLine("Scheduling enabled: ${config.schedulingEnabled}")
        sb.appendLine("Push interval (min): ${config.pushIntervalMinutes}")
        sb.appendLine("Persist across reboot: ${config.persistAcrossReboot}")
        sb.appendLine("Require unmetered: ${config.requireUnmeteredNetwork}")
        sb.appendLine("Require charging: ${config.requireCharging}")
        sb.appendLine("Dry-run: ${config.dryRunMode}")
        sb.appendLine("Job scheduled: ${app.schedulerManager.isJobScheduled()}")
        sb.appendLine()

        // Reliability
        val prefs = app.reliabilityPreferences
        val battery = BatteryOptimizationHelper.status(context)
        val fgRunning = app.foregroundServiceController.isRunning()
        sb.appendLine("--- Reliability ---")
        sb.appendLine("Battery optimization status: $battery")
        sb.appendLine("Foreground mode requested: ${prefs.isForegroundModeEnabled()}")
        sb.appendLine("Foreground service running: $fgRunning")
        sb.appendLine("Reminders disabled: ${prefs.isRemindersDisabled()}")
        sb.appendLine("Last push timestamp: ${if (prefs.lastPushTimestamp() > 0) fmt.format(Date(prefs.lastPushTimestamp())) else "never"}")
        sb.appendLine("Push attempts total: ${app.configRepository.getPushAttemptsTotal()}")
        sb.appendLine("Push failures total: ${app.configRepository.getPushFailuresTotal()}")
        val lastTest = prefs.lastSelfTest()
        if (lastTest != null) {
            sb.appendLine("Last self-test: mode=${lastTest.mode}, outcome=${lastTest.outcome}, " +
                "observed=${lastTest.observedRuns}/${lastTest.expectedRuns}")
        }
        sb.appendLine()

        // Checklist
        sb.appendLine("--- Checklist evaluation ---")
        for (ev in app.reliabilityManager.evaluate()) {
            sb.appendLine("  [${ev.status}] ${ev.step.title} (${ev.step.severity}, verify=${ev.step.verification})")
            ev.detail?.let { sb.appendLine("    $it") }
        }
        sb.appendLine()

        // Log tail
        sb.appendLine("--- Reliability log (last ${LOG_TAIL_LINES} entries) ---")
        val entries = app.reliabilityLog.entries()
        val tail = if (entries.size > LOG_TAIL_LINES) entries.subList(entries.size - LOG_TAIL_LINES, entries.size)
            else entries
        for (e in tail) {
            sb.appendLine("${fmt.format(Date(e.timestampMillis))} [${e.level}] ${e.tag}: ${e.message}")
        }

        return sb.toString()
    }

    companion object {
        private const val LOG_TAIL_LINES = 100
    }
}
