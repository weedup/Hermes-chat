package com.example.data

import android.content.Context
import com.example.data.local.HermesChatDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(context: Context) {
    private val db = HermesChatDatabase.getInstance(context)
    private val dao = db.chatMessageDao()

    val messages: Flow<List<ChatMessage>> = dao.getAllMessages().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun insertMessage(message: ChatMessage) {
        dao.insertMessage(message.toEntity())
    }

    suspend fun updateMessage(message: ChatMessage) {
        dao.updateMessage(message.toEntity())
    }

    suspend fun deleteMessage(id: String) {
        dao.deleteMessageById(id)
    }

    suspend fun clearHistory() {
        dao.clearAllMessages()
    }
}
