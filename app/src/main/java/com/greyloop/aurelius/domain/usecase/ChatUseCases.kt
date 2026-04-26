package com.greyloop.aurelius.domain.usecase

import com.greyloop.aurelius.data.repository.ChatRepository
import com.greyloop.aurelius.domain.model.Chat
import com.greyloop.aurelius.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class GetChatsUseCase(private val repository: ChatRepository) {
    operator fun invoke(): Flow<List<Chat>> = repository.getAllChats()
    fun getRecent(limit: Int = 20): Flow<List<Chat>> = repository.getRecentChats(limit)
    fun search(query: String): Flow<List<Chat>> = repository.searchChats(query)
}

class GetChatUseCase(private val repository: ChatRepository) {
    operator fun invoke(chatId: String): Flow<Chat?> = repository.getChatById(chatId)
}

class GetMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(chatId: String): Flow<List<Message>> = repository.getMessages(chatId)
    suspend fun getList(chatId: String): List<Message> = repository.getMessagesList(chatId)
}

class CreateChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(title: String = "New chat"): Chat = repository.createChat(title)
}

class DeleteChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: String) = repository.deleteChat(chatId)
}

class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: String,
        content: String,
        onStreamingUpdate: (String) -> Unit,
        onComplete: (Message) -> Unit,
        onError: (String) -> Unit
    ) = repository.sendMessage(chatId, content, onStreamingUpdate, onComplete, onError)
}

class UpdateChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chat: Chat) = repository.updateChat(chat)
}
