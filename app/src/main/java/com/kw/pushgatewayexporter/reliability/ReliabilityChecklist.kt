package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.reliability.oem.CandidateIntent
import com.kw.pushgatewayexporter.reliability.oem.OemRoute

/**
 * Severity / required-ness of a checklist step.
 *  - [REQUIRED] — without this, continuous background execution is very unlikely to work.
 *  - [RECOMMENDED] — meaningfully improves reliability on this device.
 *  - [ADVANCED] — power users only; rarely needed.
 */
enum class StepSeverity { REQUIRED, RECOMMENDED, ADVANCED }

/**
 * How we can tell whether the step was actually performed.
 *  - [DIRECT] — the OS tells us (e.g. `isIgnoringBatteryOptimizations`).
 *  - [INDIRECT] — we infer from observable side effects (e.g. self-test ran to completion).
 *  - [USER_CONFIRMED] — no public read API; user checks a box.
 *  - [NOT_APPLICABLE] — step does not apply to this device / API level.
 */
enum class VerificationKind { DIRECT, INDIRECT, USER_CONFIRMED, NOT_APPLICABLE }

/** Current evaluation of a checklist step. */
enum class StepStatus {
    REQUIRED_NOT_DONE,
    RECOMMENDED_NOT_DONE,
    DONE,
    NOT_APPLICABLE,
    UNKNOWN
}

/**
 * Immutable description of one actionable step.
 */
data class ChecklistStep(
    val id: String,
    val title: String,
    val rationale: String,
    val severity: StepSeverity,
    val verification: VerificationKind,
    /** Candidate intents to try in order. Empty = "Open settings" not offered. */
    val candidates: List<CandidateIntent> = emptyList(),
    /** Always provided as a fallback — shown via "Show manual steps". */
    val manualSteps: List<String> = emptyList(),
    /** Whether the step has a "Test" action (exposes the self-test runner). */
    val hasSelfTest: Boolean = false
)

/** Snapshot of a step evaluated against current device state. */
data class EvaluatedStep(
    val step: ChecklistStep,
    val status: StepStatus,
    val detail: String? = null
)

/**
 * Builds the ordered list of steps for a given device + OEM profile.
 *
 * Kept as a pure helper so [ReliabilityManager] can be tested by injecting the output.
 */
object ReliabilityChecklist {

    const val ID_BATTERY_OPT = "battery_optimization"
    const val ID_FOREGROUND = "foreground_service"
    const val ID_BOOT = "boot_persistence"
    const val ID_JOB = "periodic_job"
    const val ID_SELF_TEST = "self_test"

