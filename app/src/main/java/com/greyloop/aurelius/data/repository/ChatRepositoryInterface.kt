package com.greyloop.aurelius.data.repository

import com.greyloop.aurelius.domain.model.Chat
import com.greyloop.aurelius.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepositoryInterface {
    fun getAllChats(): Flow<List<Chat>>
    fun getRecentChats(limit: Int = 20): Flow<List<Chat>>
    fun getChatById(chatId: String): Flow<Chat?>
    fun getMessages(chatId: String): Flow<List<Message>>
    suspend fun getMessagesList(chatId: String): List<Message>
    suspend fun createChat(title: String = "New chat"): Chat
    suspend fun updateChat(chat: Chat)
    suspend fun deleteChat(chatId: String)
    fun searchChats(query: String): Flow<List<Chat>>
    suspend fun sendMessage(
        chatId: String,
        content: String,
        onStreamingUpdate: (String) -> Unit,
        onComplete: (Message) -> Unit,
        onError: (String) -> Unit
    )
}