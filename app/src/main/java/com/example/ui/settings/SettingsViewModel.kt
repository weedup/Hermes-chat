package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HermesApiClient
import com.example.data.HermesSettings
import com.example.data.PreferencesManager
import com.example.data.ServerHealth
import com.example.util.HapticHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val apiClient = HermesApiClient()
    val hapticHelper = HapticHelper(application)

    val settings: StateFlow<HermesSettings> = preferencesManager.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HermesSettings()
        )

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testResult = MutableStateFlow<ServerHealth?>(null)
    val testResult: StateFlow<ServerHealth?> = _testResult.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isProbingEndpoints = MutableStateFlow(false)
    val isProbingEndpoints: StateFlow<Boolean> = _isProbingEndpoints.asStateFlow()

    private val _endpointProbes = MutableStateFlow<List<com.example.data.EndpointProbeResult>>(emptyList())
    val endpointProbes: StateFlow<List<com.example.data.EndpointProbeResult>> = _endpointProbes.asStateFlow()

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.updateServerUrl(url)
            _testResult.value = null
            _endpointProbes.value = emptyList()
        }
    }

    fun updateCustomEndpoint(endpoint: String) {
        viewModelScope.launch {
            preferencesManager.updateCustomEndpoint(endpoint)
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.CLICK)
            }
        }
    }

    fun probeServerEndpoints() {
        viewModelScope.launch {
            _isProbingEndpoints.value = true
            val results = apiClient.probeEndpoints(settings.value.serverUrl)
            _endpointProbes.value = results
            _isProbingEndpoints.value = false
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
            }
        }
    }

    fun updateModelName(model: String) {
        viewModelScope.launch {
            preferencesManager.updateModelName(model)
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            preferencesManager.updateSystemPrompt(prompt)
        }
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch {
            preferencesManager.updateTemperature(temp)
        }
    }

    fun updateMaxTokens(tokens: Int) {
        viewModelScope.launch {
            preferencesManager.updateMaxTokens(tokens)
        }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateHapticEnabled(enabled)
            if (enabled) {
                hapticHelper.trigger(HapticHelper.HapticType.CLICK)
            }
        }
    }

    fun updateSPenModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateSPenModeEnabled(enabled)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            preferencesManager.resetDefaults()
            _testResult.value = null
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.HEAVY_CLICK)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testResult.value = null
            val currentUrl = settings.value.serverUrl
            val result = apiClient.checkHealth(currentUrl)
            _testResult.value = result
            _isTestingConnection.value = false

            if (result.isReachable) {
                if (settings.value.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
                }
                // Try fetching models
                val models = apiClient.fetchModels(currentUrl)
                _availableModels.value = models
            } else {
                if (settings.value.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.ERROR)
                }
            }
        }
    }
}
