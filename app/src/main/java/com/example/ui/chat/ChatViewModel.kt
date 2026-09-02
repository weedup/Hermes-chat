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
import com.example.data.ProfileDto
import com.example.data.ServerHealth
import com.example.data.local.HermesChatDatabase
import com.example.util.HapticHelper
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = HermesChatDatabase.getInstance(application)
    private val repository = ChatRepository(database.chatMessageDao())
    private val preferencesManager = PreferencesManager(application)
    private val apiClient = HermesApiClient()
    val hapticHelper = HapticHelper(application)

    val settings: StateFlow<HermesSettings> = preferencesManager.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HermesSettings()
    )

    val messages: StateFlow<List<ChatMessage>> = repository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _serverHealth = MutableStateFlow<ServerHealth?>(null)
    val serverHealth: StateFlow<ServerHealth?> = _serverHealth.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isSPenHovering = MutableStateFlow(false)
    val isSPenHovering: StateFlow<Boolean> = _isSPenHovering.asStateFlow()

    private val _agentName = MutableStateFlow<String?>(null)
    val agentName: StateFlow<String?> = _agentName.asStateFlow()

    private val _availableProfiles = MutableStateFlow<List<ProfileDto>>(emptyList())
    val availableProfiles: StateFlow<List<ProfileDto>> = _availableProfiles.asStateFlow()

    private var generateJob: Job? = null

    private val _pendingQueue = MutableStateFlow<List<String>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        checkServerHealth()
        refreshAgentName()
        refreshRealModelName()
        viewModelScope.launch {
            _isGenerating.collect { generating ->
                if (!generating && _pendingQueue.value.isNotEmpty()) {
                    val next = _pendingQueue.value.first()
                    _pendingQueue.value = _pendingQueue.value.drop(1)
                    _pendingCount.value = _pendingQueue.value.size
                    doSend(next)
                }
            }
        }
    }

    fun refreshAgentName() {
        viewModelScope.launch {
            val res = apiClient.fetchProfilesList(settings.value.serverUrl)
            if (res != null && res.profiles.isNotEmpty()) {
                _availableProfiles.value = res.profiles
                val activeProf = res.profiles.firstOrNull { it.active || it.id == res.current }
                    ?: res.profiles.first()
                _agentName.value = activeProf.name
            } else {
                val name = apiClient.fetchProfile(settings.value.serverUrl)
                if (!name.isNullOrBlank()) {
                    _agentName.value = name
                }
            }
        }
    }

    fun refreshRealModelName() {
        viewModelScope.launch {
            val current = settings.value.modelName
            if (current.isNotBlank() && current != PreferencesManager.DEFAULT_MODEL) return@launch
            val models = apiClient.fetchModels(settings.value.serverUrl)
            val real = models.firstOrNull { it.isNotBlank() }
            if (real != null && real != current) {
                preferencesManager.updateModelName(real)
            }
        }
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
        }
    }

    fun switchProfile(profileId: String, profileName: String) {
        viewModelScope.launch {
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
            }
            val success = apiClient.selectProfile(settings.value.serverUrl, profileId)
            _agentName.value = profileName
            refreshAgentName()
            
            val noticeMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "✨ Perfil alterado para **$profileName** ($profileId)",
                sender = MessageSender.SYSTEM,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(noticeMsg)
            showToast("Perfil ativo: $profileName")
        }
    }

    private fun handleCommand(cmdRaw: String): Boolean {
        val cmd = cmdRaw.trim()
        if (!cmd.startsWith("/")) return false
        val parts = cmd.split("\\s+".toRegex())
        when (parts[0].lowercase()) {
            "/new", "/clear" -> {
                viewModelScope.launch {
                    if (settings.value.hapticEnabled) {
                        hapticHelper.trigger(HapticHelper.HapticType.HEAVY_CLICK)
                    }
                    repository.clearHistory()
                }
                showToast(if (parts[0].lowercase() == "/new") "Nova sessão ✓ histórico limpo" else "Histórico limpo ✓")
                return true
            }
            "/stop" -> {
                val job = generateJob
                if (_isGenerating.value && job != null && job.isActive) {
                    job.cancel()
                    _isGenerating.value = false
                    if (settings.value.hapticEnabled) {
                        hapticHelper.trigger(HapticHelper.HapticType.HEAVY_CLICK)
                    }
                    showToast("Geração abortada ✓")
                } else {
                    showToast("Nada a abortar")
                }
                return true
            }
            "/profile" -> {
                if (parts.size >= 2) {
                    val target = parts[1].lowercase()
                    val targetName = if (target == "tara") "Tara" else if (target == "default") "Agent T" else target.capitalize()
                    switchProfile(target, targetName)
                } else {
                    viewModelScope.launch {
                        if (settings.value.hapticEnabled) {
                            hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
                        }
                        val res = apiClient.fetchProfilesList(settings.value.serverUrl)
                        val profs = res?.profiles ?: listOf(
                            ProfileDto("default", "Agent T", true),
                            ProfileDto("tara", "Tara", false)
                        )
                        _availableProfiles.value = profs
                        val currentActive = profs.firstOrNull { it.active || it.id == res?.current }?.name ?: _agentName.value ?: "Hermes"

                        val listText = buildString {
                            appendLine("👤 **Escolhe o Perfil Hermes:**")
                            appendLine("Ativo: **$currentActive**")
                            appendLine()
                            append("Toca numa das opções abaixo para alternar:")
                        }
                        val noticeMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = listText,
                            sender = MessageSender.SYSTEM,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertMessage(noticeMsg)
                    }
                }
                return true
            }
            "/help" -> {
                showToast("Comandos: /new, /profile, /stop")
                return true
            }
            else -> {
                showToast("Comando desconhecido: ${parts[0]} (usa /new, /profile, /stop)")
                return true
            }
        }
    }

    private fun showToast(text: String) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun sendMessage(overrideText: String? = null) {
        val textToSend = (overrideText ?: _inputText.value).trim()
        if (textToSend.isBlank()) return

        if (handleCommand(textToSend)) {
            _inputText.value = ""
            return
        }

        if (_isGenerating.value) {
            _pendingQueue.value = _pendingQueue.value + textToSend
            _pendingCount.value = _pendingQueue.value.size
            _inputText.value = ""
            if (settings.value.hapticEnabled) {
                hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK)
            }
            return
        }

        _inputText.value = ""
        doSend(textToSend)
    }

    private fun doSend(textToSend: String) {
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
            generateJob = coroutineContext[kotlinx.coroutines.Job]

            val currentSettings = settings.value
            val history = messages.value.filter { it.status == MessageStatus.SENT }

            apiClient.onStreamChunk = { chunk ->
                viewModelScope.launch {
                    val currentText = pendingHermesMsg.text
                    val newText = if (currentText == "...") chunk else currentText + chunk
                    val updated = pendingHermesMsg.copy(
                        text = newText,
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
                    text = reply,
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

    fun deleteMessage(msgId: String) {
        viewModelScope.launch {
            repository.deleteMessage(msgId)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
