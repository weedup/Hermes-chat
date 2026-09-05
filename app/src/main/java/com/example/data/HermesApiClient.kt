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
import kotlinx.serialization.json.JsonArray
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
        // 1. /profile devolve o estado completo do perfil ativo: {id, profile, name, model}
        val direct = fetchActiveProfileFull(baseUrl)
        if (direct != null) return@withContext direct

        // 2. Fallback: lista de perfis (a ponte nova traz modelos; a antiga não)
        val list = fetchProfilesList(baseUrl)
        val base = list?.profiles?.firstOrNull { it.active } ?: list?.profiles?.firstOrNull()
        val model = fetchActiveModel(baseUrl)
        base?.copy(model = model ?: base.model)
    }

    /** Lê {id, profile, name, model} do GET /profile — a fonte autoritativa do perfil ativo. */
    private suspend fun fetchActiveProfileFull(baseUrl: String): ProfileDto? = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.get("$normalized/profile") {
                timeout { requestTimeoutMillis = 3_000; connectTimeoutMillis = 3_000 }
            }
            if (response.status.isSuccess()) {
                val element = jsonConfig.parseToJsonElement(response.bodyAsText())
                if (element is JsonObject) {
                    val id = element["id"]?.jsonPrimitive?.contentOrNull
                        ?: element["profile"]?.jsonPrimitive?.contentOrNull
                    val name = element["alias"]?.jsonPrimitive?.contentOrNull
                        ?: element["name"]?.jsonPrimitive?.contentOrNull
                    val model = element["model"]?.jsonPrimitive?.contentOrNull
                    // Só aceita se trouxer o ID — bridges antigas devolvem só {name, model}
                    // e a identidade tem de vir da lista de perfis, não de um id inventado
                    if (!id.isNullOrBlank()) {
                        return@withContext ProfileDto(
                            id = id,
                            name = name?.takeIf { it.isNotBlank() } ?: id,
                            active = true,
                            model = model ?: ""
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext null
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
        var gotNetworkResponse = false
        for (path in candidates) {
            try {
                val response: HttpResponse = client.get("$normalized$path") {
                    timeout {
                        requestTimeoutMillis = 3_000
                        connectTimeoutMillis = 3_000
                        socketTimeoutMillis = 3_000
                    }
                }
                gotNetworkResponse = true
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
                            val model = element["model"]?.jsonPrimitive?.contentOrNull ?: ""
                            // Devolve APENAS o perfil real ativo — nada de entradas fantasmas
                            return@withContext ProfileListResponse(
                                current = prof,
                                profiles = listOf(
                                    ProfileDto(id = prof, name = name, active = true, model = model)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        // Ponte offline: NÃO inventar perfis — devolver null deixa o estado da app intacto
        return@withContext if (gotNetworkResponse) {
            ProfileListResponse(
                current = "default",
                profiles = listOf(
                    ProfileDto(id = "default", name = "Agent T", active = true)
                )
            )
        } else {
            null
        }
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
    ): Result<SendMessageResult> = withContext(Dispatchers.IO) {
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

        val modelEffective = if (model.isBlank() || model.equals("hermes-agent", ignoreCase = true) || model.equals("hermes", ignoreCase = true)) {
            "nousresearch/hermes-3-llama-3.1-8b"
        } else {
            model
        }

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
                        val streamResult: Result<SendMessageResult>? = client.preparePost(targetUrl) {
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
                            val thoughtFilter = StreamingThoughtFilter()
                            val toolCallsBuf = StringBuilder()
                            var firstToolSeen = false
                            var capturedModel: String? = null

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
                                            if (!streamResponse.model.isNullOrBlank()) {
                                                capturedModel = streamResponse.model
                                            }
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
                                                    val mStr = el["model"]?.jsonPrimitive?.contentOrNull
                                                    if (!mStr.isNullOrBlank()) {
                                                        capturedModel = mStr
                                                    }
                                                    val firstChoice = el["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                                    val deltaObj = firstChoice?.get("delta")?.jsonObject
                                                    deltaText = deltaObj?.get("content")?.jsonPrimitive?.contentOrNull
                                                    reasoningText = deltaObj?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                                                        ?: deltaObj?.get("reasoning")?.jsonPrimitive?.contentOrNull
                                                        ?: deltaObj?.get("thought")?.jsonPrimitive?.contentOrNull
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Raciocínio explícito do campo reasoning_content/reasoning/thought
                                        if (!reasoningText.isNullOrEmpty()) {
                                            thoughtFilter.feedDirectReasoning(reasoningText) { currentThought ->
                                                withContext(Dispatchers.Main) {
                                                    onReasoningChunk?.invoke(currentThought)
                                                }
                                            }
                                        }

                                        // Conteúdo de texto com filtragem em tempo real de tags <thinking>...</thinking>
                                        if (!deltaText.isNullOrEmpty()) {
                                            thoughtFilter.feed(
                                                chunk = deltaText,
                                                onTextDelta = { cleanChunk ->
                                                    withContext(Dispatchers.Main) {
                                                        onStreamChunk?.invoke(cleanChunk)
                                                    }
                                                },
                                                onThoughtUpdated = { currentThought ->
                                                    withContext(Dispatchers.Main) {
                                                        onReasoningChunk?.invoke(currentThought)
                                                    }
                                                }
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            thoughtFilter.finish(
                                onTextDelta = { cleanChunk ->
                                    withContext(Dispatchers.Main) {
                                        onStreamChunk?.invoke(cleanChunk)
                                    }
                                },
                                onThoughtUpdated = { currentThought ->
                                    withContext(Dispatchers.Main) {
                                        onReasoningChunk?.invoke(currentThought)
                                    }
                                }
                            )

                            val replyText = thoughtFilter.textAccumulator.toString().trim()
                            val reasoningTextTotal = thoughtFilter.thoughtAccumulator.toString().trim()
                            if (replyText.isNotBlank() || reasoningTextTotal.isNotBlank()) {
                                val latency = System.currentTimeMillis() - startTime
                                Result.success(
                                    SendMessageResult(
                                        reply = replyText,
                                        latencyMs = latency,
                                        reasoning = reasoningTextTotal.takeIf { it.isNotBlank() },
                                        modelName = capturedModel
                                    )
                                )
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
                    val (reply, reasoning, modelFromResp) = parseSuccessfulResponseWithReasoning(responseBody)
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext Result.success(
                        SendMessageResult(
                            reply = reply,
                            latencyMs = latency,
                            reasoning = reasoning,
                            modelName = modelFromResp
                        )
                    )
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

    private fun parseSuccessfulResponseWithReasoning(body: String): Triple<String, String?, String?> {
        try {
            val jsonElement = jsonConfig.parseToJsonElement(body)
            if (jsonElement is JsonObject) {
                val respModel = jsonElement["model"]?.jsonPrimitive?.contentOrNull
                val choices = jsonElement["choices"]?.jsonArray
                val msgObj = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                val messageContent = msgObj?.get("content")?.jsonPrimitive?.contentOrNull
                val reasoningContent = msgObj?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                    ?: msgObj?.get("reasoning")?.jsonPrimitive?.contentOrNull
                    ?: msgObj?.get("thought")?.jsonPrimitive?.contentOrNull

                if (!messageContent.isNullOrBlank()) {
                    if (!reasoningContent.isNullOrBlank()) {
                        return Triple(messageContent, reasoningContent, respModel)
                    }
                    val extracted = com.example.ui.components.extractThoughtAndResponse(messageContent)
                    return Triple(extracted.cleanResponse, extracted.thought, respModel)
                }

                val textDirect = jsonElement["text"]?.jsonPrimitive?.contentOrNull
                if (!textDirect.isNullOrBlank()) return Triple(textDirect, null, respModel)

                val responseDirect = jsonElement["response"]?.jsonPrimitive?.contentOrNull
                if (!responseDirect.isNullOrBlank()) return Triple(responseDirect, null, respModel)
            }
        } catch (_: Exception) {}
        return Triple(body, null, null)
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

    suspend fun selectModel(baseUrl: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        try {
            val response: HttpResponse = client.post("$normalized/model/select") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("model", modelName) }.toString())
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

    suspend fun fetchModels(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val endpoints = listOf("/models", "/v1/models", "/api/models")

        for (path in endpoints) {
            try {
                val response: HttpResponse = client.get("$normalized$path") {
                    timeout {
                        requestTimeoutMillis = 3_000
                        connectTimeoutMillis = 3_000
                    }
                }
                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val elem = jsonConfig.parseToJsonElement(body)
                    val result = mutableListOf<String>()

                    if (elem is JsonObject) {
                        // Formato OpenAI: { "data": [ { "id": "model_id" }, ... ] }
                        val dataArr = elem["data"]?.jsonArray
                        if (dataArr != null) {
                            for (item in dataArr) {
                                val id = when (item) {
                                    is JsonObject -> item["id"]?.jsonPrimitive?.contentOrNull
                                    is JsonPrimitive -> item.contentOrNull
                                    else -> null
                                }
                                if (!id.isNullOrBlank()) result.add(id)
                            }
                        } else {
                            // Formato alternativo { "models": [...] }
                            val modelsArr = elem["models"]?.jsonArray
                            if (modelsArr != null) {
                                for (item in modelsArr) {
                                    val id = when (item) {
                                        is JsonObject -> item["name"]?.jsonPrimitive?.contentOrNull ?: item["id"]?.jsonPrimitive?.contentOrNull
                                        is JsonPrimitive -> item.contentOrNull
                                        else -> null
                                    }
                                    if (!id.isNullOrBlank()) result.add(id)
                                }
                            }
                        }
                    } else if (elem is JsonArray) {
                        for (item in elem) {
                            val id = when (item) {
                                is JsonObject -> item["id"]?.jsonPrimitive?.contentOrNull ?: item["name"]?.jsonPrimitive?.contentOrNull
                                is JsonPrimitive -> item.contentOrNull
                                else -> null
                            }
                            if (!id.isNullOrBlank()) result.add(id)
                        }
                    }

                    val sanitized = result
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.equals("hermes-agent", ignoreCase = true) && !it.equals("hermes", ignoreCase = true) }
                        .distinct()

                    if (sanitized.isNotEmpty()) {
                        return@withContext sanitized
                    }
                }
            } catch (_: Exception) {}
        }

        // Catálogo padrão de modelos populares reais (caso o servidor ainda não tenha listagem dinâmica ativa)
        return@withContext listOf(
            "nousresearch/hermes-3-llama-3.1-8b",
            "nousresearch/hermes-3-llama-3.1-70b",
            "meta-llama/llama-3.3-70b-instruct",
            "meta-llama/llama-3.1-8b-instruct",
            "qwen/qwen-2.5-72b-instruct",
            "qwen/qwen-2.5-coder-32b-instruct",
            "deepseek/deepseek-chat",
            "deepseek/deepseek-r1",
            "mistralai/mistral-large-2407",
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "anthropic/claude-3-5-sonnet"
        )
    }
}

/**
 * Filtro resiliente para streaming em tempo real que extrai tags de raciocínio
 * (<thinking>...</thinking>, <think>...</think>, <thought>...</thought>)
 * sem misturar raciocínio com o texto final da resposta, mesmo quando as tags
 * chegam divididas entre múltiplos chunks do socket SSE.
 */
class StreamingThoughtFilter {
    companion object {
        private val OPEN_TAGS = listOf("<thinking>", "<think>", "<thought>")
        private val CLOSE_TAGS = listOf("</thinking>", "</think>", "</thought>")
        private const val MAX_PREFIX_LEN = 12
    }

    var inThought: Boolean = false
        private set

    private var pending: String = ""
    val textAccumulator = StringBuilder()
    val thoughtAccumulator = StringBuilder()

    suspend fun feedDirectReasoning(reasoning: String, onThoughtUpdated: suspend (String) -> Unit) {
        if (reasoning.isNotEmpty()) {
            thoughtAccumulator.append(reasoning)
            onThoughtUpdated(thoughtAccumulator.toString())
        }
    }

    suspend fun feed(
        chunk: String,
        onTextDelta: suspend (String) -> Unit,
        onThoughtUpdated: suspend (String) -> Unit
    ) {
        var buffer = pending + chunk
        pending = ""

        while (buffer.isNotEmpty()) {
            if (!inThought) {
                var earliestIdx = -1
                var matchedTag = ""
                for (tag in OPEN_TAGS) {
                    val idx = buffer.indexOf(tag, ignoreCase = true)
                    if (idx != -1 && (earliestIdx == -1 || idx < earliestIdx)) {
                        earliestIdx = idx
                        matchedTag = tag
                    }
                }

                if (earliestIdx != -1) {
                    val pre = buffer.substring(0, earliestIdx)
                    if (pre.isNotEmpty()) {
                        textAccumulator.append(pre)
                        onTextDelta(pre)
                    }
                    inThought = true
                    buffer = buffer.substring(earliestIdx + matchedTag.length)
                    if (buffer.startsWith("\r\n")) buffer = buffer.substring(2)
                    else if (buffer.startsWith("\n")) buffer = buffer.substring(1)
                } else {
                    val prefix = findPossibleOpenTagPrefix(buffer)
                    if (prefix != null) {
                        val safeText = buffer.substring(0, buffer.length - prefix.length)
                        if (safeText.isNotEmpty()) {
                            textAccumulator.append(safeText)
                            onTextDelta(safeText)
                        }
                        pending = prefix
                        buffer = ""
                    } else {
                        textAccumulator.append(buffer)
                        onTextDelta(buffer)
                        buffer = ""
                    }
                }
            } else {
                var earliestCloseIdx = -1
                var matchedCloseTag = ""
                for (tag in CLOSE_TAGS) {
                    val idx = buffer.indexOf(tag, ignoreCase = true)
                    if (idx != -1 && (earliestCloseIdx == -1 || idx < earliestCloseIdx)) {
                        earliestCloseIdx = idx
                        matchedCloseTag = tag
                    }
                }

                if (earliestCloseIdx != -1) {
                    val thoughtPart = buffer.substring(0, earliestCloseIdx)
                    if (thoughtPart.isNotEmpty()) {
                        thoughtAccumulator.append(thoughtPart)
                        onThoughtUpdated(thoughtAccumulator.toString())
                    }
                    inThought = false
                    buffer = buffer.substring(earliestCloseIdx + matchedCloseTag.length)
                    if (buffer.startsWith("\r\n")) buffer = buffer.substring(2)
                    else if (buffer.startsWith("\n")) buffer = buffer.substring(1)
                } else {
                    val prefix = findPossibleCloseTagPrefix(buffer)
                    if (prefix != null) {
                        val safeThought = buffer.substring(0, buffer.length - prefix.length)
                        if (safeThought.isNotEmpty()) {
                            thoughtAccumulator.append(safeThought)
                            onThoughtUpdated(thoughtAccumulator.toString())
                        }
                        pending = prefix
                        buffer = ""
                    } else {
                        thoughtAccumulator.append(buffer)
                        onThoughtUpdated(thoughtAccumulator.toString())
                        buffer = ""
                    }
                }
            }
        }
    }

    private fun findPossibleOpenTagPrefix(str: String): String? {
        val maxLen = minOf(str.length, MAX_PREFIX_LEN)
        for (len in maxLen downTo 1) {
            val suffix = str.substring(str.length - len)
            if (suffix.startsWith("<")) {
                for (tag in OPEN_TAGS) {
                    if (tag.startsWith(suffix, ignoreCase = true)) {
                        return suffix
                    }
                }
            }
        }
        return null
    }

    private fun findPossibleCloseTagPrefix(str: String): String? {
        val maxLen = minOf(str.length, MAX_PREFIX_LEN)
        for (len in maxLen downTo 1) {
            val suffix = str.substring(str.length - len)
            if (suffix.startsWith("<") || suffix.startsWith("</")) {
                for (tag in CLOSE_TAGS) {
                    if (tag.startsWith(suffix, ignoreCase = true)) {
                        return suffix
                    }
                }
            }
        }
        return null
    }

    suspend fun finish(
        onTextDelta: suspend (String) -> Unit,
        onThoughtUpdated: suspend (String) -> Unit
    ) {
        if (pending.isNotEmpty()) {
            if (inThought) {
                thoughtAccumulator.append(pending)
                onThoughtUpdated(thoughtAccumulator.toString())
            } else {
                textAccumulator.append(pending)
                onTextDelta(pending)
            }
            pending = ""
        }
    }
}
