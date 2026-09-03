package com.example.data

import kotlinx.serialization.Serializable

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

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val sessionId: String = "default",
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val latencyMs: Long = 0L,
    val modelName: String = "hermes-agent",
    val errorDetails: String? = null
)

data class ServerHealth(
    val isReachable: Boolean = false,
    val statusCode: Int = 0,
    val latencyMs: Long = 0L,
    val serverHeader: String = "",
    val dashboardAvailable: Boolean = false,
    val errorMessage: String? = null
)

data class HermesSettings(
    val serverUrl: String = "http://127.0.0.1:9120",
    val customEndpoint: String = "",
    val modelName: String = "hermes-agent",
    val systemPrompt: String = "Tu és o Hermes, um modelo de inteligência artificial de elite a correr localmente no dispositivo via Termux.",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val hapticEnabled: Boolean = true,
    val sPenModeEnabled: Boolean = true,
    val uiDensityScale: Float = 1.0f
)

data class EndpointProbeResult(
    val path: String,
    val method: String,
    val statusCode: Int?,
    val isAvailable: Boolean,
    val latencyMs: Long,
    val sampleResponse: String? = null,
    val error: String? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val active: Boolean
)

@Serializable
data class ProfilesResponse(
    val current: String,
    val profiles: List<ProfileDto>
)
