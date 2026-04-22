package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kw.pushgatewayexporter.reliability.ReliabilityTestRunner
import com.kw.pushgatewayexporter.ui.viewmodel.ReliabilityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityTestScreen(
    navController: NavController,
    viewModel: ReliabilityViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Background self-test") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                "This empirically checks whether your device actually lets the exporter run " +
                    "in the background. The test temporarily sets a short push interval, then " +
                    "counts how many scheduled pushes actually ran.",
                style = MaterialTheme.typography.bodyMedium
            )

            val running = state.testState.running
            val test = state.testState

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current test", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (!running && test.mode == null) {
                        Text("No test in progress", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Mode: ${test.mode?.name ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Observed runs: ${test.observedRuns} / ${test.expectedRuns}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (test.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(test.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!running && test.outcome != ReliabilityTestRunner.Outcome.RUNNING) {
                            Spacer(Modifier.height(4.dp))
                            Text("Outcome: ${test.outcome}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text("Start a test", style = MaterialTheme.typography.titleMedium)
            for (mode in ReliabilityTestRunner.Mode.values()) {
                Button(
                    onClick = { viewModel.startSelfTest(mode) },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (mode) {
                            ReliabilityTestRunner.Mode.SHORT -> "Short (≈15 min, 1-min interval)"
                            ReliabilityTestRunner.Mode.LONG -> "Long (≈1 hour, 5-min interval)"
                            ReliabilityTestRunner.Mode.NETWORK_RECONNECT -> "Network reconnect (10 min)"
                            ReliabilityTestRunner.Mode.CHARGING -> "Charging vs not (15 min)"
                            ReliabilityTestRunner.Mode.REBOOT_PERSISTENCE -> "Reboot persistence"
                        }
                    )
                }
            }

            if (running) {
                OutlinedButton(
                    onClick = { viewModel.cancelSelfTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel test")
                }
            }
        }
    }
}
