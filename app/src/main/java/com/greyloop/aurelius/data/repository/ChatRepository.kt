package com.greyloop.aurelius.data.repository

import android.util.Log
import com.greyloop.aurelius.data.local.ChatDao
import com.greyloop.aurelius.data.local.ChatEntity
import com.greyloop.aurelius.data.local.MessageDao
import com.greyloop.aurelius.data.local.MessageEntity
import com.greyloop.aurelius.data.remote.ChatCompletionRequest
import com.greyloop.aurelius.data.remote.ChatCompletionResponse
import com.greyloop.aurelius.data.remote.FunctionCall
import com.greyloop.aurelius.data.remote.Message as ApiMessage
import com.greyloop.aurelius.data.remote.ToolExecutor
import com.greyloop.aurelius.data.remote.ToolDefinition
import com.greyloop.aurelius.data.security.SecureStorage
import com.greyloop.aurelius.domain.model.AttachmentType
import com.greyloop.aurelius.domain.model.Chat
import com.greyloop.aurelius.domain.model.DefaultPersonas
import com.greyloop.aurelius.domain.model.Message
import com.greyloop.aurelius.domain.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val toolExecutor: ToolExecutor,
    private val secureStorage: SecureStorage
) : ChatRepositoryInterface {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    protected open fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val client by lazy { createClient() }

    protected open fun getApiUrl(): String {
        return when (secureStorage.region) {
            SecureStorage.REGION_CHINA -> "https://api.minimax.chat/v1/text/chatcompletion_v2"
            else -> "https://api.minimax.chat/v1/text/chatcompletion_v2"
        }
    }

    override fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentChats(limit: Int): Flow<List<Chat>> {
        return chatDao.getRecentChats(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getChatById(chatId: String): Flow<Chat?> {
        return chatDao.getChatByIdFlow(chatId).map { it?.toDomain() }
    }

    override fun getMessages(chatId: String): Flow<List<Message>> {
        return messageDao.getMessages(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessagesList(chatId: String): List<Message> {
        return messageDao.getMessagesList(chatId).map { it.toDomain() }
    }

    override suspend fun createChat(title: String, personaId: String): Chat {
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            preview = "",
            createdAt = now,
            updatedAt = now,
            personaId = personaId
        )
        chatDao.insert(chat)
        return chat.toDomain()
    }

    override suspend fun createBranchChat(parentChatId: String, parentMessageId: String, title: String): Chat {
        val parentChat = chatDao.getChatById(parentChatId)
        val now = System.currentTimeMillis()
        val branchChat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            preview = "",
            createdAt = now,
            updatedAt = now,
            parentBranchId = parentChatId,
            personaId = parentChat?.personaId ?: "assistant"
        )
        chatDao.insert(branchChat)
        return branchChat.toDomain()
    }

    override suspend fun generateSummary(chatId: String): String? {
        val messages = messageDao.getMessagesList(chatId)
        if (messages.size < 10) return null

        val conversationText = messages.takeLast(20).joinToString("\n") { msg ->
            "${msg.role}: ${msg.content}"
        }

        return try {
            val summaryRequest = ChatCompletionRequest(
                model = "abab6.5-chat",
                messages = listOf(
                    ApiMessage(
                        role = "system",
                        content = "You are a summarization assistant. Create a brief 2-3 sentence summary of the conversation above."
                    ),
                    ApiMessage(
                        role = "user",
                        content = "Summarize this conversation briefly:\n$conversationText"
                    )
                ),
                tools = emptyList(),
                stream = false
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                summaryRequest
            ).toRequestBody("application/json".toMediaType())

            val apiKey = secureStorage.codingPlanKey.ifEmpty { secureStorage.minimaxApiKey }
            val request = Request.Builder()
                .url(getApiUrl())
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            var result: String? = null
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val body = response.body?.string() ?: "{}"
                    val chatResponse = json.decodeFromString<ChatCompletionResponse>(body)
                    result = chatResponse.choices.firstOrNull()?.message?.content?.take(200)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "generateSummary error", e)
            null
        }
    }

    override suspend fun updateChat(chat: Chat) {
        chatDao.update(chat.toEntity())
    }

    override suspend fun deleteChat(chatId: String) {
        chatDao.deleteById(chatId)
    }

    override fun searchChats(query: String): Flow<List<Chat>> {
        return chatDao.searchChats(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        content: String,
        onStreamingUpdate: (String) -> Unit,
        onComplete: (Message) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Save user message
            val userMessage = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "user",
                content = content,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insert(userMessage)

            // Update chat preview
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                chatDao.update(chat.copy(
                    preview = content.take(MAX_PREVIEW_LENGTH),
                    updatedAt = System.currentTimeMillis()
                ))
            }

            // Get message history
            val history = messageDao.getMessagesList(chatId)

            // Get persona system prompt
            val persona = chat?.let { DefaultPersonas.getById(it.personaId) } ?: DefaultPersonas.ASSISTANT
            val personaMessage = ApiMessage(role = "system", content = persona.systemPrompt)

            val apiMessages = listOf(personaMessage) + history.map { msg ->
                ApiMessage(
                    role = msg.role,
                    content = msg.content
                )
            }

            // Execute chat completion
            val response = executeChatCompletion(
                messages = apiMessages,
                toolDefinitions = toolExecutor.getToolDefinitions()
            )

            // Process response (handle tool calls if any)
            val finalContent = processResponse(response, chatId, onError)

            // Save assistant message
            val assistantMessage = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = finalContent,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insert(assistantMessage)

            // Update chat
            chatDao.getChatById(chatId)?.let { c ->
                chatDao.update(c.copy(updatedAt = System.currentTimeMillis()))
            }

            onStreamingUpdate(finalContent)
            onComplete(assistantMessage.toDomain())

            // Auto-summary trigger: check if 10+ messages and no summary exists
            val chatForSummary = chatDao.getChatById(chatId)
            if (chatForSummary?.summary == null) {
                val messageCount = messageDao.getMessagesList(chatId).size
                if (messageCount >= 10) {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        val summary = generateSummary(chatId)
                        if (summary != null) {
                            chatDao.getChatById(chatId)?.let { c ->
                                chatDao.update(c.copy(summary = summary))
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            onError(e.message ?: "Failed to send message")
        }
    }

    private suspend fun executeChatCompletion(
        messages: List<ApiMessage>,
        toolDefinitions: List<ToolDefinition>
    ): ChatCompletionResponse {
        val request = ChatCompletionRequest(
            model = "abab6.5-chat",
            messages = messages,
            tools = toolDefinitions,
            stream = false
        )

        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        ).toRequestBody("application/json".toMediaType())

        val apiKey = secureStorage.codingPlanKey.ifEmpty { secureStorage.minimaxApiKey }
        val requestBuilder = Request.Builder()
            .url(getApiUrl())
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        return withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                val body = response.body?.string() ?: "{}"
                json.decodeFromString(body)
            }
        }
    }

    private suspend fun processResponse(
        response: ChatCompletionResponse,
        chatId: String,
        onError: (String) -> Unit
    ): String {
        val choice = response.choices.firstOrNull() ?: return ""
        var finalContent = choice.message.content

        val toolCalls = choice.message.toolCalls
        if (!toolCalls.isNullOrEmpty()) {
            var rounds = 0
            val maxRounds = 5

            for (toolCall in toolCalls) {
                if (rounds >= maxRounds) {
                    finalContent += "\n\n[Tool limit reached - continued without tools]"
                    break
                }

                val result = executeToolCall(toolCall.function, onError)
                if (result != null) {
                    // Add tool result as assistant message
                    val toolMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = "assistant",
                        content = result,
                        timestamp = System.currentTimeMillis()
                    )
                    messageDao.insert(toolMessage)

                    // Get updated history and continue
                    val history = messageDao.getMessagesList(chatId)
                    val apiMessages = history.map { msg ->
                        ApiMessage(role = msg.role, content = msg.content)
                    }

                    try {
                        val continuation = executeChatCompletion(
                            messages = apiMessages,
                            toolDefinitions = emptyList() // Don't loop with tools
                        )
                        finalContent = continuation.choices.firstOrNull()?.message?.content ?: finalContent
                    } catch (e: Exception) {
                        Log.e(TAG, "Continuation error", e)
                    }
                }
                rounds++
            }
        }

        return finalContent
    }

    private suspend fun executeToolCall(
        function: FunctionCall,
        onError: (String) -> Unit
    ): String? {
        val name = function.name
        val args = try {
            json.decodeFromString<Map<String, String>>(function.arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tool arguments", e)
            return null
        }

        return when (name) {
            ToolExecutor.TOOL_TEXT_TO_AUDIO -> {
                var result: String? = null
                toolExecutor.executeTextToAudio(
                    text = args["text"] ?: "",
                    voiceId = args["voice_id"],
                    speed = args["speed"]?.toFloatOrNull() ?: 1.0f,
                    emotion = args["emotion"],
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result?.let { "[Audio generated: $it]" }
            }

            ToolExecutor.TOOL_UNDERSTAND_IMAGE -> {
                var result: String? = null
                toolExecutor.executeImageUnderstanding(
                    imageUrl = args["image_url"] ?: "",
                    prompt = args["prompt"] ?: "Describe this image",
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result
            }

            ToolExecutor.TOOL_WEB_SEARCH -> {
                var result: String? = null
                toolExecutor.executeWebSearch(
                    query = args["query"] ?: "",
                    numResults = args["num_results"]?.toIntOrNull() ?: 5,
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result
            }

            ToolExecutor.TOOL_GENERATE_IMAGE -> {
                var result: String? = null
                toolExecutor.executeImageGeneration(
                    prompt = args["prompt"] ?: "",
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result?.let { "[Image generated: $it]" }
            }

            ToolExecutor.TOOL_MUSIC_GENERATION -> {
                var result: String? = null
                toolExecutor.executeMusicGeneration(
                    prompt = args["prompt"] ?: "",
                    title = args["title"],
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result?.let { "[Music generated: $it]" }
            }

            ToolExecutor.TOOL_GENERATE_VIDEO -> {
                var result: String? = null
                toolExecutor.executeVideoGeneration(
                    prompt = args["prompt"] ?: "",
                    onSuccess = { result = it },
                    onError = { onError(it) }
                )
                result?.let { "[Video generated: $it]" }
            }

            else -> {
                Log.w(TAG, "Unknown tool: $name")
                null
            }
        }
    }

    private fun ChatEntity.toDomain() = Chat(
        id = id,
        title = title,
        preview = preview,
        createdAt = createdAt,
        updatedAt = updatedAt,
        parentBranchId = parentBranchId,
        personaId = personaId,
        summary = summary
    )

    private fun Chat.toEntity() = ChatEntity(
        id = id,
        title = title,
        preview = preview,
        createdAt = createdAt,
        updatedAt = updatedAt,
        parentBranchId = parentBranchId,
        personaId = personaId,
        summary = summary
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        chatId = chatId,
        role = if (role == "user") Role.USER else Role.ASSISTANT,
        content = content,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
        videoUrl = videoUrl,
        attachmentName = attachmentName,
        attachmentType = attachmentType?.let {
            try { AttachmentType.valueOf(it) } catch (e: Exception) { null }
        },
        timestamp = timestamp,
        parentMessageId = parentMessageId
    )

    companion object {
        private const val TAG = "ChatRepository"
        private const val MAX_PREVIEW_LENGTH = 60
    }
}
