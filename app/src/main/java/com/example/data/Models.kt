package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val active: Boolean = false
)

@Serializable
data class ProfileListResponse(
    val current: String = "default",
    val profiles: List<ProfileDto> = emptyList()
)

enum class MessageSender {
    USER,
    HERMES,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    STREAMING,
    SENT,
    ERROR
}

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val latencyMs: Long = 0,
    val modelName: String = "",
    val errorDetails: String? = null
)

data class ServerHealth(
    val isReachable: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0,
    val serverHeader: String = "",
    val dashboardAvailable: Boolean = false,
    val errorMessage: String? = null,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)

data class HermesSettings(
    val serverUrl: String = "http://127.0.0.1:9120/",
    val customEndpoint: String = "AUTO",
    val modelName: String = "hermes-agent",
    val systemPrompt: String = "Tu és o Hermes, um assistente de IA avançado e prestável a correr localmente no Termux.",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val hapticEnabled: Boolean = true,
    val sPenModeEnabled: Boolean = true,
    val uiDensityScale: Float = 0.90f
)

data class EndpointProbeResult(
    val path: String,
    val method: String,
    val statusCode: Int,
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long
)

// OpenAI / Hermes Compatible DTOs
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048,
    val stream: Boolean = false
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiMessage? = null,
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

@Serializable
data class ModelsListResponse(
    val data: List<ModelItem> = emptyList()
)

@Serializable
data class ModelItem(
    val id: String
)
