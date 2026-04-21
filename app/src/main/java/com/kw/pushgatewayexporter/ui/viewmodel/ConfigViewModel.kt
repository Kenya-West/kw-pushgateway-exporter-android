package com.kw.pushgatewayexporter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PushgatewayExporterApp
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        _config.value = app.configRepository.getConfig()
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
