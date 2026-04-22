package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kw.pushgatewayexporter.reliability.EvaluatedStep
import com.kw.pushgatewayexporter.reliability.StepSeverity
import com.kw.pushgatewayexporter.reliability.StepStatus
import com.kw.pushgatewayexporter.reliability.VerificationKind
import com.kw.pushgatewayexporter.ui.theme.StatusError
import com.kw.pushgatewayexporter.ui.theme.StatusSuccess
import com.kw.pushgatewayexporter.ui.theme.StatusWarning
import com.kw.pushgatewayexporter.ui.viewmodel.ReliabilityViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReliabilityScreen(
    navController: NavController,
    viewModel: ReliabilityViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keep exporter running") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("diagnostics") }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-check")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Intro / why
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Why this matters", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Some Android versions and manufacturers aggressively stop apps running " +
                            "in the background. This wizard walks you through every setting " +
                            "that can improve reliability on your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Detected device: ${state.oemProfile.displayName} · API ${state.sdkInt}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.oemProfile.overview.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.oemProfile.overview, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Inconsistencies (one-tap repair)
            if (state.inconsistencies.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Something looks off",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        for (issue in state.inconsistencies) {
                            Text("• $issue", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.runSelfHealing() }) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Repair")
                        }
                        if (state.selfHealSummary.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            for (line in state.selfHealSummary) {
                                Text(line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            // Foreground service quick toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Foreground reliability mode",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Runs the exporter with a persistent notification. Strongest " +
                                    "continuity guarantee on most devices.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = state.foregroundModeEnabled,
                            onCheckedChange = { viewModel.setForegroundMode(it) }
                        )
                    }
                    if (state.foregroundModeEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.foregroundServiceRunning) "Service is running"
                            else "Service is enabled but not yet running",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.foregroundServiceRunning) StatusSuccess else StatusWarning
                        )
                    }
                }
            }

            // Steps
            Text("Checklist", style = MaterialTheme.typography.titleLarge)
            for (evaluated in state.steps) {
                StepCard(
                    ev = evaluated,
                    onOpenSettings = { viewModel.openSettingsForStep(evaluated.step.id) },
                    onMarkDone = { done -> viewModel.markStepConfirmed(evaluated.step.id, done) },
                    onRequestExemption = { viewModel.requestBatteryOptimizationExemption() },
                    onStartTest = { navController.navigate("reliability_test") }
                )
            }

            // OEM general tips
            if (state.oemProfile.generalTips.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("More tips for this device",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        for (tip in state.oemProfile.generalTips) {
                            Text("• $tip", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Reminders + reset
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Don't remind me again", Modifier.weight(1f))
                        Switch(
                            checked = state.remindersDisabled,
                            onCheckedChange = { viewModel.setRemindersDisabled(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.resetAssistant() }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reset setup assistant")
                    }
                }
            }
        }
    }

    val toast = state.lastToast
    if (toast != null) {
        LaunchedEffect(toast) {
            // Toast is surfaced as a snackbar via the scaffold in a fuller UI; here we
            // just clear it after a tick so repeated actions work.
            kotlinx.coroutines.delay(2000)
            viewModel.consumeToast()
        }
    }
}

@Composable
private fun StepCard(
    ev: EvaluatedStep,
    onOpenSettings: () -> Unit,
    onMarkDone: (Boolean) -> Unit,
    onRequestExemption: () -> Unit,
    onStartTest: () -> Unit
) {
    var showManual by remember { mutableStateOf(false) }
    val step = ev.step
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(ev.status, step.severity)
                Spacer(Modifier.width(8.dp))
                Text(step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(step.rationale, style = MaterialTheme.typography.bodySmall)
            ev.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            FlowButtons {
                if (step.candidates.isNotEmpty() || step.id ==
                    com.kw.pushgatewayexporter.reliability.ReliabilityChecklist.ID_BATTERY_OPT) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Launch, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Open settings")
                    }
                }
                if (step.id == com.kw.pushgatewayexporter.reliability.ReliabilityChecklist.ID_BATTERY_OPT &&
                    ev.status == StepStatus.REQUIRED_NOT_DONE) {
                    Button(onClick = onRequestExemption) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Request exemption")
                    }
                }
                if (step.manualSteps.isNotEmpty()) {
                    TextButton(onClick = { showManual = !showManual }) {
                        Icon(
                            if (showManual) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showManual) "Hide steps" else "Show manual steps")
                    }
                }
                if (step.hasSelfTest) {
                    OutlinedButton(onClick = onStartTest) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Test")
                    }
                }
                if (step.verification == VerificationKind.USER_CONFIRMED) {
                    val confirmed = ev.status == StepStatus.DONE
                    FilterChip(
                        selected = confirmed,
                        onClick = { onMarkDone(!confirmed) },
                        label = { Text(if (confirmed) "Marked done" else "Mark as done") },
                        leadingIcon = {
                            Icon(
                                if (confirmed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            if (showManual && step.manualSteps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                for ((i, line) in step.manualSteps.withIndex()) {
                    Text("${i + 1}. $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: StepStatus, severity: StepSeverity) {
    val (label, color) = when (status) {
        StepStatus.DONE -> "Done" to StatusSuccess
        StepStatus.REQUIRED_NOT_DONE -> "Required" to StatusError
        StepStatus.RECOMMENDED_NOT_DONE -> "Recommended" to StatusWarning
        StepStatus.NOT_APPLICABLE -> "Not applicable" to Color.Gray
        StepStatus.UNKNOWN -> "Could not verify" to Color.Gray
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.15f),
            disabledLabelColor = color,
            disabledLeadingIconContentColor = color
        )
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowButtons(content: @Composable () -> Unit) {
    // Simple wrap row using Row with horizontalArrangement; for dense buttons we rely on
    // the OS layout to wrap. Keep it simple to avoid extra dependencies.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}
