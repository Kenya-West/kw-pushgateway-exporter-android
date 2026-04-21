package com.kw.pushgatewayexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kw.pushgatewayexporter.ui.viewmodel.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(navController: NavController, viewModel: ConfigViewModel = viewModel()) {
    val config by viewModel.config.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    var instanceId by remember { mutableStateOf(viewModel.getInstanceId()) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.saveConfig() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Save Configuration")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Connection ---
            item { SectionHeader("Connection") }
            item {
                OutlinedTextField(
                    value = config.pushgatewayUrl,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(pushgatewayUrl = it) } },
                    label = { Text("Pushgateway URL") },
                    placeholder = { Text("https://pushgateway.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = config.basicAuthUsername,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(basicAuthUsername = it) } },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = config.basicAuthPassword,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(basicAuthPassword = it) } },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SwitchRow("Insecure TLS (test only)", config.insecureTls) {
                    viewModel.updateConfig { c -> c.copy(insecureTls = it) }
                }
            }

            // --- Identity ---
            item { SectionHeader("Identity") }
            item {
                OutlinedTextField(
                    value = config.jobName,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(jobName = it) } },
                    label = { Text("Job Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instance ID", style = MaterialTheme.typography.bodySmall)
                        Text(
                            instanceId,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = {
                        instanceId = viewModel.resetInstanceId()
                    }) {
                        Text("Reset")
                    }
                }
            }
            item {
                SwitchRow("Include device labels", config.includeDeviceLabels) {
                    viewModel.updateConfig { c -> c.copy(includeDeviceLabels = it) }
                }
            }

            // --- Scheduling ---
            item { SectionHeader("Scheduling") }
            item {
                SwitchRow("Enable periodic push", config.schedulingEnabled) {
                    viewModel.updateConfig { c -> c.copy(schedulingEnabled = it) }
                }
            }
            item {
                IntervalSelector(config.pushIntervalMinutes) {
                    viewModel.updateConfig { c -> c.copy(pushIntervalMinutes = it) }
                }
            }
            item {
                SwitchRow("Unmetered network only", config.requireUnmeteredNetwork) {
                    viewModel.updateConfig { c -> c.copy(requireUnmeteredNetwork = it) }
                }
            }
            item {
                SwitchRow("Only while charging", config.requireCharging) {
                    viewModel.updateConfig { c -> c.copy(requireCharging = it) }
                }
            }
            item {
                SwitchRow("Persist across reboot", config.persistAcrossReboot) {
                    viewModel.updateConfig { c -> c.copy(persistAcrossReboot = it) }
                }
            }

            // --- Log Level ---
            item { SectionHeader("Logging") }
            item {
                LogLevelSelector(config.logLevel) {
                    viewModel.updateConfig { c -> c.copy(logLevel = it) }
                }
            }

            // --- Timeouts ---
            item { SectionHeader("Timeouts & Retry") }
            item {
                NumberField("Connect timeout (s)", config.connectTimeoutSeconds) {
                    viewModel.updateConfig { c -> c.copy(connectTimeoutSeconds = it.coerceIn(1, 120)) }
                }
            }
            item {
                NumberField("Read timeout (s)", config.readTimeoutSeconds) {
                    viewModel.updateConfig { c -> c.copy(readTimeoutSeconds = it.coerceIn(1, 120)) }
                }
            }
            item {
                NumberField("Write timeout (s)", config.writeTimeoutSeconds) {
                    viewModel.updateConfig { c -> c.copy(writeTimeoutSeconds = it.coerceIn(1, 120)) }
                }
            }
            item {
                NumberField("Max retries", config.maxRetries) {
                    viewModel.updateConfig { c -> c.copy(maxRetries = it.coerceIn(0, 10)) }
                }
            }
            item {
                NumberField("Backoff base (s)", config.retryBackoffBaseSeconds) {
                    viewModel.updateConfig { c -> c.copy(retryBackoffBaseSeconds = it.coerceIn(1, 60)) }
                }
            }

            // --- Metric Families ---
            item { SectionHeader("Metric Families") }
            item {
                SwitchRow("Exporter self-metrics", config.enableExporterSelf) {
                    viewModel.updateConfig { c -> c.copy(enableExporterSelf = it) }
                }
            }
            item {
                SwitchRow("Device info", config.enableDeviceInfo) {
                    viewModel.updateConfig { c -> c.copy(enableDeviceInfo = it) }
                }
            }
            item {
                SwitchRow("Uptime", config.enableUptime) {
                    viewModel.updateConfig { c -> c.copy(enableUptime = it) }
                }
            }
            item {
                SwitchRow("Memory", config.enableMemory) {
                    viewModel.updateConfig { c -> c.copy(enableMemory = it) }
                }
            }
            item {
                SwitchRow("Storage", config.enableStorage) {
                    viewModel.updateConfig { c -> c.copy(enableStorage = it) }
                }
            }
            item {
                SwitchRow("Battery", config.enableBattery) {
                    viewModel.updateConfig { c -> c.copy(enableBattery = it) }
                }
            }
            item {
                SwitchRow("Network", config.enableNetwork) {
                    viewModel.updateConfig { c -> c.copy(enableNetwork = it) }
                }
            }
            item {
                SwitchRow("WiFi", config.enableWifi) {
                    viewModel.updateConfig { c -> c.copy(enableWifi = it) }
                }
            }
            item {
                SwitchRow("Telephony", config.enableTelephony) {
                    viewModel.updateConfig { c -> c.copy(enableTelephony = it) }
                }
            }
            item {
                SwitchRow("Traffic counters", config.enableTraffic) {
                    viewModel.updateConfig { c -> c.copy(enableTraffic = it) }
                }
            }
            item {
                SwitchRow("Display", config.enableDisplay) {
                    viewModel.updateConfig { c -> c.copy(enableDisplay = it) }
                }
            }
            item {
                SwitchRow("Hardware features", config.enableFeatures) {
                    viewModel.updateConfig { c -> c.copy(enableFeatures = it) }
                }
            }
            item {
                SwitchRow("Sensors", config.enableSensors) {
                    viewModel.updateConfig { c -> c.copy(enableSensors = it) }
                }
            }

            // --- Privacy ---
            item { SectionHeader("Privacy") }
            item {
                SwitchRow("Sensitive WiFi labels (SSID/BSSID)", config.enableSensitiveWifiLabels) {
                    viewModel.updateConfig { c -> c.copy(enableSensitiveWifiLabels = it) }
                }
            }
            item {
                SwitchRow("Sensitive network labels (IPs/DNS)", config.enableSensitiveNetworkLabels) {
                    viewModel.updateConfig { c -> c.copy(enableSensitiveNetworkLabels = it) }
                }
            }

            // --- Push Method ---
            item { SectionHeader("Push Method") }
            item {
                SwitchRow("Use PUT (full replace)", config.usePutMethod) {
                    viewModel.updateConfig { c -> c.copy(usePutMethod = it) }
                }
                if (!config.usePutMethod) {
                    Text(
                        "POST: partial metric-name replacement within the group",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            // --- Dry Run ---
            item { SectionHeader("Testing") }
            item {
                SwitchRow("Dry-run mode (simulate push)", config.dryRunMode) {
                    viewModel.updateConfig { c -> c.copy(dryRunMode = it) }
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // Space for bottom bar
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    HorizontalDivider()
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSelector(currentMinutes: Int, onSelected: (Int) -> Unit) {
    val options = listOf(1, 5, 10, 15, 30, 60)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "$currentMinutes min",
            onValueChange = {},
            readOnly = true,
            label = { Text("Push interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes min") },
                    onClick = {
                        onSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogLevelSelector(currentLevel: Int, onSelected: (Int) -> Unit) {
    val levels = listOf(0 to "OFF", 1 to "ERROR", 2 to "WARN", 3 to "INFO", 4 to "DEBUG", 5 to "VERBOSE")
    var expanded by remember { mutableStateOf(false) }
    val currentName = levels.find { it.first == currentLevel }?.second ?: "INFO"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Log level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            levels.forEach { (level, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
