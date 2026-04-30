package com.greyloop.aurelius.data.repository

import android.util.Log
import com.greyloop.aurelius.data.local.ChatDao
import com.greyloop.aurelius.data.local.ChatEntity
import com.greyloop.aurelius.data.local.MessageDao
import com.greyloop.aurelius.data.local.MessageEntity
import com.greyloop.aurelius.data.remote.AnthropicMessage
import com.greyloop.aurelius.data.remote.AnthropicRequest
import com.greyloop.aurelius.data.remote.AnthropicResponse
import com.greyloop.aurelius.data.remote.ChatCompletionRequest
import com.greyloop.aurelius.data.remote.ChatCompletionResponse
import com.greyloop.aurelius.data.remote.Choice
import com.greyloop.aurelius.data.remote.FunctionCall
import com.greyloop.aurelius.data.remote.Message as ApiMessage
import com.greyloop.aurelius.data.remote.ResponseMessage
import com.greyloop.aurelius.data.remote.ToolExecutor
import com.greyloop.aurelius.data.remote.ToolDefinition
import com.greyloop.aurelius.data.remote.Usage
import com.greyloop.aurelius.data.security.SecureStorage
import com.greyloop.aurelius.domain.model.AttachmentType
import com.greyloop.aurelius.domain.model.Chat
import com.greyloop.aurelius.domain.model.DefaultPersonas
import com.greyloop.aurelius.domain.model.Message
import com.greyloop.aurelius.domain.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private data class MediaResult(
    val content: String,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val videoUrl: String? = null
)

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val toolExecutor: ToolExecutor,
    private val secureStorage: SecureStorage
) : ChatRepositoryInterface {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
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
            SecureStorage.REGION_CHINA -> "https://api.minimax.chat/v1/chat/completions"
            else -> "https://api.minimax.io/v1/chat/completions"
        }
    }

    protected open fun getAnthropicUrl(): String {
        return when (secureStorage.region) {
            SecureStorage.REGION_CHINA -> "https://api.minimax.chat/anthropic/v1/messages"
            else -> "https://api.minimax.io/anthropic/v1/messages"
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
            val apiKey = secureStorage.codingPlanKey.ifEmpty { secureStorage.minimaxApiKey }

            val summaryRequest = ChatCompletionRequest(
                model = "MiniMax-M2.7",
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
        onError: (String) -> Unit,
        onToolStarted: ((String) -> Unit)?,
        onToolComplete: ((String) -> Unit)?
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
                    content = msg.content,
                    toolCallId = msg.toolCallId
                )
            }

            // Validate at least one API key is available before making network call
            val hasCodingPlanKey = secureStorage.codingPlanKey.isNotEmpty()
            val hasMiniMaxKey = secureStorage.minimaxApiKey.isNotEmpty()
            if (!hasCodingPlanKey && !hasMiniMaxKey) {
                val errorMsg = "No API key available. Please add your MiniMax API key in Settings, " +
                    "or use a coding plan key (sk-cp-) for Anthropic API."
                Log.e(TAG, errorMsg)
                onError(errorMsg)
                return@withContext
            }

            // Execute chat completion
            val response = executeChatCompletion(
                messages = apiMessages,
                toolDefinitions = toolExecutor.getToolDefinitions()
            )

            // Process response (handle tool calls if any)
            val finalContent = processResponse(response, chatId, onError, onToolStarted, onToolComplete)

            // Fallback: check for media intent keywords and execute directly
            val mediaResult = checkAndExecuteMediaIntent(finalContent, onError, onToolStarted, onToolComplete)

            // Save assistant message
            val assistantMessage = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = mediaResult.content,
                imageUrl = mediaResult.imageUrl,
                audioUrl = mediaResult.audioUrl,
                videoUrl = mediaResult.videoUrl,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insert(assistantMessage)

            // Update chat
            chatDao.getChatById(chatId)?.let { c ->
                chatDao.update(c.copy(updatedAt = System.currentTimeMillis()))
            }

            onStreamingUpdate(mediaResult.content)
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
            val userMessage = getUserFriendlyError(e)
            onError(userMessage)
        }
    }

    private suspend fun executeChatCompletion(
        messages: List<ApiMessage>,
        toolDefinitions: List<ToolDefinition>
    ): ChatCompletionResponse {
        // Use Anthropic endpoint if codingPlanKey is set (sk-cp- keys)
        if (secureStorage.codingPlanKey.isNotEmpty()) {
            return executeAnthropicChatCompletion(messages, toolDefinitions)
        }

        // Validate minimaxApiKey is available before making network call
        val apiKey = secureStorage.minimaxApiKey
        if (apiKey.isEmpty()) {
            throw IllegalStateException(
                "No API key available. Please add your MiniMax API key in Settings, " +
                "or use a coding plan key (sk-cp-) for Anthropic API."
            )
        }

        val request = ChatCompletionRequest(
            model = "MiniMax-M2.7",
            messages = messages,
            tools = toolDefinitions,
            stream = false
        )

        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        ).toRequestBody("application/json".toMediaType())

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
                // Check for error JSON before treating as success (Anthropic errors have "error" field)
                if (body.contains("\"error\"")) {
                    throw Exception("API Error: $body")
                }
                json.decodeFromString(body)
            }
        }
    }

    /**
     * Execute chat using MiniMax API endpoint.
     * Both standard MiniMax keys and Coding Plan keys (sk-cp-) authenticate via the same endpoint.
     */
    private suspend fun executeAnthropicChatCompletion(
        messages: List<ApiMessage>,
        toolDefinitions: List<ToolDefinition>
    ): ChatCompletionResponse {
        // Filter out system message (not supported in this path)
        val conversationMessages = messages.filter { it.role != "system" }

        // Use codingPlanKey if available, otherwise minimaxApiKey
        val apiKey = secureStorage.codingPlanKey.ifEmpty { secureStorage.minimaxApiKey }

        val request = ChatCompletionRequest(
            model = "MiniMax-M2.7",
            messages = conversationMessages,
            tools = toolDefinitions,
            stream = false
        )

        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            request
        ).toRequestBody("application/json".toMediaType())

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
                if (body.contains("\"error\"")) {
                    throw Exception("API Error: $body")
                }
                json.decodeFromString(body)
            }
        }
    }

    private suspend fun processResponse(
        response: ChatCompletionResponse,
        chatId: String,
        onError: (String) -> Unit,
        onToolStarted: ((String) -> Unit)?,
        onToolComplete: ((String) -> Unit)?
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

                val toolCallId = toolCall.id
                val result = executeToolCall(toolCall.function, onError, onToolStarted, onToolComplete)
                if (result != null) {
                    // Add tool result as tool message
                    val toolMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = "tool",
                        content = result,
                        timestamp = System.currentTimeMillis(),
                        toolCallId = toolCallId
                    )
                    messageDao.insert(toolMessage)

                    // Get updated history and continue
                    val history = messageDao.getMessagesList(chatId)
                    val apiMessages = history.map { msg ->
                        ApiMessage(
                            role = msg.role,
                            content = msg.content,
                            toolCallId = msg.toolCallId
                        )
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

    /**
     * Fallback media intent detection — checks content for media keywords and
     * directly executes the appropriate tool if keywords are found.
     * This handles cases where AI mentions media intent in text without tool call.
     */
    private suspend fun checkAndExecuteMediaIntent(
        content: String,
        onError: (String) -> Unit,
        onToolStarted: ((String) -> Unit)?,
        onToolComplete: ((String) -> Unit)?
    ): MediaResult {
        val lowerContent = content.lowercase()

        // Image generation intent
        if (lowerContent.contains("generate an image") ||
            lowerContent.contains("creating an image") ||
            lowerContent.contains("I'll create an image") ||
            lowerContent.contains("I'll generate an image")) {
            // Extract prompt from content (look for quoted prompt or descriptive text after keyword)
            val prompt = extractPromptAfterKeyword(content, listOf("generate an image", "creating an image", "I'll create an image", "I'll generate an image"))
            if (prompt.isNotEmpty()) {
                onToolStarted?.invoke(ToolExecutor.TOOL_GENERATE_IMAGE)
                try {
                    var capturedUrl: String? = null
                    toolExecutor.executeImageGeneration(
                        prompt = prompt,
                        onSuccess = { url -> capturedUrl = url },
                        onError = { }
                    )
                    onToolComplete?.invoke(ToolExecutor.TOOL_GENERATE_IMAGE)
                    return MediaResult(content = content, imageUrl = capturedUrl?.replaceFirst("http://", "https://"))
                } catch (e: Exception) {
                    onToolComplete?.invoke(ToolExecutor.TOOL_GENERATE_IMAGE)
                    onError(e.message ?: "Image generation failed")
                }
            }
        }

        // Text-to-audio intent
        if (lowerContent.contains("text to audio") ||
            lowerContent.contains("convert to speech") ||
            lowerContent.contains("generate audio")) {
            val text = extractPromptAfterKeyword(content, listOf("text to audio", "convert to speech", "generate audio"))
            if (text.isNotEmpty()) {
                onToolStarted?.invoke(ToolExecutor.TOOL_TEXT_TO_AUDIO)
                try {
                    var capturedUrl: String? = null
                    toolExecutor.executeTextToAudio(
                        text = text,
                        onSuccess = { url -> capturedUrl = url },
                        onError = { }
                    )
                    onToolComplete?.invoke(ToolExecutor.TOOL_TEXT_TO_AUDIO)
                    return MediaResult(content = content, audioUrl = capturedUrl?.replaceFirst("http://", "https://"))
                } catch (e: Exception) {
                    onToolComplete?.invoke(ToolExecutor.TOOL_TEXT_TO_AUDIO)
                    onError(e.message ?: "Audio generation failed")
                }
            }
        }

        // Music generation intent
        if (lowerContent.contains("generate music") ||
            lowerContent.contains("create a song") ||
            lowerContent.contains("composing music")) {
            val prompt = extractPromptAfterKeyword(content, listOf("generate music", "create a song", "composing music"))
            if (prompt.isNotEmpty()) {
                onToolStarted?.invoke(ToolExecutor.TOOL_MUSIC_GENERATION)
                try {
                    var capturedUrl: String? = null
                    toolExecutor.executeMusicGeneration(
                        prompt = prompt,
                        onSuccess = { url -> capturedUrl = url },
                        onError = { }
                    )
                    onToolComplete?.invoke(ToolExecutor.TOOL_MUSIC_GENERATION)
                    return MediaResult(content = content, audioUrl = capturedUrl?.replaceFirst("http://", "https://"))
                } catch (e: Exception) {
                    onToolComplete?.invoke(ToolExecutor.TOOL_MUSIC_GENERATION)
                    onError(e.message ?: "Music generation failed")
                }
            }
        }

        return MediaResult(content = content)
    }

    /**
     * Extracts the prompt text that follows a media keyword.
     */
    private fun extractPromptAfterKeyword(content: String, keywords: List<String>): String {
        val lowerContent = content.lowercase()
        for (keyword in keywords) {
            val idx = lowerContent.indexOf(keyword)
            if (idx >= 0) {
                val afterKeyword = content.substring(idx + keyword.length).trim()
                // Remove leading punctuation and quotes
                val cleaned = afterKeyword.removePrefix(":").removePrefix("-").removePrefix(" ").trim().removeSurrounding("\"", "\"")
                if (cleaned.isNotEmpty() && cleaned.length > 5) {
                    return cleaned.take(200) // Limit prompt length
                }
            }
        }
        return ""
    }

    private suspend fun executeToolCall(
        function: FunctionCall,
        onError: (String) -> Unit,
        onToolStarted: ((String) -> Unit)?,
        onToolComplete: ((String) -> Unit)?
    ): String? {
        val name = function.name
        val args = try {
            json.decodeFromString<Map<String, String>>(function.arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tool arguments", e)
            return null
        }

        // Notify tool started
        onToolStarted?.invoke(name)

        return@executeToolCall when (name) {
            ToolExecutor.TOOL_TEXT_TO_AUDIO -> {
                var result: String? = null
                var errorMsg: String? = null
                runBlocking {
                    toolExecutor.executeTextToAudio(
                        text = args["text"] ?: "",
                        voiceId = args["voice_id"],
                        speed = args["speed"]?.toFloatOrNull() ?: 1.0f,
                        emotion = args["emotion"],
                        onSuccess = { url ->
                            result = "[Audio generated: ${url.replaceFirst("http://", "https://")}]"
                        },
                        onError = { error ->
                            errorMsg = error
                        }
                    )
                }
                if (errorMsg != null) {
                    onError(errorMsg!!)
                }
                onToolComplete?.invoke(name)
                result
            }
            ToolExecutor.TOOL_UNDERSTAND_IMAGE -> {
                var result: String? = null
                var errorOccurred = false
                runBlocking {
                    toolExecutor.executeImageUnderstanding(
                        imageUrl = args["image_url"] ?: "",
                        prompt = args["prompt"] ?: "Describe this image",
                        onSuccess = { r -> result = r },
                        onError = { e ->
                            errorOccurred = true
                            onError(e)
                        }
                    )
                }
                if (!errorOccurred) {
                    onToolComplete?.invoke(name)
                }
                result
            }
            ToolExecutor.TOOL_WEB_SEARCH -> {
                var result: String? = null
                var errorOccurred = false
                runBlocking {
                    toolExecutor.executeWebSearch(
                        query = args["query"] ?: "",
                        numResults = args["num_results"]?.toIntOrNull() ?: 5,
                        onSuccess = { r -> result = r },
                        onError = { e ->
                            errorOccurred = true
                            onError(e)
                        }
                    )
                }
                if (!errorOccurred) {
                    onToolComplete?.invoke(name)
                }
                result
            }
            ToolExecutor.TOOL_GENERATE_IMAGE -> {
                var result: String? = null
                var errorMsg: String? = null
                runBlocking {
                    toolExecutor.executeImageGeneration(
                        prompt = args["prompt"] ?: "",
                        onSuccess = { url ->
                            result = "[Image generated: ${url.replaceFirst("http://", "https://")}]"
                        },
                        onError = { error ->
                            errorMsg = error
                        }
                    )
                }
                if (errorMsg != null) {
                    onError(errorMsg!!)
                }
                onToolComplete?.invoke(name)
                result
            }
            ToolExecutor.TOOL_MUSIC_GENERATION -> {
                var result: String? = null
                var errorMsg: String? = null
                runBlocking {
                    toolExecutor.executeMusicGeneration(
                        prompt = args["prompt"] ?: "",
                        title = args["title"],
                        onSuccess = { url ->
                            result = "[Music generated: ${url.replaceFirst("http://", "https://")}]"
                        },
                        onError = { error ->
                            errorMsg = error
                        }
                    )
                }
                if (errorMsg != null) {
                    onError(errorMsg!!)
                }
                onToolComplete?.invoke(name)
                result
            }
            ToolExecutor.TOOL_GENERATE_VIDEO -> {
                var result: String? = null
                var errorMsg: String? = null
                runBlocking {
                    toolExecutor.executeVideoGeneration(
                        prompt = args["prompt"] ?: "",
                        onSuccess = { url ->
                            result = "[Video generated: ${url.replaceFirst("http://", "https://")}]"
                        },
                        onError = { error ->
                            errorMsg = error
                        }
                    )
                }
                if (errorMsg != null) {
                    onError(errorMsg!!)
                }
                result
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

    private fun getUserFriendlyError(e: Exception): String {
        val message = e.message ?: ""
        return when {
            message.contains("HTTP 401") || message.contains("Unauthorized") ->
                "Invalid API key. Please check your Settings."
            message.contains("HTTP 4") ->
                "Request failed. Please try again."
            message.contains("network", ignoreCase = true) ||
             message.contains("connection", ignoreCase = true) ||
             message.contains("timeout", ignoreCase = true) ->
                "Connection issue. Please check your internet."
            message.contains("Fields [id", ignoreCase = true) ||
             message.contains("Fields [id, choices]", ignoreCase = true) ->
                "Unable to connect. Please check your API key in Settings."
            else -> "Something went wrong. Please try again."
        }
    }

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
