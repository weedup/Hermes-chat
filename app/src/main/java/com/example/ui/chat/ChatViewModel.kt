package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.ChatSession
import com.example.data.HermesApiClient
import com.example.data.HermesSettings
import com.example.data.MessageSender
import com.example.data.MessageStatus
import com.example.data.PreferencesManager
import com.example.data.ProfileDto
import com.example.data.ServerHealth
import com.example.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HermesSettings()
        )

    val sessions: StateFlow<List<ChatSession>> = repository.sessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSessionId = MutableStateFlow("default")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            repository.getMessagesForSession(sessionId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _serverHealth = MutableStateFlow<ServerHealth?>(null)
    val serverHealth: StateFlow<ServerHealth?> = _serverHealth.asStateFlow()

    private val _isSPenHovering = MutableStateFlow(false)
    val isSPenHovering: StateFlow<Boolean> = _isSPenHovering.asStateFlow()

    private val _agentName = MutableStateFlow<String?>("Agent T")
    val agentName: StateFlow<String?> = _agentName.asStateFlow()

    private val _availableProfiles = MutableStateFlow<List<ProfileDto>>(emptyList())
    val availableProfiles: StateFlow<List<ProfileDto>> = _availableProfiles.asStateFlow()

    private val _pendingQueue = MutableStateFlow<List<String>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSessionExists("default", "Chat Principal")
        }
        checkServerHealth()
        refreshProfileInfo()
        observeQueue()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            _isGenerating.collect { generating ->
                if (!generating && _pendingQueue.value.isNotEmpty()) {
                    val next = _pendingQueue.value.first()
                    _pendingQueue.value = _pendingQueue.value.drop(1)
                    _pendingCount.value = _pendingQueue.value.size
                    delay(300)
                    doSend(next)
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun createNewSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            repository.ensureSessionExists(newId, "Novo Chat")
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = sessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    _currentSessionId.value = remaining.first().id
                } else {
                    repository.ensureSessionExists("default", "Chat Principal")
                    _currentSessionId.value = "default"
                }
            }
        }
    }

    fun refreshProfileInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prof = apiClient.fetchProfileInfo(settings.value.serverUrl)
                if (prof != null) {
                    _agentName.value = prof.name
                }
                val allProfs = apiClient.fetchAllProfiles(settings.value.serverUrl)
                if (allProfs.isNotEmpty()) {
                    _availableProfiles.value = allProfs
                    val active = allProfs.firstOrNull { it.active }
                    if (active != null) {
                        _agentName.value = active.name
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun switchProfile(profileId: String, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = apiClient.switchProfile(settings.value.serverUrl, profileId)
            if (ok) {
                _agentName.value = displayName
                refreshProfileInfo()
                if (settings.value.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
                }
            } else {
                if (settings.value.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.ERROR)
                }
            }
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            val currentUrl = settings.value.serverUrl
            val health = apiClient.checkHealth(currentUrl)
            _serverHealth.value = health
            if (health.isReachable) {
                refreshProfileInfo()
            }
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun setSPenHover(hovering: Boolean) {
        if (_isSPenHovering.value != hovering) {
            _isSPenHovering.value = hovering
            if (hovering && settings.value.hapticEnabled && settings.value.sPenModeEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
            }
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val textToSend = (customPrompt ?: _inputText.value).trim()
        if (textToSend.isBlank()) return

        if (_isGenerating.value) {
            _pendingQueue.value = _pendingQueue.value + textToSend
            _pendingCount.value = _pendingQueue.value.size
            if (customPrompt == null) _inputText.value = ""
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
            }
            return
        }

        if (customPrompt == null) _inputText.value = ""
        doSend(textToSend)
    }

    private fun doSend(textToSend: String) {
        val currentSId = _currentSessionId.value
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            sessionId = currentSId,
            text = textToSend,
            sender = MessageSender.USER,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT
        )

        val hermesMsgId = UUID.randomUUID().toString()
        val pendingHermesMsg = ChatMessage(
            id = hermesMsgId,
            sessionId = currentSId,
            text = "",
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

            // Atualiza título da sessão se for o primeiro prompt
            val currentMsgs = messages.value
            if (currentMsgs.isEmpty() || currentMsgs.size <= 2) {
                val titlePreview = if (textToSend.length > 25) textToSend.take(25) + "..." else textToSend
                repository.updateSessionTitle(currentSId, titlePreview)
            }

            _isGenerating.value = true
            generateJob = coroutineContext[Job]

            val currentSettings = settings.value
            val history = messages.value.filter { it.status == MessageStatus.SENT }

            // Streaming callback: atualiza a mensagem em tempo real no Room
            var accumulatedText = ""
            apiClient.onStreamChunk = { chunk ->
                accumulatedText += chunk
                val currentChunkText = accumulatedText
                viewModelScope.launch {
                    val updated = pendingHermesMsg.copy(
                        text = currentChunkText,
                        status = MessageStatus.STREAMING
                    )
                    repository.updateMessage(updated)
                }
            }

            val result = apiClient.sendMessage(
                baseUrl = currentSettings.serverUrl,
                history = history,
                userPrompt = textToSend,
                model = currentSettings.modelName,
                systemPrompt = currentSettings.systemPrompt,
                temperature = currentSettings.temperature,
                maxTokens = currentSettings.maxTokens,
                customEndpoint = currentSettings.customEndpoint
            )

            _isGenerating.value = false

            if (result.isSuccess) {
                val (reply, latency) = result.getOrThrow()
                val completedMsg = pendingHermesMsg.copy(
                    text = if (accumulatedText.isNotBlank()) accumulatedText else reply,
                    status = MessageStatus.SENT,
                    latencyMs = latency
                )
                repository.updateMessage(completedMsg)
                if (currentSettings.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
                }
                _serverHealth.value = ServerHealth(
                    isReachable = true,
                    statusCode = 200,
                    latencyMs = latency,
                    serverHeader = "Hermes-Server"
                )
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = pendingHermesMsg.copy(
                    text = if (accumulatedText.isNotBlank()) accumulatedText else "Erro na resposta do Hermes: ${error?.message ?: "Falha de ligação"}",
                    status = MessageStatus.ERROR,
                    errorDetails = error?.localizedMessage
                )
                repository.updateMessage(errorMsg)
                if (currentSettings.hapticEnabled) {
                    hapticHelper.trigger(HapticHelper.HapticType.ERROR)
                }
            }
        }
    }

    fun retryMessage(messageId: String) {
        val currentMsgs = messages.value
        val errorMsgIndex = currentMsgs.indexOfFirst { it.id == messageId }
        if (errorMsgIndex > 0) {
            val userMsg = currentMsgs[errorMsgIndex - 1]
            if (userMsg.sender == MessageSender.USER) {
                viewModelScope.launch {
                    repository.deleteMessage(messageId)
                    doSend(userMsg.text)
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory(_currentSessionId.value)
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.SUCCESS)
            }
        }
    }
}
