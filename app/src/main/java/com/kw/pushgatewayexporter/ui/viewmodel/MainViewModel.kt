package com.kw.pushgatewayexporter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kw.pushgatewayexporter.PushgatewayExporterApp
import com.kw.pushgatewayexporter.collector.CollectorRegistry
import com.kw.pushgatewayexporter.model.MetricFamily
import com.kw.pushgatewayexporter.model.MetricSample
import com.kw.pushgatewayexporter.model.MetricType
import com.kw.pushgatewayexporter.model.PushResult
import com.kw.pushgatewayexporter.serializer.PrometheusSerializer
import com.kw.pushgatewayexporter.tls.TlsErrorHelper
import com.kw.pushgatewayexporter.transport.PushgatewayClient
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isConfigured: Boolean = false,
    val pushgatewayUrl: String = "",
    val jobName: String = "",
    val instanceId: String = "",
    val schedulingEnabled: Boolean = false,
    val pushIntervalMinutes: Int = 15,
    val isJobScheduled: Boolean = false,
    val dryRunMode: Boolean = false,
    val lastPushResult: PushResult? = null,
    val lastSuccessTime: Long = -1,
    val lastFailureTime: Long = -1,
    val pushAttemptsTotal: Long = 0,
    val pushFailuresTotal: Long = 0,
    val isPushing: Boolean = false,
    val previewPayload: String? = null,
    val previewUrl: String? = null,
    val previewMetricsCount: Int = 0,
    val previewPayloadSize: Int = 0,
    val hasTlsTrustAnchorError: Boolean = false,
    val insecureTlsEnabled: Boolean = false,
    val tlsFixInProgress: Boolean = false,
    val tlsFixMessage: String? = null,
    val pendingCertInstallIntent: Intent? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PushgatewayExporterApp
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val config = app.configRepository.getConfig()
        val lastResult = app.configRepository.getLastPushResult()
        val hasTlsError = lastResult != null && !lastResult.success &&
            TlsErrorHelper.isTrustAnchorError(lastResult.errorMessage)
        _uiState.value = _uiState.value.copy(
            isConfigured = config.isConfigured,
            pushgatewayUrl = config.pushgatewayUrl,
            jobName = config.jobName,
            instanceId = app.instanceIdentity.installationId,
            schedulingEnabled = config.schedulingEnabled,
            pushIntervalMinutes = config.pushIntervalMinutes,
            isJobScheduled = app.schedulerManager.isJobScheduled(),
            dryRunMode = config.dryRunMode,
            lastPushResult = lastResult,
            lastSuccessTime = app.configRepository.getLastSuccessTime(),
            lastFailureTime = app.configRepository.getLastFailureTime(),
            pushAttemptsTotal = app.configRepository.getPushAttemptsTotal(),
            pushFailuresTotal = app.configRepository.getPushFailuresTotal(),
            hasTlsTrustAnchorError = hasTlsError,
            insecureTlsEnabled = config.insecureTls
        )
    }

    /** Flip the active config's insecureTls flag to true and persist. */
    fun enableInsecureTls() {
        val config = app.configRepository.getConfig()
        app.configRepository.saveConfig(config.copy(insecureTls = true))
        refreshState()
    }

    /**
     * Download ISRG Root X1, verify fingerprint, and prepare the install intent.
     * The UI observes [MainUiState.pendingCertInstallIntent] and launches it.
     */
    fun prepareLetsEncryptCertInstall() {
        if (_uiState.value.tlsFixInProgress) return
        _uiState.value = _uiState.value.copy(
            tlsFixInProgress = true,
            tlsFixMessage = "Downloading ISRG Root X1…",
            pendingCertInstallIntent = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = TlsErrorHelper.downloadIsrgRootX1()
                val intent = TlsErrorHelper.buildInstallIntent(bytes)
                _uiState.value = _uiState.value.copy(
                    tlsFixInProgress = false,
                    tlsFixMessage = null,
                    pendingCertInstallIntent = intent
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tlsFixInProgress = false,
                    tlsFixMessage = "Failed: ${e.message}",
                    pendingCertInstallIntent = null
                )
            }
        }
    }

    fun consumePendingCertInstallIntent() {
        _uiState.value = _uiState.value.copy(pendingCertInstallIntent = null)
    }

    fun clearTlsFixMessage() {
        _uiState.value = _uiState.value.copy(tlsFixMessage = null)
    }

    fun pushNow() {
        if (_uiState.value.isPushing) return
        _uiState.value = _uiState.value.copy(isPushing = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = app.configRepository.getConfig()
                val instanceId = app.instanceIdentity.installationId

                val collectStart = System.currentTimeMillis()
                val registry = CollectorRegistry(getApplication(), config)
                val (families, errors) = registry.collectAll()
                val collectDuration = (System.currentTimeMillis() - collectStart) / 1000.0

                val operationalFamilies = buildOperationalMetrics(collectDuration, errors, families.size)
                val allFamilies = families + operationalFamilies
                val payload = PrometheusSerializer.serialize(allFamilies)

                if (config.dryRunMode) {
                    val result = PushResult(
                        success = true,
                        httpStatusCode = 0,
                        errorMessage = "Dry run - not pushed",
                        durationMillis = System.currentTimeMillis() - collectStart,
                        payloadSizeBytes = payload.toByteArray(Charsets.UTF_8).size,
                        metricsCount = allFamilies.sumOf { it.samples.size }
                    )
                    app.configRepository.saveLastPushResult(result)
                } else {
                    val client = PushgatewayClient(config)
                    val result = client.push(payload, instanceId)
                    app.configRepository.saveLastPushResult(result)
                }

            } catch (e: Exception) {
                val result = PushResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error"
                )
                app.configRepository.saveLastPushResult(result)
            } finally {
                _uiState.value = _uiState.value.copy(isPushing = false)
                refreshState()
            }
        }
    }

    fun generatePreview() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = app.configRepository.getConfig()
                val instanceId = app.instanceIdentity.installationId
                val registry = CollectorRegistry(getApplication(), config)
                val (families, _) = registry.collectAll()
                val payload = PrometheusSerializer.serialize(families)
                val client = PushgatewayClient(config)
                val url = if (config.isConfigured) client.buildUrl(instanceId) else "<not configured>"

                _uiState.value = _uiState.value.copy(
                    previewPayload = payload,
                    previewUrl = url,
                    previewMetricsCount = families.sumOf { it.samples.size },
                    previewPayloadSize = payload.toByteArray(Charsets.UTF_8).size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    previewPayload = "Error generating preview: ${e.message}",
                    previewUrl = null,
                    previewMetricsCount = 0,
                    previewPayloadSize = 0
                )
            }
        }
    }

    private fun buildOperationalMetrics(
        collectDuration: Double,
        errors: List<String>,
        familyCount: Int
    ): List<MetricFamily> {
        val families = mutableListOf<MetricFamily>()

        families.add(MetricFamily(
            name = "android_exporter_collect_duration_seconds",
            help = "Time spent collecting all metrics in seconds",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample("android_exporter_collect_duration_seconds", value = collectDuration))
        ))

        families.add(MetricFamily(
            name = "android_exporter_metrics_count",
            help = "Number of metric families in this export",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample("android_exporter_metrics_count", value = familyCount.toDouble()))
        ))

        families.add(MetricFamily(
            name = "android_exporter_push_attempts_total",
            help = "Total number of push attempts since app install",
            type = MetricType.COUNTER,
            samples = listOf(MetricSample(
                "android_exporter_push_attempts_total",
                value = app.configRepository.getPushAttemptsTotal().toDouble()
            ))
        ))

        families.add(MetricFamily(
            name = "android_exporter_push_failures_total",
            help = "Total number of failed push attempts since app install",
            type = MetricType.COUNTER,
            samples = listOf(MetricSample(
                "android_exporter_push_failures_total",
                value = app.configRepository.getPushFailuresTotal().toDouble()
            ))
        ))

        families.add(MetricFamily(
            name = "android_exporter_job_scheduled",
            help = "Whether the periodic push job is currently scheduled",
            type = MetricType.GAUGE,
            samples = listOf(MetricSample(
                "android_exporter_job_scheduled",
                value = if (app.configRepository.getConfig().schedulingEnabled) 1.0 else 0.0
            ))
        ))

        return families
    }
}
