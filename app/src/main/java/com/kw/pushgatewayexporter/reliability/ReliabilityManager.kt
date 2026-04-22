package com.kw.pushgatewayexporter.reliability

import android.content.Context
import android.os.Build
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.reliability.oem.OemProfile
import com.kw.pushgatewayexporter.reliability.oem.OemProfileResolver

/**
 * Top-level facade for the reliability subsystem. Holds references to the individual
 * components and exposes a single entry point the UI / background code can use.
 *
 * Construct once at application start (see [PushgatewayExporterApp]).
 */
class ReliabilityManager private constructor(
    val context: Context,
    val log: ReliabilityLog,
    val preferences: ReliabilityPreferences,
    val navigator: SettingsNavigator,
    val foregroundController: ForegroundServiceController,
    val oemProfile: OemProfile
) {

    /** Build the current checklist against live device state. */
    fun buildSteps(): List<ChecklistStep> =
        ReliabilityChecklist.buildSteps(Build.VERSION.SDK_INT, oemProfile.routes)

    /** Evaluate each step against current signals. */
    fun evaluate(): List<EvaluatedStep> {
        val steps = buildSteps()
        val app = context.applicationContext as? PushgatewayExporterApp
        val config = app?.configRepository?.getConfig()
        val batteryStatus = BatteryOptimizationHelper.status(context)
        val fgRunning = foregroundController.isRunning()
        val jobScheduled = app?.schedulerManager?.isJobScheduled() == true

        return steps.map { step ->
            when (step.id) {
                ReliabilityChecklist.ID_BATTERY_OPT -> {
                    val status = when (batteryStatus) {
                        BatteryOptimizationHelper.OptimizationStatus.Exempt -> StepStatus.DONE
                        BatteryOptimizationHelper.OptimizationStatus.NotExempt -> StepStatus.REQUIRED_NOT_DONE
                        BatteryOptimizationHelper.OptimizationStatus.NotApplicable -> StepStatus.NOT_APPLICABLE
                        BatteryOptimizationHelper.OptimizationStatus.Unknown -> StepStatus.UNKNOWN
                    }
                    EvaluatedStep(step, status, detail = "Status: $batteryStatus")
                }
                ReliabilityChecklist.ID_FOREGROUND -> {
                    val done = preferences.isForegroundModeEnabled() && fgRunning
                    EvaluatedStep(
                        step,
                        ReliabilityChecklist.classify(step, done, preferences.isForegroundModeEnabled()),
                        detail = if (fgRunning) "Service running" else "Service not running"
                    )
                }
                ReliabilityChecklist.ID_BOOT -> {
                    val done = config?.persistAcrossReboot == true && config.schedulingEnabled
                    EvaluatedStep(
                        step,
                        ReliabilityChecklist.classify(step, done, false),
                        detail = if (done) "Enabled" else "Disabled"
                    )
                }
                ReliabilityChecklist.ID_JOB -> {
                    val done = jobScheduled
                    EvaluatedStep(
                        step,
                        ReliabilityChecklist.classify(step, done, false),
                        detail = if (done) "Scheduled" else "Not scheduled"
                    )
                }
                ReliabilityChecklist.ID_SELF_TEST -> {
                    val last = preferences.lastSelfTest()
                    val done = last != null && last.outcome == "PASSED"
                    EvaluatedStep(
                        step,
                        ReliabilityChecklist.classify(step, done, false),
                        detail = last?.let { "Last: ${it.outcome} (${it.observedRuns}/${it.expectedRuns} runs)" }
                    )
                }
                else -> {
                    // OEM / user-confirmed step.
                    val confirmed = preferences.isStepConfirmed(step.id, oemProfile.id)
                    EvaluatedStep(
                        step,
                        ReliabilityChecklist.classify(step, null, confirmed),
                        detail = if (confirmed) "Marked as done" else "User confirmation required"
                    )
                }
            }
        }
    }

    /** True if at least one required step is not yet done — a signal for reminders. */
    fun hasOutstandingRequired(): Boolean =
        evaluate().any { it.status == StepStatus.REQUIRED_NOT_DONE }

    /**
     * Decide whether a reminder should be shown now. Applies the "not spammy" and
     * "disabled by user" rules.
     */
    fun shouldShowReminder(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (preferences.isRemindersDisabled()) return false
        if (!hasOutstandingRequired()) return false
        val last = preferences.lastReminderShownMillis()
        return (nowMillis - last) >= REMINDER_INTERVAL_MILLIS
    }

    /**
     * Run all self-healing checks that can be fixed automatically.
     * Returns a human-readable summary list suitable for display in the UI.
     */
    fun runSelfHealing(): List<String> {
        val summary = mutableListOf<String>()
        val app = context.applicationContext as? PushgatewayExporterApp ?: return summary
        val config = app.configRepository.getConfig()

        // If the user expected scheduling but the job is missing, re-register it.
        if (config.schedulingEnabled && !app.schedulerManager.isJobScheduled()) {
            app.schedulerManager.schedulePeriodicJob(config)
            log.w(TAG, "Self-heal: periodic job was missing, re-scheduled")
            summary += "Re-scheduled periodic push job."
        }
        // If the user enabled foreground mode but the service is dead, restart it.
        if (preferences.isForegroundModeEnabled() && !foregroundController.isRunning()) {
            foregroundController.start()
            log.w(TAG, "Self-heal: foreground service was not running, restarted")
            summary += "Restarted foreground service."
        }
        if (summary.isEmpty()) summary += "Everything looks consistent."
        return summary
    }

    companion object {
        private const val TAG = "ReliabilityManager"
        private const val REMINDER_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L // 24h

        fun create(context: Context): ReliabilityManager {
            val log = ReliabilityLog(context)
            val prefs = ReliabilityPreferences(context)
            val navigator = SettingsNavigator(context, log)
            val fg = ForegroundServiceController(context, log, prefs)
            val oem = OemProfileResolver.resolve()
            log.i(TAG, "Resolved OEM profile: ${oem.displayName} (id=${oem.id}), SDK=${Build.VERSION.SDK_INT}")
            return ReliabilityManager(context, log, prefs, navigator, fg, oem)
        }
    }
}