    /**
     * Build the default + OEM-specific steps.
     *
     * @param sdkInt current `Build.VERSION.SDK_INT`, injectable for testing.
     * @param oemRoutes ordered OEM routes; each becomes one step.
     */
    fun buildSteps(sdkInt: Int, oemRoutes: List<OemRoute>): List<ChecklistStep> {
        val out = mutableListOf<ChecklistStep>()

        // Battery optimization (API 23+)
        if (sdkInt >= 23) {
            out += ChecklistStep(
                id = ID_BATTERY_OPT,
                title = "Exempt from battery optimization",
                rationale = "Android Doze defers background work. Exempting the exporter lets " +
                    "JobScheduler and the foreground service run on schedule.",
                severity = StepSeverity.REQUIRED,
                verification = VerificationKind.DIRECT,
                candidates = emptyList(),
                manualSteps = listOf(
                    "Settings → Apps → Pushgateway Exporter → Battery.",
                    "Choose \"Don't optimize\" or \"Unrestricted\"."
                )
            )
        } else {
            // On API 21–22 there is no battery optimization concept; show the step as
            // not applicable so the user understands nothing is needed there.
            out += ChecklistStep(
                id = ID_BATTERY_OPT,
                title = "Battery optimization (Android 6+)",
                rationale = "Not applicable on this Android version.",
                severity = StepSeverity.RECOMMENDED,
                verification = VerificationKind.NOT_APPLICABLE
            )
        }

        // Foreground service (user decision)
        out += ChecklistStep(
            id = ID_FOREGROUND,
            title = "Enable foreground-service reliability mode",
            rationale = "Runs the exporter inside a foreground service with a persistent " +
                "notification. This is the single most effective way to survive OEM background " +
                "killers. Users see a constant notification while enabled.",
            severity = StepSeverity.RECOMMENDED,
            verification = VerificationKind.DIRECT,
            manualSteps = listOf(
                "Open the reliability screen in this app.",
                "Toggle \"Keep exporter running (foreground mode)\".",
                "A persistent notification will appear; dismissing it stops the mode."
            )
        )

        // Boot persistence
        out += ChecklistStep(
            id = ID_BOOT,
            title = "Re-schedule on reboot",
            rationale = "Without this, the exporter does not resume after a restart until the " +
                "user opens the app.",
            severity = StepSeverity.REQUIRED,
            verification = VerificationKind.DIRECT,
            manualSteps = listOf(
                "Open app Settings.",
                "Enable \"Persist across reboot\" and \"Enable scheduling\"."
            )
        )

        // Periodic job
        out += ChecklistStep(
            id = ID_JOB,
            title = "Register periodic push job",
            rationale = "JobScheduler is the baseline mechanism for periodic pushes when the " +
                "foreground mode is off.",
            severity = StepSeverity.REQUIRED,
            verification = VerificationKind.DIRECT,
            manualSteps = listOf(
                "Open app Settings.",
                "Set push interval and enable scheduling."
            )
        )

        // OEM-specific steps
        for (route in oemRoutes) {
            out += ChecklistStep(
                id = "oem:${route.id}",
                title = route.title,
                rationale = route.rationale,
                severity = StepSeverity.REQUIRED,
                verification = VerificationKind.USER_CONFIRMED,
                candidates = route.candidates,
                manualSteps = route.manualSteps
            )
        }

        // Self-test
        out += ChecklistStep(
            id = ID_SELF_TEST,
            title = "Run background self-test",
            rationale = "Empirical confirmation that your settings actually survive screen-off " +
                "and OS throttling.",
            severity = StepSeverity.RECOMMENDED,
            verification = VerificationKind.INDIRECT,
            hasSelfTest = true,
            manualSteps = listOf(
                "Start the test from the reliability screen.",
                "Lock the screen and leave the device idle for the chosen duration.",
                "Come back to see whether the scheduled pushes actually ran."
            )
        )

        return out
    }

    /**
     * Pure state-transition helper used by the UI layer. Given a step and the signals we have,
     * produce its current [StepStatus].
     */
    fun classify(
        step: ChecklistStep,
        directlyDone: Boolean?,
        userConfirmed: Boolean
    ): StepStatus {
        if (step.verification == VerificationKind.NOT_APPLICABLE) return StepStatus.NOT_APPLICABLE
        return when (step.verification) {
            VerificationKind.DIRECT -> when (directlyDone) {
                true -> StepStatus.DONE
                false -> notDoneForSeverity(step.severity)
                null -> StepStatus.UNKNOWN
            }
            VerificationKind.INDIRECT -> when (directlyDone) {
                true -> StepStatus.DONE
                false -> notDoneForSeverity(step.severity)
                null -> StepStatus.UNKNOWN
            }
            VerificationKind.USER_CONFIRMED -> if (userConfirmed) StepStatus.DONE
                else notDoneForSeverity(step.severity)
            VerificationKind.NOT_APPLICABLE -> StepStatus.NOT_APPLICABLE
        }
    }

    private fun notDoneForSeverity(sev: StepSeverity): StepStatus = when (sev) {
        StepSeverity.REQUIRED -> StepStatus.REQUIRED_NOT_DONE
        StepSeverity.RECOMMENDED -> StepStatus.RECOMMENDED_NOT_DONE
        StepSeverity.ADVANCED -> StepStatus.RECOMMENDED_NOT_DONE
    }
}
