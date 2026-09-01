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

    private var generateJob: kotlinx.coroutines.Job? = null

    private val _serverHealth = MutableStateFlow<ServerHealth?>(null)
    val serverHealth: StateFlow<ServerHealth?> = _serverHealth.asStateFlow()

    private val _isSPenHovering = MutableStateFlow(false)
    val isSPenHovering: StateFlow<Boolean> = _isSPenHovering.asStateFlow()

    private val _agentName = MutableStateFlow<String?>(null)
    val agentName: StateFlow<String?> = _agentName.asStateFlow()

    private val _pendingQueue = MutableStateFlow<List<String>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        checkServerHealth()
        refreshAgentName()
        refreshRealModelName()
        // Consome a fila de mensagens pendentes à medida que a geração termina
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
            val name = apiClient.fetchProfile(settings.value.serverUrl)
            if (!name.isNullOrBlank()) {
                _agentName.value = name
            }
        }
    }

    /**
     * Se o modelo guardado é o default "hermes-agent" (placeholder), substitui-o
     * pelo primeiro modelo real devolvido pelo servidor (GET /v1/models).
     */
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

    fun syncRemoteMessages() {
        // Desativado: o histórico é apenas local (Room DB).
        // O Hermes guarda o contexto da conversa no perfil.
    }

    /**
     * Comandos locais (estilo Hermes) — processados na app, sem gastar tokens:
     *   /new            -> limpa histórico + nova sessão
     *   /clear          -> limpa histórico
     *   /model          -> lista modelos disponíveis (GET /v1/models)
     *   /model <nome>   -> troca o modelo activo
     *   /help           -> lista de comandos
     * Devolve true se foi comando (e já foi tratado).
     */
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
            "/help" -> {
                showToast("/new /clear /model [nome] /stop /help")
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
            "/model" -> {
                if (parts.size >= 2) {
                    val newModel = parts.drop(1).joinToString(" ").trim()
                    viewModelScope.launch { preferencesManager.updateModelName(newModel) }
                    showToast("Modelo: $newModel ✓")
                } else {
                    viewModelScope.launch {
                        val models = apiClient.fetchModels(settings.value.serverUrl)
                        val current = settings.value.modelName
                        showToast("Modelo: $current | Disp.: " + models.filter { it != current }.joinToString(", "))
                    }
                }
                return true
            }
            else -> {
                showToast("Comando desconhecido: ${parts[0]} (usa /help)")
                return true
            }
        }
    }

    /** Aviso de comando via Toast — efémero, não fica no histórico. */
    private fun showToast(text: String) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun sendMessage(overrideText: String? = null) {
        val textToSend = (overrideText ?: _inputText.value).trim()
        if (textToSend.isBlank()) return

        // Comandos tipo Hermes: processados localmente, 0 tokens
        if (handleCommand(textToSend)) {
            _inputText.value = ""
            return
        }

        // Se já está a gerar, mete em fila (permite enviar durante o "a pensar")
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

            // Callback de streaming: actualiza a mensagem em tempo real
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
