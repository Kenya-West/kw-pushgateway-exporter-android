package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.kw.pushgatewayexporter.ui.theme.StatusError
import com.kw.pushgatewayexporter.ui.theme.StatusSuccess
import com.kw.pushgatewayexporter.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showTlsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshState() }

    val application = context.applicationContext as com.kw.pushgatewayexporter.PushgatewayExporterApp
    val showReliabilityReminder = remember(state.schedulingEnabled, state.lastPushResult) {
        application.reliabilityManager.shouldShowReminder()
    }
    LaunchedEffect(showReliabilityReminder) {
        if (showReliabilityReminder) application.reliabilityManager.preferences.markReminderShown()
    }

    // Auto-open dialog the first time a TLS trust-anchor error appears.
    LaunchedEffect(state.hasTlsTrustAnchorError, state.lastPushResult?.timestampMillis) {
        if (state.hasTlsTrustAnchorError) showTlsDialog = true
    }

    // Launch the system cert installer once the cert is downloaded + verified.
    LaunchedEffect(state.pendingCertInstallIntent) {
        val intent = state.pendingCertInstallIntent ?: return@LaunchedEffect
        (context as? Activity)?.startActivity(intent)
        viewModel.consumePendingCertInstallIntent()
        showTlsDialog = false
    }

    if (showTlsDialog) {
        TlsErrorDialog(
            insecureTlsEnabled = state.insecureTlsEnabled,
            inProgress = state.tlsFixInProgress,
            message = state.tlsFixMessage,
            onEnableInsecure = {
                viewModel.enableInsecureTls()
                showTlsDialog = false
            },
            onInstallCert = { viewModel.prepareLetsEncryptCertInstall() },
            onDismiss = {
                viewModel.clearTlsFixMessage()
                showTlsDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pushgateway Exporter") },
                actions = {
                    IconButton(onClick = { navController.navigate("config") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            // Reliability reminder banner
            if (showReliabilityReminder) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Background reliability not fully configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "Your device may stop the exporter in the background. Finish the " +
                                "reliability checklist to improve continuity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { navController.navigate("reliability") }) {
                            Icon(Icons.Default.Shield, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Open checklist")
                        }
                    }
                }
            }

            // Connection status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (state.isConfigured) {
                        InfoRow("URL", state.pushgatewayUrl)
                        InfoRow("Job", state.jobName)
                        InfoRow("Instance", state.instanceId, mono = true)
                    } else {
                        Text(
                            "Not configured — tap Settings to set Pushgateway URL",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Scheduling status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Scheduling", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Enabled", if (state.schedulingEnabled) "Yes" else "No")
                    if (state.schedulingEnabled) {
                        InfoRow("Interval", "${state.pushIntervalMinutes} min")
                        InfoRow("Job active", if (state.isJobScheduled) "Yes" else "No")
                    }
                    if (state.dryRunMode) {
                        Text(
                            "⚠ Dry-run mode active — pushes are simulated",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Last push result
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last Push", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val result = state.lastPushResult
                    if (result != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.success) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (result.success) "Success" else "Failed",
                                fontWeight = FontWeight.Bold,
                                color = if (result.success) StatusSuccess else StatusError
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (result.httpStatusCode > 0) {
                            InfoRow("HTTP Status", "${result.httpStatusCode}")
                        }
                        InfoRow("Duration", "${result.durationMillis} ms")
                        InfoRow("Payload", formatBytes(result.payloadSizeBytes))
                        InfoRow("Metrics", "${result.metricsCount}")
                        InfoRow("Time", formatTimestamp(result.timestampMillis))
                        if (!result.errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Error: ${result.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (state.hasTlsTrustAnchorError) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { showTlsDialog = true }) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Fix TLS error")
                            }
                        }
                    } else {
                        Text("No pushes yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Stats
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Push attempts", "${state.pushAttemptsTotal}")
                    InfoRow("Push failures", "${state.pushFailuresTotal}")
                    if (state.lastSuccessTime > 0) {
                        InfoRow("Last success", formatTimestamp(state.lastSuccessTime))
                    }
                    if (state.lastFailureTime > 0) {
                        InfoRow("Last failure", formatTimestamp(state.lastFailureTime))
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.pushNow() },
                    enabled = state.isConfigured && !state.isPushing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isPushing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isPushing) "Pushing…" else "Push Now")
                }
                OutlinedButton(
                    onClick = { navController.navigate("preview") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Preview, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Preview")
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("catalog") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Catalog")
                }
                OutlinedButton(
                    onClick = { navController.navigate("samples") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Samples")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("reliability") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reliability")
                }
                OutlinedButton(
                    onClick = { navController.navigate("diagnostics") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Diagnostics")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
private fun TlsErrorDialog(
    insecureTlsEnabled: Boolean,
    inProgress: Boolean,
    message: String?,
    onEnableInsecure: () -> Unit,
    onInstallCert: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("TLS certificate error") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The push failed because the device could not validate the " +
                        "Pushgateway's TLS certificate chain."
                )
                Text(
                    "This usually means the server uses a Let's Encrypt certificate " +
                        "that chains to the ISRG Root X1 root CA. Android versions " +
                        "older than 7.1.1 do not ship this root, so the chain is " +
                        "untrusted out of the box.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("You have two options:", fontWeight = FontWeight.Bold)
                Text(
                    "1. Install the Let's Encrypt root CA (ISRG Root X1) into " +
                        "Android's user certificate store. The app will download it " +
                        "from letsencrypt.org, verify its SHA-256 fingerprint, and " +
                        "hand it to the system installer. You may be prompted to set " +
                        "a screen lock.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "2. Disable TLS validation for this endpoint (insecure — use " +
                        "only on trusted LANs or test environments).",
                    style = MaterialTheme.typography.bodySmall
                )
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Working…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstallCert, enabled = !inProgress) {
                Text("Install root CA")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = onEnableInsecure,
                    enabled = !inProgress && !insecureTlsEnabled
                ) {
                    Text(if (insecureTlsEnabled) "Insecure TLS on" else "Disable TLS check")
                }
                TextButton(onClick = onDismiss, enabled = !inProgress) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun formatBytes(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
