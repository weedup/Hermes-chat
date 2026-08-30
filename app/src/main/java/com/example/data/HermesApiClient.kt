package com.example.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HermesApiClient {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("HermesApiClient", message)
                }
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        if (!trimmed.endsWith("/")) {
            trimmed = "$trimmed/"
        }
        return trimmed
    }

    suspend fun checkHealth(baseUrl: String): ServerHealth = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val startTime = System.currentTimeMillis()

        try {
            // First check root / dashboard
            val response: HttpResponse = client.get(normalized) {
                header(HttpHeaders.Accept, "*/*")
            }
            val latency = System.currentTimeMillis() - startTime
            val serverHeader = response.headers[HttpHeaders.Server] ?: "Hermes-Termux-Server"

            ServerHealth(
                isReachable = response.status.isSuccess() || response.status.value in 200..499,
                statusCode = response.status.value,
                latencyMs = latency,
                serverHeader = serverHeader,
                dashboardAvailable = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("HermesApiClient", "Health check failed for $normalized", e)

            // Try fallback endpoint /v1/models or /health
            try {
                val fallbackUrl = "${normalized.removeSuffix("/")}/v1/models"
                val fallbackResponse: HttpResponse = client.get(fallbackUrl)
                val fallbackLatency = System.currentTimeMillis() - startTime
                if (fallbackResponse.status.isSuccess()) {
                    return@withContext ServerHealth(
                        isReachable = true,
                        statusCode = fallbackResponse.status.value,
                        latencyMs = fallbackLatency,
                        serverHeader = "Hermes-API",
                        dashboardAvailable = false,
                        errorMessage = null
                    )
                }
            } catch (_: Exception) {
                // Secondary check also failed
            }

            val humanError = when {
                e.message?.contains("Failed to connect", ignoreCase = true) == true ||
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Ligação recusada em $normalized. Verifica se o servidor Hermes está a correr no Termux (porta 9119)."
                e.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
                    "Tráfego HTTP sem encriptação bloqueado pelo Android. (usesCleartextTraffic ativo)"
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Tempo limite esgotado a contactar o servidor Hermes."
                else -> e.localizedMessage ?: "Erro desconhecido ao ligar ao servidor."
            }

            ServerHealth(
                isReachable = false,
                statusCode = 0,
                latencyMs = latency,
                serverHeader = "",
                dashboardAvailable = false,
                errorMessage = humanError
            )
        }
    }

    suspend fun sendMessage(
        baseUrl: String,
        history: List<ChatMessage>,
        userPrompt: String,
        model: String,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val startTime = System.currentTimeMillis()

        val messagesList = mutableListOf<OpenAiMessage>()
        if (systemPrompt.isNotBlank()) {
            messagesList.add(OpenAiMessage(role = "system", content = systemPrompt))
        }

        // Add recent context (last 12 messages for performance)
        val contextHistory = history.takeLast(12)
        for (msg in contextHistory) {
            when (msg.sender) {
                MessageSender.USER -> messagesList.add(OpenAiMessage(role = "user", content = msg.text))
                MessageSender.HERMES -> messagesList.add(OpenAiMessage(role = "assistant", content = msg.text))
                MessageSender.SYSTEM -> messagesList.add(OpenAiMessage(role = "system", content = msg.text))
            }
        }
        messagesList.add(OpenAiMessage(role = "user", content = userPrompt))

        val requestBody = OpenAiChatRequest(
            model = model.ifBlank { "hermes-3" },
            messages = messagesList,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = false
        )

        // Try standard OpenAI endpoint: /v1/chat/completions
        val endpoint = "${normalized.removeSuffix("/")}/v1/chat/completions"

        try {
            val response: HttpResponse = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val latency = System.currentTimeMillis() - startTime
            val responseText = response.bodyAsText()

            if (response.status.isSuccess()) {
                try {
                    val parsed = jsonConfig.decodeFromString<OpenAiChatResponse>(responseText)
                    val reply = parsed.choices.firstOrNull()?.message?.content
                    if (!reply.isNullOrBlank()) {
                        return@withContext Result.success(Pair(reply, latency))
                    }
                } catch (pe: Exception) {
                    // Try loose JSON parsing in case format slightly varies
                    try {
                        val jsonElement = jsonConfig.parseToJsonElement(responseText).jsonObject
                        val choices = jsonElement["choices"]?.jsonArray
                        val text = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                            ?: jsonElement["response"]?.jsonPrimitive?.contentOrNull
                            ?: jsonElement["text"]?.jsonPrimitive?.contentOrNull

                        if (!text.isNullOrBlank()) {
                            return@withContext Result.success(Pair(text, latency))
                        }
                    } catch (_: Exception) {
                        // Fall back to plain text if not empty
                    }
                }

                if (responseText.isNotBlank()) {
                    return@withContext Result.success(Pair(responseText, latency))
                }
                return@withContext Result.failure(Exception("Resposta vazia do servidor Hermes"))
            } else {
                // Non-200 status
                return@withContext Result.failure(
                    Exception("Servidor respondeu com código HTTP ${response.status.value}: $responseText")
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("HermesApiClient", "Error sending message to $endpoint", e)

            // Try fallback endpoint /api/chat or /generate
            try {
                val fallbackEndpoint = "${normalized.removeSuffix("/")}/api/chat"
                val fallbackResponse: HttpResponse = client.post(fallbackEndpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (fallbackResponse.status.isSuccess()) {
                    val fallbackText = fallbackResponse.bodyAsText()
                    return@withContext Result.success(Pair(fallbackText, System.currentTimeMillis() - startTime))
                }
            } catch (_: Exception) {
                // Secondary endpoint failed as well
            }

            val friendlyMessage = when {
                e.message?.contains("Failed to connect", ignoreCase = true) == true ||
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Impossível ligar a $endpoint.\nCertifica-te de que o servidor Hermes está ativo no Termux na porta 9119."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Tempo limite atingido durante a geração do modelo Hermes."
                else -> e.localizedMessage ?: "Erro ao comunicar com o servidor Hermes."
            }

            return@withContext Result.failure(Exception(friendlyMessage, e))
        }
    }

    suspend fun fetchMessages(baseUrl: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val endpoints = listOf(
            "${normalized.removeSuffix("/")}/api/messages",
            "${normalized.removeSuffix("/")}/messages",
            "${normalized.removeSuffix("/")}/v1/messages"
        )

        for (endpoint in endpoints) {
            try {
                val response: HttpResponse = client.get(endpoint) {
                    header(HttpHeaders.Accept, "application/json")
                }
                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val parsed = jsonConfig.parseToJsonElement(body)
                    val messagesList = mutableListOf<ChatMessage>()
                    
                    val array = when {
                        parsed is kotlinx.serialization.json.JsonArray -> parsed
                        parsed is JsonObject && parsed.containsKey("messages") -> parsed["messages"]?.jsonArray
                        parsed is JsonObject && parsed.containsKey("data") -> parsed["data"]?.jsonArray
                        else -> null
                    }

                    if (array != null) {
                        for (element in array) {
                            val obj = element.jsonObject
                            val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: "user"
                            val content = obj["content"]?.jsonPrimitive?.contentOrNull
                                ?: obj["text"]?.jsonPrimitive?.contentOrNull
                                ?: obj["message"]?.jsonPrimitive?.contentOrNull
                                ?: ""
                            val sender = if (role.equals("assistant", ignoreCase = true) || role.equals("hermes", ignoreCase = true)) {
                                MessageSender.HERMES
                            } else {
                                MessageSender.USER
                            }
                            if (content.isNotBlank()) {
                                messagesList.add(
                                    ChatMessage(
                                        id = obj["id"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString(),
                                        text = content,
                                        sender = sender,
                                        timestamp = obj["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis(),
                                        status = MessageStatus.SENT
                                    )
                                )
                            }
                        }
                        if (messagesList.isNotEmpty()) {
                            return@withContext Result.success(messagesList)
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        return@withContext Result.failure(Exception("No remote messages endpoint found"))
    }

    suspend fun fetchModels(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val endpoint = "${normalized.removeSuffix("/")}/v1/models"
        try {
            val response: HttpResponse = client.get(endpoint)
            if (response.status.isSuccess()) {
                val parsed = jsonConfig.decodeFromString<ModelsListResponse>(response.bodyAsText())
                val models = parsed.data.map { it.id }.filter { it.isNotBlank() }
                if (models.isNotEmpty()) {
                    return@withContext models
                }
            }
        } catch (e: Exception) {
            Log.w("HermesApiClient", "Could not fetch models list: ${e.message}")
        }
        return@withContext listOf("hermes-3-llama-3.1-8b", "hermes-3", "nous-hermes-2", "hermes-local")
    }
}
