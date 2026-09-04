package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.ChatSession
import com.example.data.DashboardStatusDto
import com.example.data.HermesApiClient
import com.example.data.HermesSettings
import com.example.data.MessageSender
import com.example.data.MessageStatus
import com.example.data.PreferencesManager
import com.example.data.ProfileDto
import com.example.data.ServerHealth
import com.example.data.SessionSummary
import com.example.data.AnalyticsResponse
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

    // Pensamento ao vivo do modelo + ferramentas em uso
    private val _liveThinking = MutableStateFlow<String?>(null)
    val liveThinking: StateFlow<String?> = _liveThinking.asStateFlow()

    private val _liveToolUse = MutableStateFlow<String?>(null)
    val liveToolUse: StateFlow<String?> = _liveToolUse.asStateFlow()

    // Pensamento final (após resposta completa)
    private val _finalThinking = MutableStateFlow<String?>(null)
    val finalThinking: StateFlow<String?> = _finalThinking.asStateFlow()

    private val _serverHealth = MutableStateFlow<ServerHealth?>(null)
    val serverHealth: StateFlow<ServerHealth?> = _serverHealth.asStateFlow()

    private val _dashStatus = MutableStateFlow<DashboardStatusDto?>(null)
    val dashStatus: StateFlow<DashboardStatusDto?> = _dashStatus.asStateFlow()

    private val _dashSessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val dashSessions: StateFlow<List<SessionSummary>> = _dashSessions.asStateFlow()

    private val _dashAnalytics = MutableStateFlow<AnalyticsResponse?>(null)
    val dashAnalytics: StateFlow<AnalyticsResponse?> = _dashAnalytics.asStateFlow()

    private val _dashLoading = MutableStateFlow(false)
    val dashLoading: StateFlow<Boolean> = _dashLoading.asStateFlow()

    private val _dashError = MutableStateFlow<String?>(null)
    val dashError: StateFlow<String?> = _dashError.asStateFlow()

    private val _isSPenHovering = MutableStateFlow(false)
    val isSPenHovering: StateFlow<Boolean> = _isSPenHovering.asStateFlow()

    private val _agentName = MutableStateFlow<String?>("Agent T")
    val agentName: StateFlow<String?> = _agentName.asStateFlow()

    private val _agentModel = MutableStateFlow<String?>("")
    val agentModel: StateFlow<String?> = _agentModel.asStateFlow()

    private val _availableProfiles = MutableStateFlow<List<ProfileDto>>(emptyList())
    val availableProfiles: StateFlow<List<ProfileDto>> = _availableProfiles.asStateFlow()

    private val _pendingQueue = MutableStateFlow<List<String>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // Incrementa sempre que um comando /profile pede o dialog de seleção
    private val _profileDialogEvent = MutableStateFlow(0)
    val profileDialogEvent: StateFlow<Int> = _profileDialogEvent.asStateFlow()

    fun consumeProfileDialogEvent() {
        _profileDialogEvent.value = 0
    }

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
                    if (!prof.model.isNullOrBlank()) {
                        _agentModel.value = prof.model
                    }
                }
                val allProfs = apiClient.fetchAllProfiles(settings.value.serverUrl)
                if (allProfs.isNotEmpty()) {
                    _availableProfiles.value = allProfs
                    val active = allProfs.firstOrNull { it.active }
                    if (active != null) {
                        _agentName.value = active.name
                        if (!active.model.isNullOrBlank()) {
                            _agentModel.value = active.model
                        }
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

    /** Puxa a telemetria do dashboard (status, sessões, analytics) pela ponte 9120. */
    fun refreshDashboardTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_dashLoading.value) return@launch
            _dashLoading.value = true
            _dashError.value = null
            val url = settings.value.serverUrl
            try {
                _dashStatus.value = apiClient.fetchDashboardStatus(url)
                _dashSessions.value = apiClient.fetchDashboardSessions(url)
                _dashAnalytics.value = apiClient.fetchDashboardAnalytics(url)
                if (_dashStatus.value == null && _dashSessions.value.isEmpty() && _dashAnalytics.value == null) {
                    _dashError.value = "Ponte/dashboard indisponível — verifica se a ponte 9120 e o dashboard 9119 estão a correr."
                }
            } catch (e: Exception) {
                _dashError.value = e.localizedMessage ?: "Erro a ler telemetria do dashboard"
            } finally {
                _dashLoading.value = false
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

    /**
     * Comandos locais (estilo Hermes): resolvidos na app, 0 tokens.
     * Devolve true se o texto era um comando tratado localmente.
     */
    private fun handleLocalCommand(text: String): Boolean {
        val cmd = text.trim().lowercase()
        return when {
            cmd == "/new" -> {
                createNewSession()
                true
            }
            cmd == "/clear" -> {
                clearChat()
                true
            }
            cmd == "/stop" -> {
                stopGeneration()
                true
            }
            cmd == "/profile" -> {
                _profileDialogEvent.value += 1
                true
            }
            else -> false
        }
    }

    /** Aborta a geração em curso, limpa a fila e marca a mensagem pendente como parada. */
    fun stopGeneration() {
        generateJob?.cancel()
        generateJob = null
        _isGenerating.value = false
        _liveThinking.value = null
        _liveToolUse.value = null
        _finalThinking.value = null
        _pendingQueue.value = emptyList()
        _pendingCount.value = 0
        viewModelScope.launch {
            val pending = messages.value.filter {
                it.sessionId == _currentSessionId.value &&
                    (it.status == MessageStatus.SENDING || it.status == MessageStatus.STREAMING)
            }
            pending.forEach { msg ->
                repository.updateMessage(
                    msg.copy(
                        text = if (msg.text.isBlank()) "(geração parada)" else msg.text,
                        status = MessageStatus.ERROR,
                        errorDetails = "Parada pelo utilizador (/stop)"
                    )
                )
            }
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val textToSend = (customPrompt ?: _inputText.value).trim()
        if (textToSend.isBlank()) return

        // Comandos locais correm sempre, mesmo durante geração (exceto os que vão para a fila)
        if (handleLocalCommand(textToSend)) {
            if (customPrompt == null) _inputText.value = ""
            return
        }

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

        val initialModel = _agentModel.value?.takeIf { it.isNotBlank() }
            ?: settings.value.modelName.takeIf { it.isNotBlank() && it != "hermes-agent" }
            ?: settings.value.modelName

        val hermesMsgId = UUID.randomUUID().toString()
        val pendingHermesMsg = ChatMessage(
            id = hermesMsgId,
            sessionId = currentSId,
            text = "",
            sender = MessageSender.HERMES,
            timestamp = System.currentTimeMillis() + 1,
            status = MessageStatus.SENDING,
            modelName = initialModel
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

            // Streaming callback: atualiza texto e raciocínio em tempo real no Room e StateFlow
            var accumulatedText = ""
            var currentReasoning = ""
            _liveThinking.value = null
            _liveToolUse.value = null
            _finalThinking.value = null

            apiClient.onStreamChunk = { chunk ->
                accumulatedText += chunk
                val currentChunkText = accumulatedText
                val reasoningSnapshot = currentReasoning
                viewModelScope.launch {
                    val updated = pendingHermesMsg.copy(
                        text = currentChunkText,
                        status = MessageStatus.STREAMING,
                        reasoning = reasoningSnapshot.takeIf { it.isNotBlank() }
                    )
                    repository.updateMessage(updated)
                }
            }

            apiClient.onReasoningChunk = { reasoningText ->
                currentReasoning = reasoningText
                _liveThinking.value = reasoningText
                val textSnapshot = accumulatedText
                viewModelScope.launch {
                    val updated = pendingHermesMsg.copy(
                        text = textSnapshot,
                        status = MessageStatus.STREAMING,
                        reasoning = reasoningText.takeIf { it.isNotBlank() }
                    )
                    repository.updateMessage(updated)
                }
            }

            apiClient.onToolUse = { toolsText ->
                viewModelScope.launch { _liveToolUse.value = toolsText }
            }

            val modelToUse = _agentModel.value?.takeIf { it.isNotBlank() } ?: currentSettings.modelName

            val result = apiClient.sendMessage(
                baseUrl = currentSettings.serverUrl,
                history = history,
                userPrompt = textToSend,
                model = modelToUse,
                systemPrompt = currentSettings.systemPrompt,
                temperature = currentSettings.temperature,
                maxTokens = currentSettings.maxTokens,
                customEndpoint = currentSettings.customEndpoint
            )

            _isGenerating.value = false

            if (result.isSuccess) {
                val sendResult = result.getOrThrow()
                val reply = sendResult.reply
                val latency = sendResult.latencyMs
                val reasoning = sendResult.reasoning
                val returnedModel = sendResult.modelName

                _liveThinking.value = null
                _liveToolUse.value = null
                _finalThinking.value = null

                val rawText = if (accumulatedText.isNotBlank()) accumulatedText else reply
                val extraction = com.example.ui.components.extractThoughtAndResponse(rawText)
                val cleanText = extraction.cleanResponse
                val finalReasoning = reasoning?.takeIf { it.isNotBlank() }
                    ?: extraction.thought?.takeIf { it.isNotBlank() }
                    ?: currentReasoning.takeIf { it.isNotBlank() }

                val finalModel = returnedModel?.takeIf { it.isNotBlank() }
                    ?: pendingHermesMsg.modelName.takeIf { it.isNotBlank() && it != "hermes-agent" }
                    ?: _agentModel.value?.takeIf { it.isNotBlank() }
                    ?: currentSettings.modelName

                if (!returnedModel.isNullOrBlank()) {
                    _agentModel.value = returnedModel
                }

                val completedMsg = pendingHermesMsg.copy(
                    text = cleanText,
                    status = MessageStatus.SENT,
                    latencyMs = latency,
                    reasoning = finalReasoning,
                    modelName = finalModel
                )
                repository.updateMessage(completedMsg)

                // Disponibilizar pensamento final após a resposta
                if (!finalReasoning.isNullOrBlank()) {
                    _finalThinking.value = finalReasoning
                }

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
                _liveThinking.value = null
                _liveToolUse.value = null
                _finalThinking.value = null
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