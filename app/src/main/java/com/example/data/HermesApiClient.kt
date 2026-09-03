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
import io.ktor.client.request.preparePost
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
        engine {
            config {
                retryOnConnectionFailure(true)
            }
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
    var onReasoningChunk: ((String) -> Unit)? = null
    var onToolUse: ((String) -> Unit)? = null

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

    suspend fun fetchProfileInfo(baseUrl: String): ProfileDto? = withContext(Dispatchers.IO) {
        val list = fetchProfilesList(baseUrl)
        val base = list?.profiles?.firstOrNull { it.active } ?: list?.profiles?.firstOrNull()
        // O /profiles (lista) não traz o modelo; o /profile individual (ponte) devolve
        // {"name": ..., "model": ...}. Captura o modelo real do perfil ativo.
        val model = fetchActiveModel(baseUrl)
        return@withContext base?.copy(model = model ?: base.model) ?: (if (model != null) ProfileDto(id = "default", name = "Agent T", active = true, model = model) else null)
    }

    /** Lê o modelo real do perfil ativo via GET /profile (ponte 9120). */
    suspend fun fetchActiveModel(baseUrl: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.get("$normalized/profile") {
                timeout { requestTimeoutMillis = 3_000; connectTimeoutMillis = 3_000 }
            }
            if (response.status.isSuccess()) {
                val element = jsonConfig.parseToJsonElement(response.bodyAsText())
                if (element is JsonObject) {
                    val model = element["model"]?.jsonPrimitive?.contentOrNull
                    if (!model.isNullOrBlank()) return@withContext model
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    suspend fun fetchAllProfiles(baseUrl: String): List<ProfileDto> = withContext(Dispatchers.IO) {
        return@withContext fetchProfilesList(baseUrl)?.profiles ?: emptyList()
    }

    suspend fun switchProfile(baseUrl: String, profileId: String): Boolean = selectProfile(baseUrl, profileId)

    suspend fun fetchProfilesList(baseUrl: String): ProfileListResponse? = withContext(Dispatchers.IO) {
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
                            return@withContext jsonConfig.decodeFromString<ProfileListResponse>(text)
                        } else if (element.containsKey("name") || element.containsKey("profile")) {
                            val name = element["alias"]?.jsonPrimitive?.contentOrNull
                                ?: element["name"]?.jsonPrimitive?.contentOrNull ?: "Hermes"
                            val prof = element["profile"]?.jsonPrimitive?.contentOrNull ?: "default"
                            return@withContext ProfileListResponse(
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
        return@withContext ProfileListResponse(
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
    ): Result<Triple<String, Long, String?>> = withContext(Dispatchers.IO) {
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
                    try {
                        val streamResult: Result<Triple<String, Long, String>>? = client.preparePost(targetUrl) {
                            contentType(ContentType.Application.Json)
                            header(HttpHeaders.Accept, "text/event-stream")
                            // Sem gzip e sem bufferização intermediária
                            header("Accept-Encoding", "identity")
                            header("Cache-Control", "no-cache, no-transform")
                            header("X-Accel-Buffering", "no")
                            setBody(openAiPayload)
                        }.execute { response ->
                            if (!response.status.isSuccess()) {
                                return@execute null
                            }
                            val channel = response.bodyAsChannel()
                            val fullReply = StringBuilder()
                            val reasoningBuf = StringBuilder()
                            val toolCallsBuf = StringBuilder()
                            var firstToolSeen = false
                            var inThinkBlock = false

                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line() ?: break
                                val trimmedLine = line.trim()
                                if (trimmedLine.startsWith("data:")) {
                                    val dataContent = trimmedLine.removePrefix("data:").trim()
                                    if (dataContent == "[DONE]") break

                                    try {
                                        var deltaText: String? = null
                                        var reasoningText: String? = null

                                        try {
                                            val streamResponse = jsonConfig.decodeFromString<OpenAiChatResponse>(dataContent)
                                            val delta = streamResponse.choices.firstOrNull()?.delta
                                            deltaText = delta?.content
                                            reasoningText = delta?.reasoningContent ?: delta?.reasoning ?: delta?.thought
                                            val tc = delta?.toolCalls
                                            if (!tc.isNullOrEmpty()) {
                                                for (call in tc) {
                                                    val name = call.function?.name
                                                    if (!name.isNullOrEmpty()) {
                                                        if (firstToolSeen) toolCallsBuf.append(", ")
                                                        toolCallsBuf.append(name)
                                                        firstToolSeen = true
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    onToolUse?.invoke(toolCallsBuf.toString())
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // Fallback resiliente: parse manual de JSON caso o DTO estrito falhe
                                            try {
                                                val el = jsonConfig.parseToJsonElement(dataContent)
                                                if (el is JsonObject) {
                                                    val firstChoice = el["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                                    val deltaObj = firstChoice?.get("delta")?.jsonObject
                                                    deltaText = deltaObj?.get("content")?.jsonPrimitive?.contentOrNull
                                                    reasoningText = deltaObj?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                                                        ?: deltaObj?.get("reasoning")?.jsonPrimitive?.contentOrNull
                                                        ?: deltaObj?.get("thought")?.jsonPrimitive?.contentOrNull
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Caso 1: Raciocínio explícito do campo reasoning_content/reasoning/thought
                                        if (!reasoningText.isNullOrEmpty()) {
                                            reasoningBuf.append(reasoningText)
                                            withContext(Dispatchers.Main) {
                                                onReasoningChunk?.invoke(reasoningBuf.toString())
                                            }
                                        }

                                        // Caso 2: Conteúdo de texto com suporte em tempo real para tags <think>...</think>
                                        val nonNullDelta = deltaText
                                        if (nonNullDelta != null && nonNullDelta.isNotEmpty()) {
                                            var remaining: String = nonNullDelta
                                            while (remaining.isNotEmpty()) {
                                                if (!inThinkBlock) {
                                                    val thinkIdx = remaining.indexOf("<think>")
                                                    if (thinkIdx != -1) {
                                                        val pre = remaining.substring(0, thinkIdx)
                                                        if (pre.isNotEmpty()) {
                                                            fullReply.append(pre)
                                                            withContext(Dispatchers.Main) {
                                                                onStreamChunk?.invoke(pre)
                                                            }
                                                        }
                                                        inThinkBlock = true
                                                        remaining = remaining.substring(thinkIdx + 7)
                                                    } else {
                                                        fullReply.append(remaining)
                                                        withContext(Dispatchers.Main) {
                                                            onStreamChunk?.invoke(remaining)
                                                        }
                                                        remaining = ""
                                                    }
                                                } else {
                                                    val closeIdx = remaining.indexOf("</think>")
                                                    if (closeIdx != -1) {
                                                        val thoughtPart = remaining.substring(0, closeIdx)
                                                        if (thoughtPart.isNotEmpty()) {
                                                            reasoningBuf.append(thoughtPart)
                                                            withContext(Dispatchers.Main) {
                                                                onReasoningChunk?.invoke(reasoningBuf.toString())
                                                            }
                                                        }
                                                        inThinkBlock = false
                                                        remaining = remaining.substring(closeIdx + 8)
                                                    } else {
                                                        reasoningBuf.append(remaining)
                                                        withContext(Dispatchers.Main) {
                                                            onReasoningChunk?.invoke(reasoningBuf.toString())
                                                        }
                                                        remaining = ""
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            val replyText = fullReply.toString()
                            val reasoningTextTotal = reasoningBuf.toString()
                            if (replyText.isNotBlank() || reasoningTextTotal.isNotBlank()) {
                                val latency = System.currentTimeMillis() - startTime
                                Result.success(Triple(replyText, latency, reasoningTextTotal))
                            } else {
                                null
                            }
                        }

                        if (streamResult != null && streamResult.isSuccess) {
                            return@withContext streamResult
                        }
                    } catch (e: Exception) {
                        Log.w("HermesApiClient", "Streaming falhou em $targetUrl: ${e.message}, tentando POST não-streaming", e)
                    }
                }

                val response: HttpResponse = client.post(targetUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(openAiPayload)
                }

                if (response.status.isSuccess()) {
                    val responseBody = response.bodyAsText()
                    val (reply, reasoning) = parseSuccessfulResponseWithReasoning(responseBody)
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext Result.success(Triple(reply, latency, reasoning ?: ""))
                } else {
                    lastException = Exception("HTTP ${response.status.value} em $targetUrl — verifica o endpoint nas definições (usa AUTO para tentar /v1/chat/completions)")
                }
            } catch (e: Exception) {
                lastException = Exception("$targetUrl: ${e.message}", e)
            }
        }

        val latency = System.currentTimeMillis() - startTime
        return@withContext Result.failure(lastException ?: Exception("Falha de ligação ao Hermes (nenhum endpoint respondeu em $normalized)"))
    }

    private fun parseSuccessfulResponseWithReasoning(body: String): Pair<String, String?> {
        try {
            val jsonElement = jsonConfig.parseToJsonElement(body)
            if (jsonElement is JsonObject) {
                val choices = jsonElement["choices"]?.jsonArray
                val msgObj = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                val messageContent = msgObj?.get("content")?.jsonPrimitive?.contentOrNull
                val reasoningContent = msgObj?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                    ?: msgObj?.get("reasoning")?.jsonPrimitive?.contentOrNull
                    ?: msgObj?.get("thought")?.jsonPrimitive?.contentOrNull

                if (!messageContent.isNullOrBlank()) {
                    if (!reasoningContent.isNullOrBlank()) {
                        return Pair(messageContent, reasoningContent)
                    }
                    val oIdx = messageContent.indexOf("<think>")
                    val cIdx = messageContent.indexOf("</think>")
                    if (oIdx != -1 && cIdx != -1 && cIdx > oIdx) {
                        val thought = messageContent.substring(oIdx + 7, cIdx).trim()
                        val before = messageContent.substring(0, oIdx).trim()
                        val after = messageContent.substring(cIdx + 8).trim()
                        val clean = if (before.isNotEmpty() && after.isNotEmpty()) "$before\n\n$after" else "$before$after"
                        return Pair(clean, thought)
                    }
                    return Pair(messageContent, null)
                }

                val textDirect = jsonElement["text"]?.jsonPrimitive?.contentOrNull
                if (!textDirect.isNullOrBlank()) return Pair(textDirect, null)

                val responseDirect = jsonElement["response"]?.jsonPrimitive?.contentOrNull
                if (!responseDirect.isNullOrBlank()) return Pair(responseDirect, null)
            }
        } catch (_: Exception) {}
        return Pair(body, null)
    }

    private fun parseSuccessfulResponse(body: String): String {
        return parseSuccessfulResponseWithReasoning(body).first
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

    suspend fun fetchDashboardStatus(baseUrl: String): DashboardStatusDto? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.get("$normalized/dashboard/status") {
                timeout { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000 }
            }
            if (response.status.isSuccess()) {
                return@withContext jsonConfig.decodeFromString<DashboardStatusDto>(response.bodyAsText())
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    suspend fun fetchDashboardSessions(baseUrl: String): List<SessionSummary> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.get("$normalized/dashboard/sessions") {
                timeout { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000 }
            }
            if (response.status.isSuccess()) {
                val dto = jsonConfig.decodeFromString<SessionsResponse>(response.bodyAsText())
                return@withContext dto.sessions
            }
        } catch (_: Exception) {}
        return@withContext emptyList()
    }

    suspend fun fetchDashboardAnalytics(baseUrl: String): AnalyticsResponse? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.get("$normalized/dashboard/analytics") {
                timeout { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000 }
            }
            if (response.status.isSuccess()) {
                return@withContext jsonConfig.decodeFromString<AnalyticsResponse>(response.bodyAsText())
            }
        } catch (_: Exception) {}
        return@withContext null
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
