package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.ChatMessage
import com.example.data.MessageSender
import com.example.data.MessageStatus

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: Long,
    val status: String,
    val latencyMs: Long,
    val modelName: String,
    val errorDetails: String? = null
)

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        text = text,
        sender = when (sender) {
            "USER" -> MessageSender.USER
            "SYSTEM" -> MessageSender.SYSTEM
            else -> MessageSender.HERMES
        },
        timestamp = timestamp,
        status = when (status) {
            "SENDING" -> MessageStatus.SENDING
            "ERROR" -> MessageStatus.ERROR
            else -> MessageStatus.SENT
        },
        latencyMs = latencyMs,
        modelName = modelName,
        errorDetails = errorDetails
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        text = text,
        sender = sender.name,
        timestamp = timestamp,
        status = status.name,
        latencyMs = latencyMs,
        modelName = modelName,
        errorDetails = errorDetails
    )
}
