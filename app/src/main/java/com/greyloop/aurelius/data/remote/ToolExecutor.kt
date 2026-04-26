package com.greyloop.aurelius.data.remote

import android.util.Log
import com.greyloop.aurelius.data.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Central hub for all MiniMax MCP tool executions.
 * Supports: text_to_audio, understand_image, web_search, text_to_image, music_generation, generate_video
 */
class ToolExecutor(
    private val secureStorage: SecureStorage
) {
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

    private fun getBaseUrl(): String {
        return when (secureStorage.region) {
            SecureStorage.REGION_CHINA -> "https://api.minimax.chat"
            else -> "https://api.minimax.io"
        }
    }

    private fun getApiKey(forTool: String): String {
        // web_search and understand_image require coding plan key
        return when (forTool) {
            TOOL_WEB_SEARCH, TOOL_UNDERSTAND_IMAGE -> {
                secureStorage.codingPlanKey.ifEmpty { secureStorage.minimaxApiKey }
            }
            else -> secureStorage.minimaxApiKey
        }
    }

    private fun getModelForPlan(tool: String): String {
        val plan = secureStorage.planType
        return when (tool) {
            TOOL_TEXT_TO_AUDIO -> {
                if (plan == SecureStorage.PLAN_CODING_PLAN_PLUS) {
                    "speech-2.8-turbo"
                } else {
                    "speech-2.8-hd"
                }
            }
            TOOL_GENERATE_IMAGE -> "anydoor高清"
            TOOL_MUSIC_GENERATION -> "music-01"
            TOOL_GENERATE_VIDEO -> "video-01"
            TOOL_UNDERSTAND_IMAGE -> "MiniMax-VL-01"
            TOOL_WEB_SEARCH -> "MiniMax-M2.7-highspeed"
            else -> "MiniMax-M1-zero"
        }
    }

    suspend fun executeTextToAudio(
        text: String,
        voiceId: String? = null,
        speed: Float = 1.0f,
        emotion: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val model = getModelForPlan(TOOL_TEXT_TO_AUDIO)
            val request = TextToAudioRequest(
                model = model,
                text = text,
                stream = false,
                voice_setting = VoiceSetting(
                    voice_id = voiceId,
                    speed = speed,
                    emotion = emotion
                )
            )

            val response = executeRequest(
                endpoint = "/v1/t2a_v2",
                body = json.encodeToString(TextToAudioRequest.serializer(), request),
                tool = TOOL_TEXT_TO_AUDIO
            )

            val audioResponse = json.decodeFromString<TextToAudioResponse>(response)
            val audioUrl = audioResponse.data.flow_url ?: audioResponse.data.url
            if (audioUrl != null) {
                onSuccess(audioUrl.replaceFirst("http://", "https://"))
            } else {
                onError("No audio URL in response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "text_to_audio error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun executeImageUnderstanding(
        imageUrl: String,
        prompt: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val model = getModelForPlan(TOOL_UNDERSTAND_IMAGE)
            val request = ImageUnderstandingRequest(
                model = model,
                messages = listOf(
                    ImageMessage(
                        role = "user",
                        content = listOf(
                            ContentPart(
                                type = "image_url",
                                image_url = ImageUrl(url = imageUrl)
                            ),
                            ContentPart(
                                type = "text",
                                text = prompt
                            )
                        )
                    )
                )
            )

            val response = executeRequest(
                endpoint = "/v1/coding_plan/vlm",
                body = json.encodeToString(ImageUnderstandingRequest.serializer(), request),
                tool = TOOL_UNDERSTAND_IMAGE
            )

            val imageResponse = json.decodeFromString<ImageUnderstandingResponse>(response)
            val content = imageResponse.choices.firstOrNull()?.message?.content ?: ""
            onSuccess(content)
        } catch (e: Exception) {
            Log.e(TAG, "understand_image error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun executeWebSearch(
        query: String,
        numResults: Int = 5,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = WebSearchRequest(
                query = query,
                numResults = numResults
            )

            val response = executeRequest(
                endpoint = "/v1/coding_plan/search",
                body = json.encodeToString(WebSearchRequest.serializer(), request),
                tool = TOOL_WEB_SEARCH
            )

            val searchResponse = json.decodeFromString<WebSearchResponse>(response)
            val content = searchResponse.choices.firstOrNull()?.message?.content ?: ""
            onSuccess(content)
        } catch (e: Exception) {
            Log.e(TAG, "web_search error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun executeImageGeneration(
        prompt: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val model = getModelForPlan(TOOL_GENERATE_IMAGE)
            val requestBody = """{"model":"$model","prompt":"$prompt"}"""

            val response = executeRequestRaw(
                endpoint = "/v1/image_generation",
                body = requestBody,
                tool = TOOL_GENERATE_IMAGE
            )

            val imageResponse = json.decodeFromString<ImageGenerationResponse>(response)
            val imageUrl = imageResponse.data.firstOrNull()?.url
            if (imageUrl != null) {
                onSuccess(imageUrl.replaceFirst("http://", "https://"))
            } else {
                onError("No image URL in response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "generate_image error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun executeMusicGeneration(
        prompt: String,
        title: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val model = getModelForPlan(TOOL_MUSIC_GENERATION)
            val request = MusicGenerationRequest(
                model = model,
                prompt = prompt,
                title = title
            )

            val response = executeRequest(
                endpoint = "/v1/music_generation",
                body = json.encodeToString(MusicGenerationRequest.serializer(), request),
                tool = TOOL_MUSIC_GENERATION
            )

            val musicResponse = json.decodeFromString<MusicGenerationResponse>(response)
            val taskId = musicResponse.task_id

            // Poll for completion
            pollForTaskCompletion(
                taskId = taskId,
                tool = TOOL_MUSIC_GENERATION,
                onSuccess = onSuccess,
                onError = onError
            )
        } catch (e: Exception) {
            Log.e(TAG, "music_generation error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun executeVideoGeneration(
        prompt: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val model = getModelForPlan(TOOL_GENERATE_VIDEO)
            val request = VideoGenerationRequest(
                model = model,
                prompt = prompt
            )

            val response = executeRequest(
                endpoint = "/v1/video_generation",
                body = json.encodeToString(VideoGenerationRequest.serializer(), request),
                tool = TOOL_GENERATE_VIDEO
            )

            val videoResponse = json.decodeFromString<VideoGenerationResponse>(response)
            val taskId = videoResponse.task_id

            // Poll for completion
            pollForTaskCompletion(
                taskId = taskId,
                tool = TOOL_GENERATE_VIDEO,
                onSuccess = onSuccess,
                onError = onError
            )
        } catch (e: Exception) {
            Log.e(TAG, "generate_video error", e)
            onError(e.message ?: "Unknown error")
        }
    }

    private suspend fun pollForTaskCompletion(
        taskId: String,
        tool: String,
        maxAttempts: Int = 30,
        intervalMs: Long = 10000,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var attempts = 0
        while (attempts < maxAttempts) {
            try {
                kotlinx.coroutines.delay(intervalMs)
                val statusResponse = checkTaskStatus(taskId, tool)
                val status = statusResponse.status

                if (status == "success" || status == "completed") {
                    val url = when (tool) {
                        TOOL_MUSIC_GENERATION -> statusResponse.data?.url
                        TOOL_GENERATE_VIDEO -> statusResponse.data?.url
                        else -> null
                    }
                    if (url != null) {
                        onSuccess(url.replaceFirst("http://", "https://"))
                    } else {
                        onError("No URL in completed task response")
                    }
                    return@withContext
                } else if (status == "failed") {
                    onError("Task failed")
                    return@withContext
                }
                attempts++
            } catch (e: Exception) {
                Log.e(TAG, "pollForTaskCompletion error", e)
                attempts++
                if (attempts >= maxAttempts) {
                    onError(e.message ?: "Polling failed")
                }
            }
        }
        onError("Timeout waiting for task completion")
    }

    private suspend fun checkTaskStatus(taskId: String, tool: String): TaskStatusResponse {
        val endpoint = when (tool) {
            TOOL_MUSIC_GENERATION -> "/v1/music_generation/retrieve"
            TOOL_GENERATE_VIDEO -> "/v1/video_generation/retrieve"
            else -> throw IllegalArgumentException("Unknown tool: $tool")
        }

        val requestBody = """{"task_id":"$taskId"}"""
        val response = executeRequestRaw(endpoint, requestBody, tool)
        return json.decodeFromString(response)
    }

    private suspend fun executeRequest(
        endpoint: String,
        body: String,
        tool: String
    ): String {
        val apiKey = getApiKey(tool)
        val url = "${getBaseUrl()}$endpoint"

        val requestBody = body.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Request failed: ${response.code} - $errorBody")
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                response.body?.string() ?: "{}"
            }
        }
    }

    private suspend fun executeRequestRaw(
        endpoint: String,
        body: String,
        tool: String
    ): String = executeRequest(endpoint, body, tool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_TEXT_TO_AUDIO,
                    description = "Generate audio from text with voice synthesis. Use this for text-to-speech, voice responses with emotion control.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "text" to ToolProperty("string", "The text to convert to speech"),
                            "voice_id" to ToolProperty("string", "Optional voice identifier"),
                            "speed" to ToolProperty("number", "Speech speed (0.5-2.0)"),
                            "emotion" to ToolProperty("string", "Voice emotion: neutral, positive, negative, happy, sad, angry")
                        ),
                        required = listOf("text")
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_UNDERSTAND_IMAGE,
                    description = "Analyze images to describe content, objects, text, or answer questions about them.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "image_url" to ToolProperty("string", "URL of the image to analyze"),
                            "prompt" to ToolProperty("string", "Question or instruction about the image")
                        ),
                        required = listOf("image_url", "prompt")
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_WEB_SEARCH,
                    description = "Search the web for current information, news, or factual data.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "query" to ToolProperty("string", "The search query"),
                            "num_results" to ToolProperty("integer", "Number of results (default 5)")
                        ),
                        required = listOf("query")
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_GENERATE_IMAGE,
                    description = "Generate images from text prompts using AI.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "Detailed description of the image to generate")
                        ),
                        required = listOf("prompt")
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_MUSIC_GENERATION,
                    description = "Generate music from text prompts.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "Description of the music style, mood, instruments"),
                            "title" to ToolProperty("string", "Optional title for the music")
                        ),
                        required = listOf("prompt")
                    )
                )
            ),
            ToolDefinition(
                function = ToolFunction(
                    name = TOOL_GENERATE_VIDEO,
                    description = "Generate short videos from text prompts.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "Description of the video scene and action")
                        ),
                        required = listOf("prompt")
                    )
                )
            )
        )
    }

    companion object {
        private const val TAG = "ToolExecutor"
        const val TOOL_TEXT_TO_AUDIO = "text_to_audio"
        const val TOOL_UNDERSTAND_IMAGE = "understand_image"
        const val TOOL_WEB_SEARCH = "web_search"
        const val TOOL_GENERATE_IMAGE = "generate_image"
        const val TOOL_MUSIC_GENERATION = "music_generation"
        const val TOOL_GENERATE_VIDEO = "generate_video"
    }
}
