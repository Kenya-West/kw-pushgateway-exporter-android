package com.kw.pushgatewayexporter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.config.AppConfig
import com.kw.pushgatewayexporter.config.EndpointProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PushgatewayExporterApp
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _endpoints = MutableStateFlow<List<EndpointProfile>>(emptyList())
    val endpoints: StateFlow<List<EndpointProfile>> = _endpoints.asStateFlow()

    private val _activeEndpointId = MutableStateFlow<String?>(null)
    val activeEndpointId: StateFlow<String?> = _activeEndpointId.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        _endpoints.value = app.configRepository.getEndpoints()
        _activeEndpointId.value = app.configRepository.getActiveEndpointId()
        // getConfig() reads connection fields that are kept in sync with the active endpoint.
        _config.value = app.configRepository.getConfig()
    }

    fun setActiveEndpoint(id: String) {
        app.configRepository.setActiveEndpointId(id)
        val cfg = app.configRepository.getConfig()
        if (cfg.schedulingEnabled && cfg.isConfigured) {
            app.schedulerManager.schedulePeriodicJob(cfg)
        }
        loadConfig()
        _saveMessage.value = "Active endpoint updated"
    }

    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        _config.value = transform(_config.value)
    }

    fun saveConfig() {
        val cfg = _config.value

        // Validate URL
        if (cfg.pushgatewayUrl.isNotBlank()) {
            if (!cfg.pushgatewayUrl.startsWith("http://") && !cfg.pushgatewayUrl.startsWith("https://")) {
                _saveMessage.value = "URL must start with http:// or https://"
                return
            }
        }

        app.configRepository.saveConfig(cfg)

        // Update scheduling
        if (cfg.schedulingEnabled && cfg.isConfigured) {
            app.schedulerManager.schedulePeriodicJob(cfg)
        } else {
            app.schedulerManager.cancelJob()
        }

        _saveMessage.value = "Configuration saved"
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun getInstanceId(): String = app.instanceIdentity.installationId

    fun resetInstanceId(): String {
        val newId = app.instanceIdentity.rotateIdentity()
        return newId
    }
}
