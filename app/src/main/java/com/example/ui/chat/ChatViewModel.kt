package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.HermesApiClient
import com.example.data.HermesSettings
import com.example.data.MessageSender
import com.example.data.MessageStatus
import com.example.data.PreferencesManager
import com.example.data.ServerHealth
import com.example.util.HapticHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val preferencesManager = PreferencesManager(application)
    private val apiClient = HermesApiClient()
    val hapticHelper = HapticHelper(application)

    val settings: StateFlow<HermesSettings> = preferencesManager.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HermesSettings()
        )

    val messages: StateFlow<List<ChatMessage>> = repository.messages
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _serverHealth = MutableStateFlow<ServerHealth?>(null)
    val serverHealth: StateFlow<ServerHealth?> = _serverHealth.asStateFlow()

    private val _isSPenHovering = MutableStateFlow(false)
    val isSPenHovering: StateFlow<Boolean> = _isSPenHovering.asStateFlow()

    init {
        checkServerHealth()
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun setSPenHover(hovering: Boolean) {
        _isSPenHovering.value = hovering
        if (hovering && settings.value.hapticEnabled) {
            hapticHelper.trigger(HapticHelper.HapticType.SPEN_HOVER)
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            val url = settings.value.serverUrl
            val health = apiClient.checkHealth(url)
            _serverHealth.value = health
            if (health.isReachable) {
                syncRemoteMessages()
            }
        }
    }

    fun syncRemoteMessages() {
        viewModelScope.launch {
            val url = settings.value.serverUrl
            val result = apiClient.fetchMessages(url)
            if (result.isSuccess) {
                val remoteMessages = result.getOrNull()
                if (!remoteMessages.isNullOrEmpty()) {
                    for (msg in remoteMessages) {
                        repository.insertMessage(msg)
                    }
                }
            }
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val textToSend = (overrideText ?: _inputText.value).trim()
        if (textToSend.isBlank() || _isGenerating.value) return

        _inputText.value = ""
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            text = textToSend,
            sender = MessageSender.USER,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT
        )

        val hermesMsgId = UUID.randomUUID().toString()
        val pendingHermesMsg = ChatMessage(
            id = hermesMsgId,
            text = "...",
            sender = MessageSender.HERMES,
            timestamp = System.currentTimeMillis() + 1,
            status = MessageStatus.SENDING,
            modelName = settings.value.modelName
        )

        viewModelScope.launch {
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.CLICK)
            }

            repository.insertMessage(userMsg)
            repository.insertMessage(pendingHermesMsg)
            _isGenerating.value = true

            val currentSettings = settings.value
            val history = messages.value.filter { it.status == MessageStatus.SENT }

            val result = apiClient.sendMessage(
                baseUrl = currentSettings.serverUrl,
                history = history,
                userPrompt = textToSend,
                model = currentSettings.modelName,
                systemPrompt = currentSettings.systemPrompt,
                temperature = currentSettings.temperature,
                maxTokens = currentSettings.maxTokens
            )

            _isGenerating.value = false

            if (result.isSuccess) {
                val (reply, latency) = result.getOrThrow()
                val completedMsg = pendingHermesMsg.copy(
                    text = reply,
                    status = MessageStatus.SENT,
                    latencyMs = latency
                )
                repository.updateMessage(completedMsg)
                if (currentSettings.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
                }
                // Refresh health state
                _serverHealth.value = ServerHealth(
                    isReachable = true,
                    statusCode = 200,
                    latencyMs = latency,
                    serverHeader = "Hermes-Server"
                )
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = pendingHermesMsg.copy(
                    text = "Erro na resposta do Hermes: ${error?.message ?: "Falha de ligação"}",
                    status = MessageStatus.ERROR,
                    errorDetails = error?.localizedMessage
                )
                repository.updateMessage(errorMsg)
                if (currentSettings.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.ERROR)
                }
                _serverHealth.value = ServerHealth(
                    isReachable = false,
                    statusCode = 0,
                    errorMessage = error?.message
                )
            }
        }
    }

    fun retryMessage(failedMsgId: String) {
        val allMsgs = messages.value
        val failedIdx = allMsgs.indexOfFirst { it.id == failedMsgId }
        if (failedIdx > 0) {
            val prevUserMsg = allMsgs[failedIdx - 1]
            if (prevUserMsg.sender == MessageSender.USER) {
                viewModelScope.launch {
                    repository.deleteMessage(failedMsgId)
                    sendMessage(prevUserMsg.text)
                }
            }
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.HEAVY_CLICK)
            }
            repository.clearHistory()
        }
    }
}
