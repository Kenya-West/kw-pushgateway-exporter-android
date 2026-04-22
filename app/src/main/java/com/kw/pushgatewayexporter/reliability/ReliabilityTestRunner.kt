package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.PushgatewayExporterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Framework to empirically test whether background execution survives screen-off / OEM
 * restrictions.
 *
 * Strategy: schedule the normal exporter job at a short interval for the duration of the
 * test, record the `last_push_seen` heartbeat each minute, and compare observed runs
 * against expected runs. This is inherently *indirect* verification (see
 * [VerificationKind.INDIRECT]) — the user still has to leave the device alone.
 */
class ReliabilityTestRunner(
    private val app: PushgatewayExporterApp,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    enum class Mode(val durationMinutes: Int, val intervalMinutes: Int) {
        SHORT(15, 1),
        LONG(60, 5),
        NETWORK_RECONNECT(10, 1),
        CHARGING(15, 1),
        REBOOT_PERSISTENCE(0, 15) // user-driven; we just snapshot state
    }

    enum class Outcome { PASSED, PARTIAL, FAILED, INCONCLUSIVE, RUNNING }

    data class State(
        val mode: Mode? = null,
        val running: Boolean = false,
        val startedMillis: Long = 0L,
        val expectedRuns: Int = 0,
        val observedRuns: Int = 0,
        val finishedMillis: Long = 0L,
        val outcome: Outcome = Outcome.INCONCLUSIVE,
        val notes: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var currentJob: Job? = null

    fun start(mode: Mode) {
        cancel()
        val baseline = System.currentTimeMillis()
        val expected = if (mode.durationMinutes > 0) {
            (mode.durationMinutes / mode.intervalMinutes).coerceAtLeast(1)
        } else 0
        _state.value = State(
            mode = mode,
            running = true,
            startedMillis = baseline,
            expectedRuns = expected,
            observedRuns = 0,
            outcome = Outcome.RUNNING,
            notes = when (mode) {
                Mode.SHORT -> "Lock the screen and leave the device idle for ~15 min."
                Mode.LONG -> "Lock the screen and leave the device idle for ~1 hour."
                Mode.NETWORK_RECONNECT -> "Toggle airplane mode once during the test."
                Mode.CHARGING -> "Start the test while charging; unplug after 5 min."
                Mode.REBOOT_PERSISTENCE -> "Reboot the device, wait a few minutes, then return."
            }
        )
        app.reliabilityLog.i(TAG, "Self-test started: mode=$mode, expected=$expected")

        // Schedule at the test interval temporarily.
        val originalConfig = app.configRepository.getConfig()
        val testConfig = originalConfig.copy(
            pushIntervalMinutes = mode.intervalMinutes,
            schedulingEnabled = true
        )
        app.configRepository.saveConfig(testConfig)
        app.schedulerManager.schedulePeriodicJob(testConfig)

        currentJob = scope.launch {
            val heartbeatsSeenAtStart = app.reliabilityPreferences.lastPushTimestamp()
            val durationMs = if (mode.durationMinutes > 0) {
                mode.durationMinutes * 60L * 1000L
            } else {
                // Reboot persistence mode just waits for the user.
                24L * 60L * 60L * 1000L
            }
            val pollMs = 30_000L
            val deadline = baseline + durationMs
            while (System.currentTimeMillis() < deadline && _state.value.running) {
                delay(pollMs)
                val observed = countObservedRunsSince(baseline, heartbeatsSeenAtStart)
                _state.value = _state.value.copy(observedRuns = observed)
            }
            finish(originalConfig)
        }
    }

    /** Stop early without recording a pass. */
    fun cancel() {
        val s = _state.value
        if (!s.running) return
        currentJob?.cancel()
        currentJob = null
        _state.value = s.copy(running = false, outcome = Outcome.INCONCLUSIVE, notes = "Cancelled by user")
    }

    private suspend fun finish(originalConfig: com.kw.pushgatewayexporter.config.AppConfig) {
        val s = _state.value
        val observed = s.observedRuns
        val outcome = when {
            s.expectedRuns == 0 -> Outcome.INCONCLUSIVE
            observed >= s.expectedRuns -> Outcome.PASSED
            observed >= (s.expectedRuns * 0.5).toInt() -> Outcome.PARTIAL
            observed == 0 -> Outcome.FAILED
            else -> Outcome.FAILED
        }
        val notes = "Observed $observed of ${s.expectedRuns} scheduled runs. " +
            "OEM=${app.reliabilityManager.oemProfile.displayName}"
        val finished = System.currentTimeMillis()
        _state.value = s.copy(
            running = false,
            observedRuns = observed,
            finishedMillis = finished,
            outcome = outcome,
            notes = notes
        )
        app.reliabilityPreferences.saveSelfTest(
            ReliabilityPreferences.SelfTestSnapshot(
                mode = s.mode?.name ?: "UNKNOWN",
                startedMillis = s.startedMillis,
                finishedMillis = finished,
                expectedRuns = s.expectedRuns,
                observedRuns = observed,
                outcome = outcome.name,
                notes = notes
            )
        )
        app.reliabilityLog.i(TAG, "Self-test finished: $outcome ($notes)")

        // Restore original scheduling so we do not leave the interval stuck at 1min.
        withContext(Dispatchers.IO) {
            app.configRepository.saveConfig(originalConfig)
            if (originalConfig.schedulingEnabled) {
                app.schedulerManager.schedulePeriodicJob(originalConfig)
            } else {
                app.schedulerManager.cancelJob()
            }
        }
    }

    private fun countObservedRunsSince(startMillis: Long, heartbeatAtStart: Long): Int {
        val now = app.reliabilityPreferences.lastPushTimestamp()
        if (now <= heartbeatAtStart) return 0
        // We can't distinguish individual runs from a single heartbeat — use attempt counter instead.
        val attemptsNow = app.configRepository.getPushAttemptsTotal()
        return (attemptsNow - attemptsAtBaseline(startMillis)).toInt().coerceAtLeast(0)
    }

    // We latch the baseline attempt counter on first call.
    @Volatile private var baselineAttempts: Long = -1
    @Volatile private var baselineTakenAt: Long = 0
    private fun attemptsAtBaseline(startMillis: Long): Long {
        if (baselineAttempts < 0 || baselineTakenAt != startMillis) {
            baselineAttempts = app.configRepository.getPushAttemptsTotal()
            baselineTakenAt = startMillis
        }
        return baselineAttempts
    }

    companion object {
        private const val TAG = "SelfTest"
    }
}
