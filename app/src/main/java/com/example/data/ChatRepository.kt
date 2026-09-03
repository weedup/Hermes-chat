package com.example.data

import android.content.Context
import com.example.data.local.ChatSessionEntity
import com.example.data.local.HermesChatDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(context: Context) {
    private val db = HermesChatDatabase.getInstance(context)
    private val dao = db.chatMessageDao()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return dao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    val sessions: Flow<List<ChatSession>> = dao.getAllSessions().map { entities ->
        entities.map { entity ->
            ChatSession(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                lastActiveAt = entity.lastActiveAt
            )
        }
    }

    suspend fun ensureSessionExists(sessionId: String, title: String = "Nova Conversa") {
        dao.insertSession(
            ChatSessionEntity(
                id = sessionId,
                title = title,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertMessage(message: ChatMessage) {
        dao.insertMessage(message.toEntity())
        dao.updateSessionActivity(message.sessionId, message.timestamp)
    }

    suspend fun updateMessage(message: ChatMessage) {
        dao.updateMessage(message.toEntity())
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        dao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteMessage(id: String) {
        dao.deleteMessageById(id)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.clearMessagesForSession(sessionId)
        dao.deleteSessionById(sessionId)
    }

    suspend fun clearHistory(sessionId: String? = null) {
        if (sessionId != null) {
            dao.clearMessagesForSession(sessionId)
        } else {
            dao.clearAllMessages()
        }
    }
}
