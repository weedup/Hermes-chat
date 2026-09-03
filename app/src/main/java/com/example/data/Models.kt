package com.example.data

import kotlinx.serialization.SerialName
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
    val errorDetails: String? = null,
    val reasoning: String? = null
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
    val systemPrompt: String = "Tu és o Hermes, um modelo de inteligência artificial de elite a correr localmente no dispositivo via Termux. ANTES de cada resposta final, mostra o teu raciocínio passo a passo envolvido obrigatoriamente em <thinking>...</thinking>. O utilizador quer ver o teu pensamento em direto.",
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
    val isSuccess: Boolean,
    val message: String = "",
    val latencyMs: Long,
    val sampleResponse: String? = null,
    val error: String? = null
)

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

// OpenAI / Hermes Compatible DTOs
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float = 0.7f,
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
    val finishReason: String? = null
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("reasoning") val reasoning: String? = null,
    @SerialName("thought") val thought: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null
)

@Serializable
data class OpenAiToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: OpenAiToolCallFunctionDelta? = null
)

@Serializable
data class OpenAiToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class OpenAiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
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

// ---- Dashboard telemetry DTOs (proxy /dashboard/* na ponte 9120) ----

@Serializable
data class DashboardStatusDto(
    val version: String = "",
    @SerialName("gateway_running") val gatewayRunning: Boolean = false,
    @SerialName("gateway_state") val gatewayState: String = "",
    @SerialName("active_sessions") val activeSessions: Int = 0,
    @SerialName("overall") val overall: String = "",
    @SerialName("dashboard") val dashboard: DashboardComponentDto? = null
)

@Serializable
data class DashboardComponentDto(
    val status: String = "",
    @SerialName("recent_unhandled_errors") val recentUnhandledErrors: Int = 0
)

@Serializable
data class SessionSummary(
    val id: String = "",
    val source: String = "",
    val model: String = "",
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("started_at") val startedAt: Double = 0.0,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary> = emptyList()
)

@Serializable
data class UsageTotals(
    @SerialName("total_input") val totalInput: Long = 0,
    @SerialName("total_output") val totalOutput: Long = 0,
    @SerialName("total_cache_read") val totalCacheRead: Long = 0,
    @SerialName("total_sessions") val totalSessions: Int = 0,
    @SerialName("total_api_calls") val totalApiCalls: Int = 0,
    @SerialName("total_estimated_cost") val totalEstimatedCost: Double = 0.0
)

@Serializable
data class DailyUsage(
    val day: String = "",
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("sessions") val sessions: Int = 0,
    @SerialName("api_calls") val apiCalls: Int = 0
)

@Serializable
data class AnalyticsResponse(
    val totals: UsageTotals = UsageTotals(),
    val daily: List<DailyUsage> = emptyList()
)