package com.greyloop.aurelius.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String,
    val name: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ResponseMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String>? = null
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

// Tool Response models
@Serializable
data class ImageGenerationResponse(
    val id: String? = null,
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val data: ImageGenerationData? = null,
    val metadata: ImageMetadata? = null,
    @SerialName("task_id")
    val task_id: String? = null,
    @SerialName("base_resp")
    val baseResp: BaseResp? = null
)

@Serializable
data class ImageMetadata(
    @SerialName("failed_count")
    val failed_count: String? = null,
    @SerialName("success_count")
    val success_count: String? = null
)

@Serializable
data class ImageGenerationData(
    val image_urls: List<String> = emptyList()
)

@Serializable
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val response_format: String = "url",
    val aspect_ratio: String? = null,
    val n: Int? = null
)

@Serializable
data class TextToAudioRequest(
    val model: String,
    val text: String,
    val stream: Boolean = false,
    val voice_setting: VoiceSetting,
    @SerialName("voice_id")
    val voice_id: String? = null  // Top-level fallback per MiniMax API
)

@Serializable
data class VoiceSetting(
    val voice_id: String,
    val speed: Float? = null,
    val vol: Float? = null,
    val pitch: Float? = null,
    val emotion: String? = null
)

@Serializable
data class TextToAudioResponse(
    val id: String? = null,
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val data: AudioData? = null
)

@Serializable
data class AudioData(
    val flow_url: String? = null,
    val url: String? = null,
    val audio: String? = null
)

@Serializable
data class WebSearchRequest(
    val query: String,
    @SerialName("num_results")
    val numResults: Int = 5
)

@Serializable
data class WebSearchResponse(
    val id: String,
    val choices: List<SearchChoice>
)

@Serializable
data class SearchChoice(
    val index: Int,
    val message: SearchMessage
)

@Serializable
data class SearchMessage(
    val role: String,
    val content: String
)

@Serializable
data class MusicGenerationRequest(
    val model: String,
    val prompt: String,
    val lyrics: String? = null,
    val title: String? = null,
    @SerialName("prompt_tags")
    val promptTags: String? = null
)

@Serializable
data class MusicGenerationResponse(
    val id: String? = null,
    val model: String? = null,
    @SerialName("task_id")
    val task_id: String? = null,
    val status: String? = null,
    @SerialName("base_resp")
    val base_resp: BaseResp? = null
)

@Serializable
data class BaseResp(
    @SerialName("status_code")
    val status_code: Int?,
    @SerialName("status_msg")
    val status_msg: String?
)

@Serializable
data class VideoGenerationRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class VideoGenerationResponse(
    val id: String,
    val model: String,
    val task_id: String,
    val status: String
)

@Serializable
data class TaskStatusResponse(
    val id: String? = null,
    @SerialName("task_id")
    val task_id: String? = null,
    val status: String? = null,
    val data: TaskData? = null
)

@Serializable
data class TaskData(
    val url: String? = null,
    val image_urls: List<String> = emptyList()
)

@Serializable
data class VideoData(
    val url: String? = null
)

@Serializable
data class ImageUnderstandingRequest(
    val model: String,
    val messages: List<ImageMessage>
)

@Serializable
data class ImageMessage(
    val role: String,
    val content: List<ContentPart>
)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

@Serializable
data class ImageUnderstandingResponse(
    val id: String? = null,
    val choices: List<ImageUnderstandingChoice>? = null,
    val content: String? = null,
    @SerialName("base_resp")
    val baseResp: BaseResp? = null
)

@Serializable
data class ImageUnderstandingChoice(
    val index: Int,
    val message: ImageUnderstandingMessage
)

@Serializable
data class ImageUnderstandingMessage(
    val role: String,
    val content: String
)

// ============================================================
// Anthropic API models (for codingPlanKey / sk-cp- keys)
// ============================================================

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val max_tokens: Int = 4096,
    val system: String? = null
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicContentBlock(
    val type: String,
    val text: String? = null
)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)
