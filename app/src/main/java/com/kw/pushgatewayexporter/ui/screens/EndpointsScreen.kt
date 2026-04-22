package com.kw.pushgatewayexporter.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kw.pushgatewayexporter.config.EndpointProfile
import com.kw.pushgatewayexporter.ui.viewmodel.EndpointsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointsScreen(
    navController: NavController,
    viewModel: EndpointsViewModel = viewModel()
) {
    val context = LocalContext.current
    val endpoints by viewModel.endpoints.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EndpointProfile?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var showImportChoice by remember { mutableStateOf<String?>(null) } // holds json payload

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(viewModel.exportJson().toByteArray(Charsets.UTF_8))
                }
            }.onFailure { viewModel.clearMessage() }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val json = readTextFromUri(context, uri)
            if (json != null) showImportChoice = json
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Endpoints") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import")
                    }
                    IconButton(
                        enabled = endpoints.isNotEmpty(),
                        onClick = { exportLauncher.launch("pushgateway-endpoints.json") }
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Export")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = EndpointProfile(name = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Add endpoint")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (endpoints.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No endpoints configured.\nTap + to add one, or use the import action above.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(endpoints, key = { it.id }) { ep ->
                    EndpointCard(
                        endpoint = ep,
                        isActive = ep.id == activeId,
                        onSetActive = { viewModel.setActive(ep.id) },
                        onEdit = { editing = ep },
                        onDuplicate = { viewModel.duplicate(ep.id) },
                        onDelete = { confirmDeleteId = ep.id }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editing?.let { ep ->
        EndpointEditDialog(
            initial = ep,
            onDismiss = { editing = null },
            onSave = { updated ->
                if (viewModel.upsert(updated)) editing = null
            }
        )
    }

    confirmDeleteId?.let { id ->
        val ep = endpoints.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Delete endpoint?") },
            text = { Text("\"${ep?.name ?: "Endpoint"}\" will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    confirmDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    showImportChoice?.let { json ->
        AlertDialog(
            onDismissRequest = { showImportChoice = null },
            title = { Text("Import endpoints") },
            text = { Text("Merge with existing endpoints, or replace all?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importJson(json, replace = false)
                    showImportChoice = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.importJson(json, replace = true)
                        showImportChoice = null
                    }) { Text("Replace") }
                    TextButton(onClick = { showImportChoice = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun EndpointCard(
    endpoint: EndpointProfile,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onSetActive)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        endpoint.name.ifBlank { "(unnamed)" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        endpoint.url.ifBlank { "(no URL)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2
                    )
                    if (isActive) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDuplicate) { Text("Duplicate") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun EndpointEditDialog(
    initial: EndpointProfile,
    onDismiss: () -> Unit,
    onSave: (EndpointProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var jobName by remember { mutableStateOf(initial.jobName) }
    var insecureTls by remember { mutableStateOf(initial.insecureTls) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank() && initial.url.isBlank()) "Add endpoint" else "Edit endpoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Pushgateway URL") },
                    placeholder = { Text("https://pushgateway.example.com") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jobName, onValueChange = { jobName = it },
                    label = { Text("Job Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Insecure TLS (test only)", modifier = Modifier.weight(1f))
                    Switch(checked = insecureTls, onCheckedChange = { insecureTls = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name,
                        url = url,
                        username = username,
                        password = password,
                        jobName = jobName,
                        insecureTls = insecureTls
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}
