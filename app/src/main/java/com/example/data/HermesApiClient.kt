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
            // Check root with a responsive timeout
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
            Log.d("HermesApiClient", "Server $normalized is not reachable yet: ${e.message}")

            // Try fallback endpoint /v1/models or /health with short timeout
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
                e.message?.contains("Connection refused", ignoreCase = true) == true ||
                e.message?.contains("ECONNREFUSED", ignoreCase = true) == true ->
                    "Servidor offline em $normalized. Corre a ponte: python3 scripts/hermes_chat_bridge.py (porta 9120)."
                e.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
                    "Tráfego HTTP sem encriptação bloqueado pelo Android. (usesCleartextTraffic ativo)"
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Tempo limite esgotado a contactar o servidor Hermes ($normalized)."
                else -> e.localizedMessage ?: "Servidor inacessível no momento."
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

    /**
     * Callback para streaming: chamado com cada fragmento de texto à medida que chega.
     * Se for null, o pedido é feito sem streaming (compatibilidade).
     */
    var onStreamChunk: ((String) -> Unit)? = null

    /**
     * Devolve o nome do perfil/agente Hermes ativo, lendo o endpoint /profile da
     * ponte (que faz a resolução para "Agent T" / "Tara" / etc.). Fallback: null.
     */
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
            } catch (_: Exception) {
                // tenta o próximo endpoint
            }
        }
        return@withContext null
    }

    /**
     * Devolve detalhes do perfil ativo (nome, id do perfil, etc.)
     */
    suspend fun fetchProfileDetails(baseUrl: String): Map<String, String>? = withContext(Dispatchers.IO) {
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
                        val result = mutableMapOf<String, String>()
                        element["name"]?.jsonPrimitive?.contentOrNull?.let { result["name"] = it }
                        element["profile"]?.jsonPrimitive?.contentOrNull?.let { result["profile"] = it }
                        element["alias"]?.jsonPrimitive?.contentOrNull?.let { result["alias"] = it }
                        if (result.isNotEmpty()) return@withContext result
                    }
                }
            } catch (_: Exception) {
                // tenta o próximo endpoint
            }
        }
        return@withContext null
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

        // Prepare messages structure
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

        // Build full context prompt for non-OpenAI endpoints
        val fullPromptBuilder = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            fullPromptBuilder.append("<|im_start|>system\n$systemPrompt<|im_end|>\n")
        }
        for (m in messagesList) {
            fullPromptBuilder.append("<|im_start|>${m.role}\n${m.content}<|im_end|>\n")
        }
        fullPromptBuilder.append("<|im_start|>assistant\n")
        val fullPromptText = fullPromptBuilder.toString()

        // JSON payload variants
        val isStreaming = onStreamChunk != null
        val openAiPayload = OpenAiChatRequest(
            model = modelEffective,
            messages = messagesList,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = isStreaming
        )

        // Simple dict payload for FastAPI / Flask / Python local scripts
        val simpleDictPayload = buildJsonObject {
            put("model", modelEffective)
            put("prompt", fullPromptText)
            put("user_prompt", userPrompt)
            put("message", userPrompt)
            put("text", userPrompt)
            put("query", userPrompt)
            put("input", userPrompt)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
            putJsonArray("messages") {
                messagesList.forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                }
            }
        }

        // Determine list of endpoints to try
        val endpointsToTry = mutableListOf<Pair<String, Boolean>>() // path, isOpenAiFormat

        if (customEndpoint.isNotBlank() && !customEndpoint.equals("AUTO", ignoreCase = true)) {
            val cleanPath = if (customEndpoint.startsWith("/")) customEndpoint else "/$customEndpoint"
            endpointsToTry.add(Pair(cleanPath, cleanPath.contains("completion", ignoreCase = true)))
        }

        // Add standard endpoints in priority order
        val standardEndpoints = listOf(
            Pair("/v1/chat/completions", true),
            Pair("/chat/completions", true),
            Pair("/chat", false),
            Pair("/api/chat", false),
            Pair("/v1/chat", false),
            Pair("/generate", false),
            Pair("/api/generate", false),
            Pair("/completion", false),
            Pair("/completions", false),
            Pair("/v1/completions", false),
            Pair("/predict", false),
            Pair("/prompt", false),
            Pair("", false) // POST to root
        )

        for (ep in standardEndpoints) {
            if (endpointsToTry.none { it.first.removeSuffix("/") == ep.first.removeSuffix("/") }) {
                endpointsToTry.add(ep)
            }
        }

        val errorsLog = mutableListOf<String>()

        for ((path, preferOpenAi) in endpointsToTry) {
            val fullUrl = if (path.isEmpty()) normalized else "$normalized${if (path.startsWith("/")) path else "/$path"}"
            
            // Try preferred payload first, then fallback payload on the same endpoint
            val payloads = if (preferOpenAi) {
                listOf(Pair("openai", openAiPayload), Pair("simple", simpleDictPayload))
            } else {
                listOf(Pair("simple", simpleDictPayload), Pair("openai", openAiPayload))
            }

            for ((_, payload) in payloads) {
                try {
                    val response: HttpResponse = client.post(fullUrl) {
                        contentType(ContentType.Application.Json)
                        if (payload is OpenAiChatRequest) {
                            setBody(payload)
                        } else if (payload is JsonObject) {
                            setBody(payload)
                        }
                    }

                    // Streaming SSE: se onStreamChunk está definido e a resposta é texto/event-stream
                    if (isStreaming && response.status.isSuccess()) {
                        val ct = response.headers[HttpHeaders.ContentType] ?: ""
                        if (ct.contains("text/event-stream", ignoreCase = true) ||
                            ct.contains("text/plain", ignoreCase = true)) {
                            val fullText = StringBuilder()
                            try {
                                val channel = response.bodyAsChannel()
                                while (!channel.isClosedForRead) {
                                    val line = channel.readUTF8Line() ?: break
                                    if (line.startsWith("data: ")) {
                                        val data = line.removePrefix("data: ").trim()
                                        if (data == "[DONE]") break
                                        try {
                                            val sseJson = jsonConfig.parseToJsonElement(data)
                                            if (sseJson is JsonObject) {
                                                val delta = sseJson["choices"]?.jsonArray
                                                    ?.firstOrNull()?.jsonObject
                                                    ?.get("delta")?.jsonObject
                                                val chunk = delta?.get("content")?.jsonPrimitive?.contentOrNull
                                                    ?: sseJson["choices"]?.jsonArray
                                                        ?.firstOrNull()?.jsonObject
                                                        ?.get("text")?.jsonPrimitive?.contentOrNull
                                                if (!chunk.isNullOrBlank()) {
                                                    fullText.append(chunk)
                                                    onStreamChunk?.invoke(chunk)
                                                }
                                            }
                                        } catch (_: Exception) { /* skip malformed SSE */ }
                                    }
                                }
                            } catch (_: Exception) { /* fallback to non-streaming below */ }
                            val result = fullText.toString().ifBlank { null }
                            if (result != null) {
                                val latency = System.currentTimeMillis() - startTime
                                Log.d("HermesApiClient", "SSE stream success from $fullUrl in ${latency}ms")
                                return@withContext Result.success(Pair(result, latency))
                            }
                        }
                    }

                    val responseText = response.bodyAsText()
                    val latency = System.currentTimeMillis() - startTime

                    if (response.status.isSuccess()) {
                        val extracted = extractContentFromResponse(responseText)
                        if (!extracted.isNullOrBlank()) {
                            Log.d("HermesApiClient", "Success with endpoint $fullUrl in ${latency}ms")
                            return@withContext Result.success(Pair(extracted, latency))
                        }
                        if (responseText.isNotBlank()) {
                            return@withContext Result.success(Pair(responseText, latency))
                        }
                    } else {
                        errorsLog.add("$path -> HTTP ${response.status.value}")
                        // If 405 Method Not Allowed or 404 Not Found, immediately try next endpoint
                        if (response.status == HttpStatusCode.MethodNotAllowed ||
                            response.status == HttpStatusCode.NotFound) {
                            break // Try next endpoint in list
                        }
                    }
                } catch (e: Exception) {
                    errorsLog.add("$path -> ${e.message ?: "Connection error"}")
                    Log.d("HermesApiClient", "Probe to $fullUrl failed: ${e.message}")
                    // If connection refused on root, whole server is likely down
                    if (e.message?.contains("Connection refused", ignoreCase = true) == true ||
                        e.message?.contains("Failed to connect", ignoreCase = true) == true) {
                        return@withContext Result.failure(
                            Exception("Servidor offline em $baseUrl. Corre a ponte: python3 scripts/hermes_chat_bridge.py (porta 9120).")
                        )
                    }
                }
            }
        }

        val lastErrorSummary = errorsLog.joinToString("; ")
        val friendlyMessage = "Não foi possível obter resposta válida dos endpoints testados em $baseUrl.\nResumo das tentativas: $lastErrorSummary\n\nDica: Abre as Definições para testar ou selecionar a rota exata do teu script Termux."
        return@withContext Result.failure(Exception(friendlyMessage))
    }

    private fun extractContentFromResponse(responseText: String): String? {
        // Try strict OpenAiChatResponse
        try {
            val parsed = jsonConfig.decodeFromString<OpenAiChatResponse>(responseText)
            val reply = parsed.choices.firstOrNull()?.message?.content
            if (!reply.isNullOrBlank()) return reply
        } catch (_: Exception) {
        }

        // Try loose JSON parsing
        try {
            val element = jsonConfig.parseToJsonElement(responseText)
            if (element is JsonObject) {
                // Check choices[0].message.content
                element["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.let { choice ->
                    val c = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                        ?: choice["text"]?.jsonPrimitive?.contentOrNull
                    if (!c.isNullOrBlank()) return c
                }

                // Check top-level standard fields
                val directFields = listOf(
                    "response", "reply", "output", "result", "text", "content", "message", "answer", "data"
                )
                for (field in directFields) {
                    val fieldVal = element[field]
                    if (fieldVal is JsonPrimitive) {
                        val text = fieldVal.contentOrNull
                        if (!text.isNullOrBlank()) return text
                    } else if (fieldVal is JsonObject) {
                        val text = fieldVal["content"]?.jsonPrimitive?.contentOrNull
                            ?: fieldVal["text"]?.jsonPrimitive?.contentOrNull
                        if (!text.isNullOrBlank()) return text
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Fallback: if it's plain text without JSON markers, return trimmed text
        if (!responseText.trim().startsWith("{") && !responseText.trim().startsWith("[")) {
            return responseText.trim()
        }

        return null
    }

    suspend fun probeEndpoints(baseUrl: String): List<EndpointProbeResult> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl).removeSuffix("/")
        val results = mutableListOf<EndpointProbeResult>()

        val candidatePaths = listOf(
            Pair("/v1/chat/completions", "POST"),
            Pair("/chat/completions", "POST"),
            Pair("/chat", "POST"),
            Pair("/api/chat", "POST"),
            Pair("/generate", "POST"),
            Pair("/api/generate", "POST"),
            Pair("/v1/models", "GET"),
            Pair("/models", "GET"),
            Pair("/health", "GET"),
            Pair("/", "GET")
        )

        for ((path, method) in candidatePaths) {
            val fullUrl = "$normalized$path"
            val start = System.currentTimeMillis()
            try {
                val pingBody = buildJsonObject {
                    put("prompt", "ping")
                    put("model", "hermes-agent")
                    put("message", "ping")
                    put("text", "ping")
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "user")
                            put("content", "ping")
                        }
                    }
                }

                val response: HttpResponse = if (method == "POST") {
                    client.post(fullUrl) {
                        contentType(ContentType.Application.Json)
                        setBody(pingBody)
                        timeout {
                            requestTimeoutMillis = 3_000
                            connectTimeoutMillis = 3_000
                        }
                    }
                } else {
                    client.get(fullUrl) {
                        timeout {
                            requestTimeoutMillis = 3_000
                            connectTimeoutMillis = 3_000
                        }
                    }
                }

                val latency = System.currentTimeMillis() - start
                val code = response.status.value
                val isSuccess = response.status.isSuccess()
                val message = when (code) {
                    200 -> "200 OK (Disponível)"
                    405 -> "405 Method Not Allowed"
                    404 -> "404 Not Found"
                    422 -> "422 Unprocessable Entity"
                    else -> "HTTP $code"
                }

                results.add(
                    EndpointProbeResult(
                        path = path,
                        method = method,
                        statusCode = code,
                        isSuccess = isSuccess,
                        message = message,
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
                        message = e.message ?: "Erro de ligação",
                        latencyMs = latency
                    )
                )
            }
        }
        return@withContext results
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
                    timeout {
                        requestTimeoutMillis = 3_000
                        connectTimeoutMillis = 3_000
                    }
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
        } catch (e: Exception) {
            Log.w("HermesApiClient", "Could not fetch models list: ${e.message}")
        }
        return@withContext listOf("hermes-agent", "hermes-3")
    }
}
