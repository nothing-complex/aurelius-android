package com.greyloop.aurelius.data.remote

import android.util.Log
import com.greyloop.aurelius.data.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Central hub for all MiniMax MCP tool executions.
 * Supports: text_to_audio, understand_image, web_search, text_to_image, music_generation, generate_video
 */
class ToolExecutor(
    private val secureStorage: SecureStorage,
    private val cacheDir: java.io.File
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
            .dispatcher(Dispatcher().apply {
                maxRequests = 10
                maxRequestsPerHost = 10
            })
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
            TOOL_GENERATE_IMAGE -> "image-01"
            TOOL_MUSIC_GENERATION -> "music-02"
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
            val effectiveVoiceId = voiceId ?: "female-shaonv"
            val voiceSetting = VoiceSetting(
                voice_id = effectiveVoiceId,
                speed = speed,
                vol = null,
                pitch = null,
                emotion = emotion
            )
            val request = TextToAudioRequest(
                model = model,
                text = text,
                stream = false,
                voice_setting = voiceSetting,
                voice_id = effectiveVoiceId  // Also send at top level per MiniMax API
            )

            // Log the full JSON being sent
            val requestJson = json.encodeToString(TextToAudioRequest.serializer(), request)
            Log.d(TAG, "TTS request JSON: $requestJson")

            // Log request headers and body details
            Log.d(TAG, "TTS model=$model, voiceId=$effectiveVoiceId, speed=$speed, emotion=$emotion")
            Log.d(TAG, "TTS voice_setting object: voice_id=${voiceSetting.voice_id}, speed=${voiceSetting.speed}, emotion=${voiceSetting.emotion}")

            val response = executeRequest(
                endpoint = "/v1/t2a_v2",
                body = requestJson,
                tool = TOOL_TEXT_TO_AUDIO
            )

            Log.d(TAG, "TTS raw response: $response")
            val audioResponse = json.decodeFromString<TextToAudioResponse>(response)
            Log.d(TAG, "TTS parsed: id=${audioResponse.id}, data=${audioResponse.data}")

            // Try flow_url or url first (streaming URL format)
            var audioUrl = audioResponse.data?.flow_url ?: audioResponse.data?.url

            // If no URL, try raw audio data (base64 encoded)
            if (audioUrl == null && audioResponse.data?.audio != null) {
                val audioData = audioResponse.data.audio
                Log.d(TAG, "TTS got raw audio data, length=${audioData.length}")
                try {
                    val audioBytes = android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
                    val audioFile = java.io.File(cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                    audioFile.writeBytes(audioBytes)
                    audioUrl = audioFile.absolutePath
                    Log.d(TAG, "TTS saved to file: ${audioFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS failed to save audio file", e)
                }
            }

            Log.d(TAG, "TTS extracted URL: $audioUrl")
            if (audioUrl != null) {
                val safeUrl = audioUrl.replaceFirst("http://", "https://")
                Log.d(TAG, "TTS calling onSuccess with: $safeUrl")
                onSuccess(safeUrl)
            } else {
                Log.e(TAG, "TTS no audio URL in response, data=$audioResponse.data")
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
            Log.d(TAG, "understand_image: starting with URL=$imageUrl")

            // Try to download image and convert to base64 since MiniMax URLs may be internal
            val finalImageData = try {
                val conn = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.readBytes()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } else {
                    Log.d(TAG, "understand_image: URL not accessible (${conn.responseCode}), using original")
                    imageUrl
                }
            } catch (e: Exception) {
                Log.d(TAG, "understand_image: download failed, using original URL: ${e.message}")
                imageUrl
            }

            val model = getModelForPlan(TOOL_UNDERSTAND_IMAGE)
            Log.d(TAG, "understand_image: model=$model, usingBase64=${finalImageData.length > 200}")

            // MiniMax VL API expects flat format: {"prompt": "...", "image_url": "data:image/jpeg;base64,..."}
            val imageDataForApi = if (finalImageData.length > 200 && !finalImageData.startsWith("data:")) {
                "data:image/jpeg;base64,$finalImageData"
            } else {
                finalImageData
            }
            val requestJson = """{"prompt":"$prompt","image_url":"$imageDataForApi"}"""

            val response = executeRequest(
                endpoint = "/v1/coding_plan/vlm",
                body = requestJson,
                tool = TOOL_UNDERSTAND_IMAGE
            )

            Log.d(TAG, "understand_image: response=$response")
            val imageResponse = json.decodeFromString<ImageUnderstandingResponse>(response)
            // Try choices format first (OpenAI compatible), then fall back to MiniMax direct format
            val content = imageResponse.choices?.firstOrNull()?.message?.content
                ?: imageResponse.content
                ?: imageResponse.baseResp?.status_msg
                ?: ""
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
            Log.d(TAG, "executeImageGeneration: START prompt=$prompt")
            val model = getModelForPlan(TOOL_GENERATE_IMAGE)
            val request = ImageGenerationRequest(
                model = model,
                prompt = prompt,
                response_format = "url"
            )

            val response = executeRequest(
                endpoint = "/v1/image_generation",
                body = json.encodeToString(ImageGenerationRequest.serializer(), request),
                tool = TOOL_GENERATE_IMAGE
            )

            val imageResponse = json.decodeFromString<ImageGenerationResponse>(response)
            Log.d(TAG, "executeImageGeneration: imageResponse=$imageResponse")

            // Check base_resp for API-level errors
            val baseResp = imageResponse.baseResp
            if (baseResp != null && baseResp.status_code != null && baseResp.status_code != 0) {
                val msg = baseResp.status_msg ?: "Image generation failed (code: ${baseResp.status_code})"
                Log.e(TAG, "Image API error: ${baseResp.status_code} ${baseResp.status_msg}")
                onError(msg)
                return@withContext
            }

            // Check if immediate image URLs are available (sync response)
            val immediateImageUrl = imageResponse.data?.image_urls?.firstOrNull()
            if (immediateImageUrl != null) {
                Log.d(TAG, "executeImageGeneration: immediate image URL available")
                onSuccess(immediateImageUrl.replaceFirst("http://", "https://"))
                return@withContext
            }

            // Otherwise, check for task_id and poll for async completion
            val taskId = imageResponse.task_id
            if (taskId == null) {
                onError("No image URL or task_id in response")
                return@withContext
            }

            Log.d(TAG, "executeImageGeneration: async task_id=$taskId, polling for completion")
            pollForTaskCompletion(
                taskId = taskId,
                tool = TOOL_GENERATE_IMAGE,
                onSuccess = onSuccess,
                onError = onError
            )
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
        val requestBody = json.encodeToString(MusicGenerationRequest.serializer(), MusicGenerationRequest(
            model = getModelForPlan(TOOL_MUSIC_GENERATION),
            prompt = prompt,
            title = title
        ))
        val rawResponse = executeRequest(
            endpoint = "/v1/music_generation",
            body = requestBody,
            tool = TOOL_MUSIC_GENERATION
        )
        try {
            val musicResponse = json.decodeFromString<MusicGenerationResponse>(rawResponse)
            val baseResp = musicResponse.base_resp
            if (baseResp != null && baseResp.status_code != null && baseResp.status_code != 0) {
                val msg = baseResp.status_msg ?: "Music generation failed (code: ${baseResp.status_code})"
                Log.e(TAG, "Music API error: ${baseResp.status_code} ${baseResp.status_msg}")
                onError(msg)
                return@withContext
            }
            val taskId = musicResponse.task_id
            if (taskId == null) {
                onError("No task_id in music generation response")
                return@withContext
            }

            // Poll for completion
            pollForTaskCompletion(
                taskId = taskId,
                tool = TOOL_MUSIC_GENERATION,
                onSuccess = onSuccess,
                onError = onError
            )
        } catch (e: kotlinx.serialization.MissingFieldException) {
            Log.e(TAG, "Music generation failed - checking base_resp: $rawResponse")
            try {
                val errorResp = json.decodeFromString<MusicGenerationResponse>(rawResponse)
                val errBaseResp = errorResp.base_resp
                val msg = if (errBaseResp?.status_msg != null) {
                    "Music generation failed: ${errBaseResp.status_msg}"
                } else {
                    "Music generation failed (code: ${errBaseResp?.status_code ?: "unknown"})"
                }
                Log.e(TAG, msg)
                onError(msg)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not parse base_resp either: $rawResponse")
                onError("Music generation failed: ${e.message ?: "Unknown error"}")
            }
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
                        TOOL_GENERATE_IMAGE -> statusResponse.data?.image_urls?.firstOrNull()
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
            TOOL_GENERATE_IMAGE -> "/v1/images/generation/retrieve"
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
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "executeRequest: ENTRY endpoint=$endpoint")
        val apiKey = getApiKey(tool)
        val url = "${getBaseUrl()}$endpoint"

        Log.d(TAG, "executeRequest URL: $url")
        Log.d(TAG, "executeRequest headers: Authorization=Bearer [redacted], Content-Type=application/json")
        Log.d(TAG, "executeRequest body: $body")

        val requestBody = body.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        Log.d(TAG, "executeRequest: BEFORE client.newCall.execute()")
        val response = client.newCall(request).execute()
        Log.d(TAG, "executeRequest: AFTER client.newCall.execute() response=${response.code}")
        response.use { resp ->
            Log.d(TAG, "HTTP ${resp.code} for $endpoint")
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                Log.e(TAG, "Request failed: ${resp.code} - $errorBody")
                throw Exception("HTTP ${resp.code}: $errorBody")
            }
            val bodyString = resp.body?.string() ?: "{}"
            Log.d(TAG, "executeRequest: response body length=${bodyString.length}")
            bodyString
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
                type = "function",
                function = ToolFunction(
                    name = TOOL_TEXT_TO_AUDIO,
                    description = "Generate audio from text with voice synthesis. Use this for text-to-speech, voice responses with emotion control.",
                    parameters = ToolParameters(
                        type = "object",
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
                type = "function",
                function = ToolFunction(
                    name = TOOL_GENERATE_IMAGE,
                    description = "Generate images from text prompts using AI.",
                    parameters = ToolParameters(
                        type = "object",
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "Detailed description of the image to generate")
                        ),
                        required = listOf("prompt")
                    )
                )
            ),
            ToolDefinition(
                type = "function",
                function = ToolFunction(
                    name = TOOL_MUSIC_GENERATION,
                    description = "Generate music from text prompts.",
                    parameters = ToolParameters(
                        type = "object",
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "Description of the music style, mood, instruments"),
                            "title" to ToolProperty("string", "Optional title for the music")
                        ),
                        required = listOf("prompt")
                    )
                )
            ),
            ToolDefinition(
                type = "function",
                function = ToolFunction(
                    name = TOOL_UNDERSTAND_IMAGE,
                    description = "Analyze images to describe content, objects, text, or answer questions about them.",
                    parameters = ToolParameters(
                        type = "object",
                        properties = mapOf(
                            "image_url" to ToolProperty("string", "URL of the image to analyze"),
                            "prompt" to ToolProperty("string", "Question or instruction about the image")
                        ),
                        required = listOf("image_url", "prompt")
                    )
                )
            ),
            ToolDefinition(
                type = "function",
                function = ToolFunction(
                    name = TOOL_WEB_SEARCH,
                    description = "Search the web for current information, news, or factual data.",
                    parameters = ToolParameters(
                        type = "object",
                        properties = mapOf(
                            "query" to ToolProperty("string", "The search query"),
                            "num_results" to ToolProperty("integer", "Number of results (default 5)")
                        ),
                        required = listOf("query")
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
