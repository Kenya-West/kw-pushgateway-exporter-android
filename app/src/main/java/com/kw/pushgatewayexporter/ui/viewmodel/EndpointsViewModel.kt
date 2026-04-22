package com.kw.pushgatewayexporter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.config.EndpointProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EndpointsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PushgatewayExporterApp

    private val _endpoints = MutableStateFlow<List<EndpointProfile>>(emptyList())
    val endpoints: StateFlow<List<EndpointProfile>> = _endpoints.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _endpoints.value = app.configRepository.getEndpoints()
        _activeId.value = app.configRepository.getActiveEndpointId()
    }

    fun setActive(id: String) {
        app.configRepository.setActiveEndpointId(id)
        // Re-schedule with the new endpoint values if scheduling is on.
        val cfg = app.configRepository.getConfig()
        if (cfg.schedulingEnabled && cfg.isConfigured) {
            app.schedulerManager.schedulePeriodicJob(cfg)
        }
        refresh()
        _message.value = "Active endpoint updated"
    }

    fun upsert(ep: EndpointProfile): Boolean {
        val trimmed = ep.copy(
            name = ep.name.trim(),
            url = ep.url.trim(),
            jobName = ep.jobName.trim().ifBlank { "android_device_exporter" }
        )
        if (trimmed.name.isBlank()) {
            _message.value = "Name cannot be empty"
            return false
        }
        if (trimmed.url.isBlank()) {
            _message.value = "URL cannot be empty"
            return false
        }
        if (!trimmed.url.startsWith("http://") && !trimmed.url.startsWith("https://")) {
            _message.value = "URL must start with http:// or https://"
            return false
        }
        app.configRepository.upsertEndpoint(trimmed)
        refresh()
        _message.value = "Endpoint saved"
        return true
    }

    fun delete(id: String) {
        app.configRepository.deleteEndpoint(id)
        refresh()
        _message.value = "Endpoint deleted"
    }

    fun duplicate(id: String) {
        val src = _endpoints.value.firstOrNull { it.id == id } ?: return
        val copy = src.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${src.name} (copy)"
        )
        app.configRepository.upsertEndpoint(copy)
        refresh()
    }

    fun exportJson(): String = app.configRepository.exportEndpointsJson()

    fun importJson(json: String, replace: Boolean) {
        try {
            val count = app.configRepository.importEndpointsJson(json, replace)
            refresh()
            _message.value = if (replace) {
                "Imported $count endpoints (replaced existing)"
            } else {
                "Imported $count endpoints"
            }
        } catch (t: Throwable) {
            _message.value = "Import failed: ${t.message ?: "invalid JSON"}"
        }
    }

    fun clearMessage() { _message.value = null }
}
