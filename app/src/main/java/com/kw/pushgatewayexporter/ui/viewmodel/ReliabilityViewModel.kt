package com.kw.pushgatewayexporter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.reliability.DiagnosticsReporter
import com.kw.pushgatewayexporter.reliability.EvaluatedStep
import com.kw.pushgatewayexporter.reliability.ReliabilityTestRunner
import com.kw.pushgatewayexporter.reliability.SettingsNavigator
import com.kw.pushgatewayexporter.reliability.oem.OemProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReliabilityUiState(
    val oemProfile: OemProfile,
    val sdkInt: Int,
    val steps: List<EvaluatedStep> = emptyList(),
    val foregroundModeEnabled: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val remindersDisabled: Boolean = false,
    val lastToast: String? = null,
    val selfHealSummary: List<String> = emptyList(),
    val inconsistencies: List<String> = emptyList(),
    val diagnosticsText: String? = null,
    val testState: ReliabilityTestRunner.State = ReliabilityTestRunner.State()
)

/**
 * Reliability screen + diagnostics screen + self-test screen share this single view model
 * because they all read from the same underlying reliability state and it is cheap.
 */
class ReliabilityViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PushgatewayExporterApp
    private val manager get() = app.reliabilityManager
    private val navigator: SettingsNavigator get() = manager.navigator
    private val testRunner get() = app.reliabilityTestRunner

    private val _state: MutableStateFlow<ReliabilityUiState> =
        MutableStateFlow(
            ReliabilityUiState(
                oemProfile = manager.oemProfile,
                sdkInt = android.os.Build.VERSION.SDK_INT
            )
        )
    val state: StateFlow<ReliabilityUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            testRunner.state.collect { testState ->
                _state.value = _state.value.copy(testState = testState)
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(
            steps = manager.evaluate(),
            foregroundModeEnabled = manager.preferences.isForegroundModeEnabled(),
            foregroundServiceRunning = manager.foregroundController.isRunning(),
            remindersDisabled = manager.preferences.isRemindersDisabled(),
            inconsistencies = app.bootRecoveryCoordinator.detectInconsistencies()
        )
    }

    // --- Step actions ---

    fun openSettingsForStep(stepId: String) {
        val step = manager.buildSteps().firstOrNull { it.id == stepId } ?: return
        val result = when {
            stepId == com.kw.pushgatewayexporter.reliability.ReliabilityChecklist.ID_BATTERY_OPT -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    navigator.openIgnoreBatteryOptimizationList()
                } else navigator.openAppDetails()
            }
            step.candidates.isNotEmpty() -> navigator.tryCandidates(step.candidates)
            else -> navigator.openAppDetails()
        }
        _state.value = _state.value.copy(
            lastToast = when (result) {
                is SettingsNavigator.Result.Success -> "Opened: ${result.label}"
                is SettingsNavigator.Result.Failure -> "Could not open settings; see manual steps"
            }
        )
    }

    fun requestBatteryOptimizationExemption() {
        val result = navigator.requestIgnoreBatteryOptimization()
        _state.value = _state.value.copy(
            lastToast = when (result) {
                is SettingsNavigator.Result.Success -> "Requested exemption"
                is SettingsNavigator.Result.Failure -> "Failed to request exemption; see manual steps"
            }
        )
    }

    fun markStepConfirmed(stepId: String, confirmed: Boolean) {
        manager.preferences.setStepConfirmed(stepId, manager.oemProfile.id, confirmed)
        refresh()
    }

    // --- Foreground service toggle ---

    fun setForegroundMode(enabled: Boolean) {
        if (enabled) manager.foregroundController.start()
        else manager.foregroundController.stop()
        refresh()
    }

    // --- Reminders ---

    fun setRemindersDisabled(disabled: Boolean) {
        manager.preferences.setRemindersDisabled(disabled)
        refresh()
    }

    fun resetAssistant() {
        manager.preferences.resetAssistant()
        refresh()
    }

    // --- Diagnostics ---

    fun loadDiagnostics() {
        viewModelScope.launch(Dispatchers.Default) {
            val text = DiagnosticsReporter(getApplication()).build()
            _state.value = _state.value.copy(diagnosticsText = text)
        }
    }

    fun clearLog() {
        manager.log.clear()
        loadDiagnostics()
    }

    fun runSelfHealing() {
        val summary = manager.runSelfHealing()
        _state.value = _state.value.copy(selfHealSummary = summary)
        refresh()
    }

    // --- Self-test ---

    fun startSelfTest(mode: ReliabilityTestRunner.Mode) = testRunner.start(mode)
    fun cancelSelfTest() = testRunner.cancel()

    fun consumeToast() {
        _state.value = _state.value.copy(lastToast = null)
    }
}
