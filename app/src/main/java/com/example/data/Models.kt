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
