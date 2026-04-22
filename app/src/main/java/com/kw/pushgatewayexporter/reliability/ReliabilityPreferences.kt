package com.kw.pushgatewayexporter.reliability

import android.content.Context
import android.content.SharedPreferences

/**
 * Durable user-facing reliability settings — separate from [com.kw.pushgatewayexporter.config.ConfigRepository]
 * so the exporter config stays focused on "what to push / where".
 *
 * Includes:
 *  - foreground-service mode toggle
 *  - reminder dismissal state
 *  - per-step user-confirmed completion (for steps the OS cannot verify)
 *  - self-test history (lightweight)
 */
class ReliabilityPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Foreground service / reliability mode ---

    fun isForegroundModeEnabled(): Boolean = prefs.getBoolean(KEY_FG_MODE, false)
    fun setForegroundModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FG_MODE, enabled).apply()
    }

    // --- Reminders ---

    fun isRemindersDisabled(): Boolean = prefs.getBoolean(KEY_REMINDERS_DISABLED, false)
    fun setRemindersDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_DISABLED, disabled).apply()
    }

    fun lastReminderShownMillis(): Long = prefs.getLong(KEY_LAST_REMINDER, 0L)
    fun markReminderShown() {
        prefs.edit().putLong(KEY_LAST_REMINDER, System.currentTimeMillis()).apply()
    }

    // --- Per-checklist-step user confirmation ---
    //
    // Many OEM steps cannot be verified programmatically. For those, the user taps
    // "Mark as done" and we store that confirmation keyed by the step id.
    //
    // We also track the "OEM profile at confirmation time" so that if the user upgrades
    // their ROM to a different OEM skin the confirmation is implicitly invalidated.

    fun isStepConfirmed(stepId: String, oemProfileId: String): Boolean {
        val storedOem = prefs.getString(stepKeyOem(stepId), null) ?: return false
        return storedOem == oemProfileId && prefs.getBoolean(stepKeyConfirmed(stepId), false)
    }

    fun setStepConfirmed(stepId: String, oemProfileId: String, confirmed: Boolean) {
        prefs.edit().apply {
            putBoolean(stepKeyConfirmed(stepId), confirmed)
            putString(stepKeyOem(stepId), if (confirmed) oemProfileId else null)
            apply()
        }
    }

    fun clearAllStepConfirmations() {
        val edit = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("step_")) edit.remove(k)
        }
        edit.apply()
    }

    // --- Self-test history (most recent only, for simplicity) ---

    data class SelfTestSnapshot(
        val mode: String,
        val startedMillis: Long,
        val finishedMillis: Long,
        val expectedRuns: Int,
        val observedRuns: Int,
        val outcome: String,
        val notes: String
    )

    fun saveSelfTest(snapshot: SelfTestSnapshot) {
        prefs.edit().apply {
            putString(KEY_TEST_MODE, snapshot.mode)
            putLong(KEY_TEST_STARTED, snapshot.startedMillis)
            putLong(KEY_TEST_FINISHED, snapshot.finishedMillis)
            putInt(KEY_TEST_EXPECTED, snapshot.expectedRuns)
            putInt(KEY_TEST_OBSERVED, snapshot.observedRuns)
            putString(KEY_TEST_OUTCOME, snapshot.outcome)
            putString(KEY_TEST_NOTES, snapshot.notes)
            apply()
        }
    }

    fun lastSelfTest(): SelfTestSnapshot? {
        val started = prefs.getLong(KEY_TEST_STARTED, -1L)
        if (started < 0) return null
        return SelfTestSnapshot(
            mode = prefs.getString(KEY_TEST_MODE, "") ?: "",
            startedMillis = started,
            finishedMillis = prefs.getLong(KEY_TEST_FINISHED, 0L),
            expectedRuns = prefs.getInt(KEY_TEST_EXPECTED, 0),
            observedRuns = prefs.getInt(KEY_TEST_OBSERVED, 0),
            outcome = prefs.getString(KEY_TEST_OUTCOME, "") ?: "",
            notes = prefs.getString(KEY_TEST_NOTES, "") ?: ""
        )
    }

    // --- Push-loop heartbeat used by reminder heuristics ---

    fun lastPushTimestamp(): Long = prefs.getLong(KEY_LAST_PUSH_SEEN, 0L)
    fun markPushTimestamp(millis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_PUSH_SEEN, millis).apply()
    }

    // --- Reset ---

    fun resetAssistant() {
        val edit = prefs.edit()
        for (k in prefs.all.keys) edit.remove(k)
        edit.apply()
    }

    private fun stepKeyConfirmed(id: String) = "step_${id}_confirmed"
    private fun stepKeyOem(id: String) = "step_${id}_oem"

    companion object {
        private const val PREFS_NAME = "pushgateway_exporter_reliability_prefs"

        private const val KEY_FG_MODE = "foreground_mode_enabled"
        private const val KEY_REMINDERS_DISABLED = "reminders_disabled"
        private const val KEY_LAST_REMINDER = "last_reminder_shown"

        private const val KEY_TEST_MODE = "selftest_mode"
        private const val KEY_TEST_STARTED = "selftest_started"
        private const val KEY_TEST_FINISHED = "selftest_finished"
        private const val KEY_TEST_EXPECTED = "selftest_expected"
        private const val KEY_TEST_OBSERVED = "selftest_observed"
        private const val KEY_TEST_OUTCOME = "selftest_outcome"
        private const val KEY_TEST_NOTES = "selftest_notes"

        private const val KEY_LAST_PUSH_SEEN = "last_push_seen"
    }
}
