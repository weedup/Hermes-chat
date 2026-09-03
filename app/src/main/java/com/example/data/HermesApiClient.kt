package com.example.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 300_000
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
            val response: HttpResponse = client.get(normalized) {
                header(HttpHeaders.Accept, "*/*")
                timeout {
                    requestTimeoutMillis = 3_000
                    connectTimeoutMillis = 3_000
                    socketTimeoutMillis = 3_000
                }
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
            try {
                val fallbackUrl = "${normalized.removeSuffix("/")}/v1/models"
                val fallbackResponse: HttpResponse = client.get(fallbackUrl) {
                    timeout {
                        requestTimeoutMillis = 2_000
                        connectTimeoutMillis = 2_000
                    }
                }
                val fallbackLatency = System.currentTimeMillis() - startTime
                if (fallbackResponse.status.isSuccess()) {
                    return@withContext ServerHealth(
                        isReachable = true,
                        statusCode = fallbackResponse.status.value,
                        latencyMs = fallbackLatency,
                        serverHeader = fallbackResponse.headers[HttpHeaders.Server] ?: "Hermes-API",
                        dashboardAvailable = false,
                        errorMessage = null
                    )
                }
            } catch (_: Exception) {}

            ServerHealth(
                isReachable = false,
                statusCode = 0,
                latencyMs = latency,
                serverHeader = "",
                dashboardAvailable = false,
                errorMessage = "Servidor offline em $normalized. Corre a ponte: python3 scripts/hermes_chat_bridge.py (porta 9120)."
            )
        }
    }

    var onStreamChunk: ((String) -> Unit)? = null

    suspend fun fetchProfile(baseUrl: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val candidates = listOf("/profile", "/api/profile", "/v1/profile")
        for (path in candidates) {
            try {
                val response: HttpResponse = client.get("$normalized$path") {
                    timeout {
                        requestTimeoutMillis = 3_000
                        connectTimeoutMillis = 3_000
                        socketTimeoutMillis = 3_000
                    }
                }
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    val element = jsonConfig.parseToJsonElement(text)
                    if (element is JsonObject) {
                        val name = element["alias"]?.jsonPrimitive?.contentOrNull
                            ?: element["name"]?.jsonPrimitive?.contentOrNull
                        if (!name.isNullOrBlank()) return@withContext name
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    suspend fun fetchProfilesList(baseUrl: String): ProfilesResponse? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val candidates = listOf("/profiles", "/api/profiles", "/profile")
        for (path in candidates) {
            try {
                val response: HttpResponse = client.get("$normalized$path") {
                    timeout {
                        requestTimeoutMillis = 3_000
                        connectTimeoutMillis = 3_000
                        socketTimeoutMillis = 3_000
                    }
                }
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    val element = jsonConfig.parseToJsonElement(text)
                    if (element is JsonObject) {
                        if (element.containsKey("profiles")) {
                            return@withContext jsonConfig.decodeFromString<ProfilesResponse>(text)
                        } else if (element.containsKey("name") || element.containsKey("profile")) {
                            val name = element["alias"]?.jsonPrimitive?.contentOrNull
                                ?: element["name"]?.jsonPrimitive?.contentOrNull ?: "Hermes"
                            val prof = element["profile"]?.jsonPrimitive?.contentOrNull ?: "default"
                            return@withContext ProfilesResponse(
                                current = prof,
                                profiles = listOf(
                                    ProfileDto(id = "default", name = if (prof == "default") name else "Agent T", active = (prof == "default")),
                                    ProfileDto(id = "tara", name = if (prof == "tara") name else "Tara", active = (prof == "tara"))
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext ProfilesResponse(
            current = "default",
            profiles = listOf(
                ProfileDto(id = "default", name = "Agent T", active = true),
                ProfileDto(id = "tara", name = "Tara", active = false)
            )
        )
    }

    suspend fun selectProfile(baseUrl: String, profileId: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.post("$normalized/profile/select") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("profile", profileId) }.toString())
                timeout {
                    requestTimeoutMillis = 3_000
                    connectTimeoutMillis = 3_000
                }
            }
            return@withContext response.status.isSuccess()
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun sendMessage(
        baseUrl: String,
        history: List<ChatMessage>,
        userPrompt: String,
        model: String,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        customEndpoint: String = "AUTO"
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val startTime = System.currentTimeMillis()

        val messagesList = mutableListOf<OpenAiMessage>()
        if (systemPrompt.isNotBlank()) {
            messagesList.add(OpenAiMessage(role = "system", content = systemPrompt))
        }

        val contextHistory = history.takeLast(10)
        for (msg in contextHistory) {
            when (msg.sender) {
                MessageSender.USER -> messagesList.add(OpenAiMessage(role = "user", content = msg.text))
                MessageSender.HERMES -> messagesList.add(OpenAiMessage(role = "assistant", content = msg.text))
                MessageSender.SYSTEM -> messagesList.add(OpenAiMessage(role = "system", content = msg.text))
            }
        }
        messagesList.add(OpenAiMessage(role = "user", content = userPrompt))

        val modelEffective = model.ifBlank { "hermes-agent" }

        val isStreaming = onStreamChunk != null
        val openAiPayload = OpenAiChatRequest(
            model = modelEffective,
            messages = messagesList,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = isStreaming
        )

        val endpointsToTry = mutableListOf<Pair<String, Boolean>>()
        if (customEndpoint.isNotBlank() && !customEndpoint.equals("AUTO", ignoreCase = true)) {
            val cleanPath = if (customEndpoint.startsWith("/")) customEndpoint else "/$customEndpoint"
            endpointsToTry.add(Pair(cleanPath, cleanPath.contains("completion", ignoreCase = true)))
        }

        val standardEndpoints = listOf(
            Pair("/v1/chat/completions", true),
            Pair("/chat/completions", true),
            Pair("/v1/chat", true),
            Pair("/chat", true),
            Pair("/api/chat", true)
        )
        endpointsToTry.addAll(standardEndpoints.filter { ep -> !endpointsToTry.any { it.first.equals(ep.first, ignoreCase = true) } })

        var lastException: Exception? = null

        for ((endpointPath, isOpenAi) in endpointsToTry) {
            val targetUrl = "$normalized$endpointPath"
            try {
                if (isStreaming && isOpenAi) {
                    val response: HttpResponse = client.post(targetUrl) {
                        contentType(ContentType.Application.Json)
                        setBody(openAiPayload)
                    }

                    if (response.status.isSuccess()) {
                        val channel = response.bodyAsChannel()
                        val fullReply = StringBuilder()

                        while (!channel.isClosedForRead) {
                            val line = channel.readUTF8Line() ?: break
                            val trimmedLine = line.trim()
                            if (trimmedLine.startsWith("data:")) {
                                val dataContent = trimmedLine.removePrefix("data:").trim()
                                if (dataContent == "[DONE]") break

                                try {
                                    val streamResponse = jsonConfig.decodeFromString<OpenAiChatResponse>(dataContent)
                                    val deltaText = streamResponse.choices.firstOrNull()?.delta?.content
                                    if (!deltaText.isNullOrEmpty()) {
                                        fullReply.append(deltaText)
                                        withContext(Dispatchers.Main) {
                                            onStreamChunk?.invoke(deltaText)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        val replyText = fullReply.toString()
                        if (replyText.isNotBlank()) {
                            val latency = System.currentTimeMillis() - startTime
                            return@withContext Result.success(Pair(replyText, latency))
                        }
                    }
                }

                val response: HttpResponse = client.post(targetUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(openAiPayload)
                }

                if (response.status.isSuccess()) {
                    val responseBody = response.bodyAsText()
                    val reply = parseSuccessfulResponse(responseBody)
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext Result.success(Pair(reply, latency))
                } else {
                    lastException = Exception("HTTP ${response.status.value}: ${response.status.description}")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        val latency = System.currentTimeMillis() - startTime
        return@withContext Result.failure(lastException ?: Exception("Falha de ligação ao Hermes"))
    }

    private fun parseSuccessfulResponse(body: String): String {
        try {
            val jsonElement = jsonConfig.parseToJsonElement(body)
            if (jsonElement is JsonObject) {
                val choices = jsonElement["choices"]?.jsonArray
                val messageContent = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                if (!messageContent.isNullOrBlank()) return messageContent

                val textDirect = jsonElement["text"]?.jsonPrimitive?.contentOrNull
                if (!textDirect.isNullOrBlank()) return textDirect

                val responseDirect = jsonElement["response"]?.jsonPrimitive?.contentOrNull
                if (!responseDirect.isNullOrBlank()) return responseDirect
            }
        } catch (_: Exception) {}
        return body
    }

    suspend fun probeEndpoints(baseUrl: String): List<EndpointProbeResult> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val endpoints = listOf(
            Pair("/v1/chat/completions", "POST"),
            Pair("/chat/completions", "POST"),
            Pair("/v1/models", "GET"),
            Pair("/models", "GET"),
            Pair("/health", "GET"),
            Pair("/profile", "GET"),
            Pair("/profiles", "GET")
        )
        val results = mutableListOf<EndpointProbeResult>()
        for ((path, method) in endpoints) {
            val start = System.currentTimeMillis()
            try {
                val response: HttpResponse = if (method == "GET") {
                    client.get("$normalized$path") {
                        timeout { requestTimeoutMillis = 2_000 }
                    }
                } else {
                    client.post("$normalized$path") {
                        timeout { requestTimeoutMillis = 2_000 }
                    }
                }
                val latency = System.currentTimeMillis() - start
                results.add(
                    EndpointProbeResult(
                        path = path,
                        method = method,
                        statusCode = response.status.value,
                        isSuccess = response.status.isSuccess() || response.status.value in 200..499,
                        message = "HTTP ${response.status.value}",
                        latencyMs = latency
                    )
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                results.add(
                    EndpointProbeResult(
                        path = path,
                        method = method,
                        statusCode = 0,
                        isSuccess = false,
                        message = e.localizedMessage ?: "Erro de ligação",
                        latencyMs = latency
                    )
                )
            }
        }
        return@withContext results
    }

    suspend fun fetchModels(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val endpoint = "${normalized.removeSuffix("/")}/v1/models"
        try {
            val response: HttpResponse = client.get(endpoint) {
                timeout {
                    requestTimeoutMillis = 3_000
                    connectTimeoutMillis = 3_000
                }
            }
            if (response.status.isSuccess()) {
                val parsed = jsonConfig.decodeFromString<ModelsListResponse>(response.bodyAsText())
                val models = parsed.data.map { it.id }.filter { it.isNotBlank() }
                if (models.isNotEmpty()) {
                    return@withContext models
                }
            }
        } catch (_: Exception) {}
        return@withContext listOf("hermes-agent", "hermes-3")
    }
}
