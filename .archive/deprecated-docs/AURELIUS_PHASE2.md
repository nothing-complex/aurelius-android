# Aurelius — Phase 2 Development Brief

**Prepared after full code audit + API specification research**  
**Scope:** Bug fixes, API corrections, thinking UI, chat history, settings rework  
**Stack:** Kotlin / PySide6 / Android, MiniMax API, Room DB

---

## Table of Contents

1. [Critical Bugs](#1-critical-bugs)
2. [API Specification Corrections](#2-api-specification-corrections)
3. [Functional Issues](#3-functional-issues)
4. [Thinking & Tool Status UI](#4-thinking--tool-status-ui)
5. [Chat History & Session Management](#5-chat-history--session-management)
6. [Settings Rework](#6-settings-rework)
7. [Polish & UX Gaps](#7-polish--ux-gaps)
8. [Stale / Dead Code Cleanup](#8-stale--dead-code-cleanup)
9. [Implementation Order](#9-implementation-order)
10. [New File Manifest](#10-new-file-manifest)

---

## 1. Critical Bugs

These will cause visible breakage at runtime and should be addressed before anything else.

---

### 1.1 Cursor blink animation never actually blinks

**File:** `ui/MessageAdapter.kt`

**The problem:**  
`cursorRunnable` is a `Runnable` that toggles a `show` boolean every 500ms but never updates the `TextView` text. The `▌` cursor character is written once in `bind()` and is never touched again. The "blinking" effect does not occur — the cursor is permanently visible.

**Root cause:**  
The runnable has no reference to the specific `ViewHolder` it should update. It runs in the background changing state nobody observes.

**Recommended fix:**  
Move the blinking logic into a per-ViewHolder `Handler` that updates its own `TextView` directly, OR use the adapter-level runnable but have it call `notifyItemChanged(idx)` to trigger a rebind of the active streaming item. The second approach is simpler:

```kotlin
private val cursorRunnable = object : Runnable {
    var show = true
    override fun run() {
        show = !show
        val idx = currentList.indexOfLast { it.isStreaming }
        if (idx >= 0) notifyItemChanged(idx)
        cursorHandler.postDelayed(this, 500)
    }
}
```

Then in `bind()`, read the `show` state when setting text:

```kotlin
msg.role == Role.ASSISTANT && msg.isStreaming -> {
    val cursor = if (cursorRunnable.show) "▌" else " "
    textView.text = msg.content + cursor
    cursorRunnable.show = true
    cursorHandler.post(cursorRunnable)
}
```

**Pitfalls:**
- Calling `notifyItemChanged` too frequently (every 500ms) on a large list is cheap in practice because `DiffUtil` is not involved — `notifyItemChanged` goes directly to the specific position. No performance concern.
- If `currentList` is accessed on a non-main thread, you'll get a `ConcurrentModificationException`. Ensure the `cursorHandler` is attached to `Looper.getMainLooper()`, which it already is.
- When the streaming message completes and `isStreaming` becomes `false`, `indexOfLast` returns `-1`. Make sure the runnable stops gracefully: check for `-1` before calling `notifyItemChanged` and call `cursorHandler.removeCallbacks(cursorRunnable)` from `onComplete`.

---

### 1.2 No way to return to settings after first launch

**File:** `ui/SettingsActivity.kt`

**The problem:**  
`SettingsActivity` is the `LAUNCHER` activity and contains a gate: if `setup_complete == true`, it immediately starts `MainActivity` and calls `finish()`. Once the user has set their API key, they can never change it — there is no gear icon, no settings route from `MainActivity`, and no way to recover from an incorrect key without clearing app data.

**Recommended fix:**  
This is resolved as part of the larger UI rework in section 6, but the immediate fix is to add a settings icon (`@drawable/ic_settings`) to the `MainActivity` toolbar that navigates to `SettingsActivity` with a flag that bypasses the auto-redirect:

```kotlin
// In SettingsActivity.onCreate():
val forceShow = intent.getBooleanExtra("force_show", false)
if (!forceShow && isSetupComplete()) {
    startActivity(Intent(this, MainActivity::class.java))
    finish()
    return
}
```

**Pitfalls:**
- If the user changes their API key to something invalid, all subsequent requests will 401. The error message surfaced in the chat bubble (`"Error: HTTP 401: ..."`) should be clear enough, but consider adding a banner or toast with a "Check settings" action link.
- The `setup_complete` flag should remain — its purpose is to skip the setup screen on normal launch, not to prevent the screen from being accessed.

---

### 1.3 `reasoning_split` continuation sends thinking to the UI as raw `<think>` tags

**File:** `data/ChatRepository.kt`

**The problem:**  
In multiple places, reasoning content extracted from the API response is passed directly to `onToken()` wrapped in `<think>...</think>` tags. These render as literal text in the chat bubble. There is no XML/HTML rendering in the `TextView`, so the user sees:

```
<think>
Let me think about this. The user is asking about...
</think>

Here is my answer.
```

**Recommended fix:**  
This is addressed fully in section 4 (Thinking & Tool Status UI). The short-term fix is to strip any `<think>...</think>` content before calling `onToken()`, or suppress it entirely. The full fix replaces this with a styled status label shown while reasoning occurs, which clears when actual response text arrives.

**Pitfalls:**
- The regex `Regex("<think>[\\s\\S]*?</think>")` will correctly strip non-greedy. The existing stripping in `assistantContent` already does this for the continuation message — apply the same pattern to display output.
- Do not strip `<think>` from the `assistantContent` that is sent back to the API in `continueWithToolResults`. The API may require the thinking content to be present in the history for the reasoning chain to remain coherent. Only strip for display purposes.

---

## 2. API Specification Corrections

Every untested tool (`generate_image`, `text_to_audio`, `generate_video`, `music_generation`) has the wrong endpoint path, wrong request body structure, or wrong response model — in most cases all three. These are verified against the official `platform.minimax.io` documentation.

---

### 2.1 Image Generation

**File:** `data/ToolExecutor.kt`, `models/ApiModels.kt`

**Current (broken):**
- Endpoint: `POST /v1/images/generations`
- Response model: `{ "data": [{ "url": "..." }] }` (OpenAI image format)

**Correct per MiniMax docs:**
- Endpoint: `POST /v1/image_generation`
- Response: `{ "data": { "image_urls": ["...", "..."] }, "metadata": { ... } }`

The response structure is completely different. `data` is an object, not an array. The URL array is at `data.image_urls[0]`, not `data[0].url`.

**Corrected models:**

```kotlin
data class ImageRequest(
    val model: String = "image-01",
    val prompt: String,
    val response_format: String = "url",   // Required — omitting returns base64 by default
    val aspect_ratio: String? = null,      // e.g. "16:9", "1:1", "9:16"
    val n: Int = 1
)

data class ImageResponse(
    val id: String?,
    val data: ImageData?,
    val base_resp: BaseResp?
)

data class ImageData(
    val image_urls: List<String>?
)
```

**Corrected executor call:**

```kotlin
val url = imgResp.data?.image_urls?.firstOrNull() ?: ""
onResult(url)
```

**Pitfalls:**
- If `response_format` is omitted, the API returns base64-encoded image data in `data.image_base64`, not a URL. The current response parser will silently return an empty string. Always include `response_format: "url"`.
- The `n` parameter controls how many images are generated. Default to 1 to keep costs predictable.
- Image URLs are time-limited (the docs do not specify duration for image generation specifically, but treat them as ephemeral). Do not persist URLs — if chat history needs to reference generated images, consider storing the prompt instead and noting that regeneration is required.

---

### 2.2 Text-to-Speech (TTS)

**File:** `data/ToolExecutor.kt`, `models/ApiModels.kt`

**Current (broken):**
- Endpoint: `POST /v1/audio/speech`
- Request: flat fields `input`, `voice`, `speed`, `vol`, `pitch`, `emotion`
- Response: `{ "data": { "speech_file": "..." } }` — field does not exist

**Correct per MiniMax docs:**
- Endpoint: `POST /v1/t2a_v2`
- Request: nested `voice_setting` and `audio_setting` objects
- Response: `{ "data": { "audio": "<hex or url>", "status": 2 }, "extra_info": { ... } }`

The response audio field is **hex-encoded binary audio** by default, not a URL. To get a URL, add `"output_format": "url"` to the request. The URL is valid for 9 hours.

**Corrected models:**

```kotlin
data class SpeechRequest(
    val model: String = "speech-02-hd",
    val text: String,
    val stream: Boolean = false,
    val output_format: String = "url",           // "url" or "hex"
    val voice_setting: VoiceSetting,
    val audio_setting: AudioSetting? = null
)

data class VoiceSetting(
    val voice_id: String = "female-shaonv",
    val speed: Float = 1.0f,
    val vol: Float = 1.0f,
    val pitch: Int = 0,
    val emotion: String? = null                  // "happy", "sad", "angry", "neutral"
)

data class AudioSetting(
    val sample_rate: Int = 32000,
    val bitrate: Int = 128000,
    val format: String = "mp3"
)

data class SpeechResponse(
    val data: SpeechData?,
    val base_resp: BaseResp?
)

data class SpeechData(
    val audio: String?,                          // URL string when output_format="url"
    val status: Int?
)
```

**Corrected executor call:**

```kotlin
val url = speechResp.data?.audio ?: ""
onResult(url)
```

The `SpeechArgs` data class also needs updating — the incoming tool call arguments use `text` not `input`, and `voice` maps to `voice_id` inside the nested object:

```kotlin
private data class SpeechArgs(
    val text: String,
    val voice_id: String? = null,
    val speed: Float? = null,
    val emotion: String? = null
)
```

**Pitfalls:**
- If `output_format` is `"hex"`, `data.audio` will contain a long hex string. Parsing it as a URL will silently produce a broken link. Always include `output_format: "url"` and treat the value as a URL string.
- The URL expires after 9 hours. If the user tries to play the audio from a restored chat session the next day, the link will be dead. For the current scope this is acceptable — log a note in the UI if playback fails.
- Voice IDs are case-sensitive (`"female-shaonv"` not `"Female-Shaonv"`). The model may accept an invalid ID without error but produce unexpected audio. Use the default ID as a fallback.
- The `emotion` field is only honoured by certain models. `speech-02-hd` supports it; older variants may silently ignore it.

---

### 2.3 Video Generation

**File:** `data/ToolExecutor.kt`, `models/ApiModels.kt`

**Current (completely broken):**
- Endpoint: `POST /v1/videos/generations`
- Assumes synchronous response with `{ "data": { "video_url": "..." } }`
- Model name: `"video-01"` (does not exist)

**Correct per MiniMax docs:**  
Video generation is **fully asynchronous**. It is a three-step process:

1. `POST /v1/video_generation` → returns `{ "task_id": "..." }`
2. `GET /v1/query/video_generation?task_id=...` → poll every ~10 seconds until `status == "Success"`, returns `{ "file_id": "..." }`
3. `GET /v1/files/retrieve_content?file_id=...` → returns the video file or a download URL

The `task_id` from step 1 never contains a video URL. Step 1's response body has only `task_id`. The current code tries to parse a `video_url` from step 1 — it will always be null.

Current supported model names: `MiniMax-Hailuo-2.3`, `MiniMax-Hailuo-2.3-Fast`, `MiniMax-Hailuo-02`.

**Corrected models:**

```kotlin
data class VideoRequest(
    val model: String = "MiniMax-Hailuo-2.3",
    val prompt: String,
    val duration: Int? = null,       // 6 or 10 seconds
    val resolution: String? = null   // "768P" or "1080P"
)

data class VideoTaskResponse(
    val task_id: String?,
    val base_resp: BaseResp?
)

data class VideoStatusResponse(
    val task_id: String?,
    val status: String?,             // "Preparing", "Processing", "Success", "Fail"
    val file_id: String?,
    val base_resp: BaseResp?
)
```

**Corrected executor flow:**

```kotlin
private fun generateVideo(toolCall: ToolCall, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val args = gson.fromJson(toolCall.function.arguments, VideoArgs::class.java)
    val body = VideoRequest(prompt = args.prompt, duration = args.duration, resolution = args.resolution)
    val request = buildRequest("/v1/video_generation", gson.toJson(body))

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Video submission failed")

        override fun onResponse(call: Call, response: Response) {
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) { onError("Video API ${response.code}: $respBody"); return }
            val taskResp = gson.fromJson(respBody, VideoTaskResponse::class.java)
            val taskId = taskResp.task_id ?: run { onError("No task_id in video response"); return }
            pollVideoTask(taskId, onResult, onError)
        }
    })
}

private fun pollVideoTask(taskId: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val maxAttempts = 30   // 30 × 10s = 5 minutes max
    var attempts = 0

    fun poll() {
        if (attempts >= maxAttempts) { onError("Video generation timed out after 5 minutes"); return }
        attempts++
        val request = Request.Builder()
            .url("${baseUrl}/v1/query/video_generation?task_id=$taskId")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Polling failed")
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val status = gson.fromJson(body, VideoStatusResponse::class.java)
                when (status.status) {
                    "Success" -> {
                        val fileId = status.file_id ?: run { onError("No file_id on success"); return }
                        fetchVideoFile(fileId, onResult, onError)
                    }
                    "Fail" -> onError("Video generation failed")
                    else -> {
                        // Still processing — wait 10 seconds and retry
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({ poll() }, 10_000)
                    }
                }
            }
        })
    }
    poll()
}

private fun fetchVideoFile(fileId: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val request = Request.Builder()
        .url("${baseUrl}/v1/files/retrieve?file_id=$fileId")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "File fetch failed")
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: ""
            // Response contains download_url or similar — parse and return
            val url = org.json.JSONObject(body).optJSONObject("file")?.optString("download_url") ?: ""
            if (url.isBlank()) onError("No download URL in file response") else onResult(url)
        }
    })
}
```

**Pitfalls:**
- Video generation typically takes 60–120 seconds. The `OkHttpClient` has a `readTimeout` of 120 seconds, but that timeout applies per HTTP call, not across the polling loop. The polling loop itself has no timeout unless you add `maxAttempts`. Keep `maxAttempts` at 30 (5 minutes) to handle slow generation gracefully.
- The `Handler.postDelayed` approach posts back to the main thread. This is safe but means the polling runs on the main looper. For a production app, move this to a `coroutine` with `delay(10_000)` instead — cleaner and cancellable.
- `status` values from the docs are: `"Preparing"`, `"Processing"`, `"Success"`, `"Fail"`. Treat any unrecognised status as "still running" rather than an error, in case MiniMax adds new intermediate states.
- The `readTimeout` on the shared `OkHttpClient` should be increased to at least 30 seconds for polling calls (the default poll response should be near-instant, but network variability can cause delays).
- The `ViewModel` will show "Generating video…" in the thinking label for the full duration. This is correct behaviour — do not set a shorter label timeout.

---

### 2.4 Music Generation

**File:** `data/ToolExecutor.kt`, `models/ApiModels.kt`

**Current (broken):**
- Endpoint: `POST /v1/musicGeneration`
- Response model expects `{ "data": { "music_url": "..." } }` — field does not exist
- Model name: `"music-01"` (outdated)

**Correct per MiniMax docs:**
- Endpoint: `POST /v1/music_generation`
- Response: `{ "data": { "audio": "<hex or url>", "status": 2 }, "extra_info": { ... } }`
- Current model: `"music-2.6"` (free tier: `"music-2.6-free"`)

The response structure is identical to TTS — `data.audio` contains the audio, either as hex or as a URL depending on `output_format`.

**Corrected models:**

```kotlin
data class MusicRequest(
    val model: String = "music-2.6",
    val prompt: String,
    val lyrics: String? = null,
    val output_format: String = "url",     // "url" or "hex"
    val audio_setting: AudioSetting? = null
)

data class MusicResponse(
    val data: MusicData?,
    val base_resp: BaseResp?
)

data class MusicData(
    val audio: String?,                    // URL when output_format="url"
    val status: Int?
)
```

**Corrected executor call:**

```kotlin
val url = musicResp.data?.audio ?: ""
onResult(url)
```

**Pitfalls:**
- `"music-2.6"` is restricted to Token Plan and paid users. Users on the free tier will receive a permissions error. Use `"music-2.6-free"` as the default and note in settings/documentation that the free tier has lower RPM (requests per minute) limits.
- The `lyrics` field is optional. If omitted and `is_instrumental` is not set, MiniMax will attempt to generate lyrics automatically from the prompt. For predictable behaviour, either always supply lyrics or always set `is_instrumental: true`.
- Music generation can take 20–40 seconds. The 120-second `readTimeout` is sufficient, but this is a synchronous call unlike video — there is no polling. If the generation exceeds the timeout, `OkHttp` will throw a `SocketTimeoutException`. Handle it in `onFailure` with a user-friendly message.

---

### 2.5 Base URL inconsistency

**Files:** `data/ChatRepository.kt`, `data/ToolExecutor.kt`, `ui/SettingsActivity.kt`, `res/layout/activity_settings.xml`

**The problem:**  
The Settings UI labels the global region as `api.minimaxi.chat`, but both `ChatRepository` and `ToolExecutor` set `BASE_GLOBAL = "https://api.minimax.io"`. These are two different domains.

**Correct domains per MiniMax documentation:**
- Global: `https://api.minimax.io`
- China: `https://api.minimax.chat`

**Fix:**  
Update the radio button label in `activity_settings.xml` from `"Global (api.minimaxi.chat)"` to `"Global (api.minimax.io)"`. The code is correct — only the UI label is wrong.

**Pitfall:**  
`api.minimaxi.chat` (with an extra `i`) is an older domain that may still resolve, which is why this discrepancy was not caught during testing. Do not rely on it — use `api.minimax.io` explicitly.

---

## 3. Functional Issues

These are issues that cause incorrect behaviour in specific scenarios rather than outright crashes.

---

### 3.1 Tool continuation does not handle chained tool calls

**File:** `data/ChatRepository.kt`

**The problem:**  
`continueWithToolResults()` sends the tool results back to MiniMax and then renders whatever text is in the response. If MiniMax's continuation response itself contains another round of `tool_calls` (e.g., the model searches the web, reads a result, and decides it needs to search again), those tool calls are silently dropped. The displayed text will be empty or incomplete.

**Recommended fix:**  
After parsing the continuation response, check for tool calls using the same two-location check used in `streamChat()`. If tool calls are found, recurse into `executeToolCalls()`. Add a `roundsRemaining` parameter to prevent infinite loops:

```kotlin
private fun continueWithToolResults(
    toolResults: List<ToolResultMessage>,
    assistantMessageContent: String,
    assistantToolCallsJson: String,
    originalMessages: List<ApiMessage>,
    roundsRemaining: Int = 5,           // ← add this
    onToken: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    // ... existing logic ...
    // After parsing response:
    val newToolCalls = message?.tool_calls
    if (!newToolCalls.isNullOrEmpty() && roundsRemaining > 0) {
        executeToolCalls(newToolCalls, "", gson.toJson(newToolCalls), messagesWithTools,
            roundsRemaining - 1, onToken, onComplete, onError)
        return
    }
    // Otherwise display text as before
}
```

**Pitfall:**  
Without `roundsRemaining`, a misbehaving model or a prompt that always triggers tool use could create an infinite loop that drains API credits. Five rounds is generous for web search + synthesis workflows.

---

### 3.2 Two separate `OkHttpClient` instances

**Files:** `data/ChatRepository.kt`, `data/ToolExecutor.kt`

**The problem:**  
Each class creates its own `OkHttpClient` with identical settings. `OkHttpClient` manages a connection pool and thread pool internally — creating two instances doubles resource usage unnecessarily.

**Recommended fix:**  
Create a single `OkHttpClient` at the application level and pass it as a constructor parameter to both classes. The cleanest place is an `AppModule` singleton:

```kotlin
object AppModule {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
```

**Pitfall:**  
If you later add interceptors (e.g., for logging), do it on the shared instance so all calls are covered. Adding an interceptor to only one client instance creates asymmetric behaviour that is hard to debug.

---

### 3.3 `parseLegacyToolCalls` regex produces duplicate `TextBlock` entries

**File:** `data/ChatRepository.kt`

**The problem:**  
The `textPattern` used in `parseLegacyToolCalls` is:

```kotlin
val textPattern = Regex("([^<]*?)(?=<tool_call>|$)", RegexOption.DOT_MATCHES_ALL)
```

The `?` makes the group non-greedy AND optional, which means it will match empty strings throughout the content. Combined with `DOT_MATCHES_ALL`, this generates many empty `TextBlock` entries and duplicates the visible text.

This is labelled as a legacy format handler, but if it ever activates it will produce garbled output.

**Recommended fix:**  
Replace the regex approach with a simple split:

```kotlin
private fun parseLegacyToolCalls(content: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val toolCallPattern = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
    var lastEnd = 0
    for (match in toolCallPattern.findAll(content)) {
        val textBefore = content.substring(lastEnd, match.range.first).trim()
        if (textBefore.isNotEmpty()) blocks.add(ContentBlock.TextBlock(textBefore))
        // parse tool call XML...
        lastEnd = match.range.last + 1
    }
    val trailing = content.substring(lastEnd).trim()
    if (trailing.isNotEmpty()) blocks.add(ContentBlock.TextBlock(trailing))
    return blocks
}
```

**Pitfall:**  
If MiniMax has fully deprecated this format (which appears to be the case), this code path may never be hit. Low priority, but if it activates unexpectedly the duplicated text output is confusing.

---

## 4. Thinking & Tool Status UI

**New feature requested:** Display a brief, styled status label in the streaming message bubble while the model is reasoning or executing a tool, in place of the current `<think>` tag dump.

---

### 4.1 Overview

The target behaviour mirrors Claude's own interface: while reasoning occurs, the bubble shows an italic, muted label such as *Thinking…* with the blinking cursor. When a tool executes, the label updates to describe what is happening — *Searching for "red pandas"…*. When actual response text arrives, the label clears and normal streaming resumes.

---

### 4.2 `Message.kt` — add `thinkingLabel`

```kotlin
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
    val thinkingLabel: String? = null      // null = no label; non-null = show label
)
```

The `thinkingLabel` is separate from `content`. When `thinkingLabel` is non-null and `isStreaming` is true, the bubble shows only the label. When `content` starts arriving, `thinkingLabel` is set to `null` and content is shown. This means thinking and content never overlap.

---

### 4.3 `ChatRepository.kt` — add `onThinking` callback

Add `onThinking: (String) -> Unit` to `streamChat()` and `continueWithToolResults()`. An empty string signals "done thinking, clear the label".

Replace all existing `onToken("<think>...</think>")` calls:

```kotlin
// BEFORE — dumps raw tags to UI
val reasoningText = message?.reasoning_details?.firstOrNull()?.get("text") as? String
if (!reasoningText.isNullOrBlank()) {
    onToken("<think>$reasoningText</think>\n")
}

// AFTER — signals the label
if (!reasoningText.isNullOrBlank()) {
    onThinking("Thinking…")
}
```

Add a `toolLabel()` helper that generates human-readable labels per tool:

```kotlin
private fun toolLabel(toolCall: ToolCall): String {
    val args = try { org.json.JSONObject(toolCall.function.arguments) } catch (e: Exception) { null }
    return when (toolCall.function.name) {
        "web_search" -> {
            val q = args?.optString("query", "")?.take(40)?.trim() ?: ""
            if (q.isNotBlank()) "Searching for "$q"…" else "Searching the web…"
        }
        "generate_image"   -> "Generating image…"
        "generate_video"   -> "Generating video…"
        "text_to_audio"    -> "Generating audio…"
        "music_generation" -> "Generating music…"
        else               -> "Using tool…"
    }
}
```

Call `onThinking(toolLabel(toolCall))` at the start of `executeNext()`, before dispatching to `toolExecutor`.

Call `onThinking("")` at the start of `continueWithToolResults` response handling, before `onToken()`.

---

### 4.4 `ChatViewModel.kt` — wire `onThinking`

```kotlin
onThinking = { label ->
    viewModelScope.launch(Dispatchers.Main) {
        val list = _messages.value.orEmpty().toMutableList()
        val idx = list.indexOfLast { it.isStreaming }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                thinkingLabel = label.ifEmpty { null }
            )
            _messages.value = list
        }
    }
},
onToken = { token ->
    viewModelScope.launch(Dispatchers.Main) {
        val list = _messages.value.orEmpty().toMutableList()
        val idx = list.indexOfLast { it.isStreaming }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                content = list[idx].content + token,
                thinkingLabel = null           // Clear label the moment content arrives
            )
            _messages.value = list
        }
    }
},
```

---

### 4.5 `MessageAdapter.kt` — styled label rendering

In `bind()`, add a third case for the thinking state:

```kotlin
fun bind(msg: Message) {
    textView.removeCallbacks(cursorRunnable)
    cursorHandler.removeCallbacks(cursorRunnable)

    when {
        msg.role == Role.ASSISTANT && msg.isStreaming && msg.thinkingLabel != null -> {
            val label = msg.thinkingLabel
            val cursor = if (cursorRunnable.show) " ▌" else "  "
            val full = "$label$cursor"
            val span = SpannableString(full)
            span.setSpan(
                StyleSpan(android.graphics.Typeface.ITALIC),
                0, label.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            span.setSpan(
                ForegroundColorSpan(0xFF888888.toInt()),
                0, full.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            textView.text = span
            cursorRunnable.show = true
            cursorHandler.post(cursorRunnable)
        }
        msg.role == Role.ASSISTANT && msg.isStreaming -> {
            val cursor = if (cursorRunnable.show) "▌" else " "
            textView.text = msg.content + cursor
            cursorRunnable.show = true
            cursorHandler.post(cursorRunnable)
        }
        else -> {
            textView.text = msg.content
        }
    }
}
```

**Pitfalls:**
- `SpannableString` is created on every `bind()` call, including the 500ms blink redraws. This is cheap but if profiling shows GC pressure, cache the last-built span and only rebuild when `thinkingLabel` changes.
- `ForegroundColorSpan(0xFF888888.toInt())` hardcodes a grey. This works on the dark theme (`#0A0A0A` background) but would be nearly invisible on a light background. Consider using a theme attribute or a resource colour.
- The label text length is used for the italic span range. If `label` is somehow null at span construction time (it should not be, given the non-null check in `when`), the app will crash. The Kotlin smart cast should prevent this, but add a `?: return` safety if in doubt.

---

## 5. Chat History & Session Management

**New feature requested:** Chat sessions persist across app launches. On launch, five recent chats with one-line summaries are shown. A swipeable sidebar lists all previous chats grouped by date.

---

### 5.1 Data layer — Room database

Add to `build.gradle.kts`:

```kotlin
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

Enable `kapt` plugin at the top of the file if not already present:

```kotlin
plugins {
    ...
    kotlin("kapt")
}
```

**Entities:**

```kotlin
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("chatId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

**DAOs:**

```kotlin
@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC LIMIT 5")
    fun getRecentChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title, updatedAt = :ts WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(chat: ChatEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countMessages(chatId: String): Int
}
```

**Database singleton:**

```kotlin
@Database(entities = [ChatEntity::class, MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "aurelius.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

**Pitfalls for Room:**
- Room does not allow database access on the main thread. All DAO calls must be in `suspend` functions or wrapped in `withContext(Dispatchers.IO)`. Forgetting this causes an `IllegalStateException` that can be confusing if you haven't seen it before.
- `Flow` queries from Room are automatically executed on a background thread and delivered to the collector — you do not need `withContext` for reads when collecting a `Flow`. Only writes need explicit dispatcher switching.
- The `@Index("chatId")` on `MessageEntity` is important. Without it, `getMessages(chatId)` performs a full table scan. As chat history grows, this becomes the dominant query and will noticeably slow down chat loading.
- Database migrations are not covered in this version (schema version 1). When the schema changes in a future version, a `Migration` object must be provided or `fallbackToDestructiveMigration()` must be called — the latter wipes all data. Plan for this early.

---

### 5.2 `HomeActivity` — new launcher

`HomeActivity` replaces `SettingsActivity` as the `LAUNCHER` in `AndroidManifest.xml`. It performs the API key check: if no key is stored, it navigates to `SettingsActivity`. Otherwise it shows the home screen.

**Layout structure (`activity_home.xml`):**

```xml
<androidx.drawerlayout.widget.DrawerLayout>

    <!-- Main content -->
    <ConstraintLayout>
        <Toolbar />                           <!-- "Aurelius" title + gear icon -->
        <TextView text="Recent" />            <!-- Section heading -->
        <RecyclerView id="rvRecentChats" />   <!-- 5 recent chats -->
        <Button id="btnNewChat" />            <!-- "+ New chat" -->
    </ConstraintLayout>

    <!-- Sidebar (slides in from left) -->
    <LinearLayout android:layout_gravity="start" android:layout_width="300dp">
        <TextView text="Conversations" />
        <RecyclerView id="rvAllChats" />      <!-- All chats, grouped by date -->
        <Button id="btnSettings" />           <!-- Settings at the bottom -->
    </LinearLayout>

</androidx.drawerlayout.widget.DrawerLayout>
```

The sidebar opens via swipe from the left edge (handled by `DrawerLayout` automatically) or by tapping a hamburger icon in the toolbar.

**`HomeViewModel.kt`:**

```kotlin
class HomeViewModel(context: Context) : ViewModel() {
    private val db = AppDatabase.getInstance(context)

    val recentChats: StateFlow<List<ChatEntity>> = db.chatDao()
        .getRecentChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChats: StateFlow<List<ChatEntity>> = db.chatDao()
        .getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

**Pitfalls:**
- `SharingStarted.WhileSubscribed(5000)` means the upstream `Flow` is cancelled 5 seconds after the last collector disappears (e.g., the activity is paused). This is the recommended pattern — it prevents unnecessary database polling when the app is in the background.
- The `DrawerLayout` requires that the sidebar child has `android:layout_gravity="start"` (not `"left"`) to handle RTL layouts correctly.
- On first launch with no chats, `rvRecentChats` will be empty. Show an empty state view ("No conversations yet — start one below") rather than a blank space.

---

### 5.3 Chat grouping in the sidebar

The sidebar `RecyclerView` uses two `ViewType`s: `TYPE_HEADER` (date label) and `TYPE_CHAT` (tappable row). The grouping logic runs in the adapter or ViewModel:

```kotlin
sealed class SidebarItem {
    data class Header(val label: String) : SidebarItem()
    data class Chat(val entity: ChatEntity) : SidebarItem()
}

fun List<ChatEntity>.toSidebarItems(): List<SidebarItem> {
    val now = System.currentTimeMillis()
    val today = /* start of today in ms */
    val yesterday = today - 86_400_000L
    val thisWeek = today - 7 * 86_400_000L

    return buildList {
        var lastHeader = ""
        for (chat in this@toSidebarItems) {
            val header = when {
                chat.updatedAt >= today    -> "Today"
                chat.updatedAt >= yesterday -> "Yesterday"
                chat.updatedAt >= thisWeek  -> "This week"
                else                        -> "Older"
            }
            if (header != lastHeader) {
                add(SidebarItem.Header(header))
                lastHeader = header
            }
            add(SidebarItem.Chat(chat))
        }
    }
}
```

**Pitfalls:**
- "Start of today" must be calculated using `Calendar` or `LocalDate` with the device's timezone, not by rounding `System.currentTimeMillis()` to a day boundary in UTC. A chat from 11pm last night is "Yesterday" in local time but may be "Today" if you subtract 24 hours from UTC midnight.
- Long-press to delete should show an `AlertDialog` confirmation before calling `chatDao().delete(chat)`. Deletion cascades to all messages via the `ForeignKey.CASCADE` constraint.

---

### 5.4 `ChatActivity` — session-aware

`ChatActivity` (renamed from `MainActivity`) receives a `chatId` String via `Intent.getStringExtra("chat_id")`. If the string is a new UUID generated by `HomeActivity`, a new `ChatEntity` is inserted on first message. If it matches an existing chat, messages are loaded from Room and prepopulated into `_messages`.

**`ChatViewModel` changes:**

```kotlin
class ChatViewModel(
    private val context: Context,
    private val chatId: String
) : ViewModel() {

    private val db = AppDatabase.getInstance(context)
    private val repository = ChatRepository(context)

    init {
        // Load existing messages from Room
        viewModelScope.launch {
            db.messageDao().getMessages(chatId)
                .collect { entities ->
                    if (!_isLoading.value) {    // Don't clobber an active stream
                        _messages.value = entities.map { it.toMessage() }
                    }
                }
        }
    }

    fun sendMessage(userText: String) {
        // ... existing logic ...

        // Persist user message immediately
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().insert(MessageEntity(
                id = userMsg.id,
                chatId = chatId,
                role = "user",
                content = userText
            ))
            db.chatDao().touch(chatId)
        }

        // After onComplete, persist assistant message:
        // db.messageDao().insert(MessageEntity(...))
        // If this is the first assistant message, generate a title
    }
}
```

---

### 5.5 AI-generated chat titles

After the first assistant response completes in a new chat, fire a lightweight API call to generate a 5-word title. This runs in a background coroutine and never blocks the UI.

**In `ChatRepository.kt`:**

```kotlin
suspend fun generateChatTitle(userMessage: String, assistantReply: String): String {
    val prompt = "In 5 words or fewer, summarise this conversation. " +
                 "Reply with only the title. No punctuation, no quotes.\n\n" +
                 "User: ${userMessage.take(200)}\n" +
                 "Assistant: ${assistantReply.take(200)}"

    val body = ChatRequest(
        messages = listOf(ApiMessage(role = "user", content = prompt)),
        stream = false,
        tools = null,
        reasoning_split = false,
        model = "MiniMax-M2.7-highspeed",    // Fast model, no reasoning needed
        temperature = 0.3f
    )

    // Synchronous call — this is already on Dispatchers.IO
    val response = /* OkHttp synchronous execute */
    return parsePlainTextResponse(response).take(60).trim()
}
```

**In `ChatViewModel.kt`:**

```kotlin
private var titleGenerated = false

// In onComplete callback, after persisting assistant message:
if (!titleGenerated) {
    titleGenerated = true
    viewModelScope.launch(Dispatchers.IO) {
        val title = repository.generateChatTitle(firstUserMessage, assistantContent)
        db.chatDao().updateTitle(chatId, title)
    }
}
```

**Pitfalls:**
- `take(200)` on the input messages prevents sending the full conversation to a title-generation call. Titles should be derived from the opening exchange, not the entire thread.
- The title generation call costs a small number of tokens on every new conversation. This is acceptable but worth noting — on the free tier with tight rate limits, this may occasionally 429 alongside the main chat call. Wrap in a `try/catch` and fall back to `"New chat"` silently.
- `temperature = 0.3f` produces more deterministic, factual titles. Higher temperatures produce creative but sometimes inaccurate summaries.
- `model = "MiniMax-M2.7-highspeed"` is fastest and cheapest for this task. No reasoning capability is needed.

---

## 6. Settings Rework

**Requested:** Settings must be accessible at any time, not only on first launch. API key should be editable. Region toggle should persist correctly.

---

### 6.1 Remove the first-launch gate

In `SettingsActivity.onCreate()`, delete the `isSetupComplete()` auto-redirect entirely. `SettingsActivity` is now a normal screen navigated to explicitly — it should always show its UI.

Pre-populate the API key field with the stored value on load. Mask it as a password field (`inputType="textPassword"`) but allow the user to toggle visibility with a show/hide eye icon.

Change the "Continue" button to "Save". On save, update SharedPreferences and show a brief `Toast("Settings saved")`. Do not navigate away automatically — let the user press back.

**`SettingsActivity.kt` key changes:**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // REMOVED: isSetupComplete() check

    binding = ActivitySettingsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Pre-populate existing values
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    binding.etApiKey.setText(prefs.getString(KEY_API_KEY, ""))
    val region = prefs.getString(KEY_REGION, "global")
    if (region == "china") binding.rbChina.isChecked = true
    else binding.rbGlobal.isChecked = true

    binding.btnSave.setOnClickListener {
        val apiKey = binding.etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val selectedRegion = if (binding.rbChina.isChecked) "china" else "global"
        saveSettings(apiKey, selectedRegion)
        // Mark setup as complete in case this is the first launch
        markSetupComplete()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
}
```

**`AndroidManifest.xml`:**

`SettingsActivity` is no longer the `LAUNCHER`. Remove the `<intent-filter>` from it and add it to `HomeActivity`:

```xml
<activity android:name=".ui.HomeActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<activity android:name=".ui.SettingsActivity" android:exported="false" />
```

**Pitfalls:**
- Since `SettingsActivity` is now `exported="false"`, it can only be started by the app itself. This is correct — there is no reason for an external app to launch the settings screen.
- If the user changes the API key or region while a chat is in progress in `ChatActivity`, the new values are read on the *next* request because `baseUrl` and `apiKey` are computed properties that read SharedPreferences on each access. This is the desired behaviour — no restart required.
- The `setup_complete` preference is retained so `HomeActivity` can still determine whether to show `SettingsActivity` on first launch.

---

## 7. Polish & UX Gaps

These are not blocking issues but meaningfully affect the user experience.

---

### 7.1 No Markdown rendering in chat bubbles

**Current:** `TextView` displays raw asterisks, hashes, and backticks. `**bold**` is rendered as literal `**bold**`.

**Recommended fix:** Add the Markwon library:

```kotlin
implementation("io.noties.markwon:markwon:4.6.2")
```

Usage in `MessageAdapter.bind()`:

```kotlin
val markwon = Markwon.create(itemView.context)
markwon.setMarkdown(textView, msg.content)
```

**Pitfall:** Markwon is incompatible with `SpannableString` operations on the same `TextView`. The thinking label (section 4.5) uses `SpannableString` directly. Since thinking labels and rendered Markdown never appear simultaneously, this is safe — apply `markwon.setMarkdown()` only when `thinkingLabel == null` and `!isStreaming`.

**Pitfall:** Markwon processes the entire string on each `bind()` call. During streaming, `bind()` is called on every token arrival — potentially hundreds of times. This adds measurable CPU overhead. Consider throttling Markdown rendering during active streaming (render at most every 500ms, or only on `onComplete`). Apply plain `textView.text = msg.content + cursor` during streaming and render Markdown only once on completion.

---

### 7.2 `item_message_assistant.xml` uses fixed `maxWidth`

**Current:** `android:maxWidth="320dp"` — will look narrow on tablets and large phones.

**Recommended fix:** Replace the fixed `maxWidth` with a percentage-based constraint. In a `ConstraintLayout` parent, use `app:layout_constraintWidth_percent="0.85"` and `app:layout_constraintWidth_default="wrap"`.

---

### 7.3 `inputContainer` layout is fragile

**Current:** The `inputContainer` is a `LinearLayout` used purely as a background/border anchor. The `EditText`, `ProgressBar`, and `btnSend` are `ConstraintLayout` siblings positioned against it rather than children inside it.

This works currently but any change to `inputContainer`'s dimensions or position will break the layout of the three sibling views without any compile-time warning.

**Recommended fix:** Move `EditText`, `ProgressBar`, and `btnSend` inside the `inputContainer` `LinearLayout` as children, which is the semantically correct structure. Add the 1dp top border as a `<View>` with `layout_height="1dp"` positioned outside and above the container.

---

### 7.4 No empty-state view in chat list

When the app is freshly installed and there are no previous chats, `rvRecentChats` is empty and the screen shows blank space. Add a `TextView` or illustrated empty state with copy such as "No conversations yet" that is shown when the list is empty and hidden when items exist.

---

## 8. Stale / Dead Code Cleanup

These items are safe to remove without affecting functionality.

---

### 8.1 `BACKEND_URL` in `build.gradle.kts`

The `buildConfigField("String", "BACKEND_URL", ...)` line in `defaultConfig` generates a `BuildConfig.BACKEND_URL` constant that is no longer referenced anywhere in the app. Remove it.

```kotlin
// DELETE THIS:
buildConfigField(
    "String",
    "BACKEND_URL",
    "\"${project.findProperty("BACKEND_URL") ?: "http://10.0.2.2:3000"}\""
)
```

---

### 8.2 `network_security_config.xml` (if still present)

The PLAN.md noted this file for deletion. Confirm it is absent. If present, remove it and ensure `AndroidManifest.xml` does not reference it. The app is HTTPS-only (`usesCleartextTraffic="false"`), which is correct.

---

### 8.3 `local.properties` and `gradle.properties` `BACKEND_URL` entries

The PLAN.md noted `BACKEND_URL=http://10.0.2.2:3000` in `local.properties`. Confirm it is absent. `gradle.properties` similarly. `local.properties` is gitignored by default — verify the `.gitignore` covers it.

---

### 8.4 `ChatRequest.stream` is always `false`

`stream` is a field on `ChatRequest` and is hardcoded to `false` on every call. The original intent was to use streaming SSE, which was dropped because SSE parsing on Android proved unreliable. If streaming is not planned for a future version, remove the field from `ChatRequest` and from the serialised JSON. If it may return, document the reason it is disabled.

---

## 9. Implementation Order

The following order minimises regressions — each phase leaves the app in a working state.

**Phase A — Stability (complete before adding features)**

1. Fix cursor blink (1.1)
2. Fix `<think>` tag display (1.3 / 4)
3. Correct all four tool API endpoints and response models (2.1–2.4)
4. Fix base URL label inconsistency (2.5)
5. Remove dead `BACKEND_URL` code (8.1)

**Phase B — Thinking UI (self-contained, no dependency on other phases)**

6. Add `thinkingLabel` to `Message.kt`
7. Add `onThinking` to `ChatRepository`, `ChatViewModel`, `MessageAdapter`
8. Test with web search (confirmed working) and image generation (after Phase A fixes)

**Phase C — Chat History**

9. Add Room dependency, create entities and DAOs, build `AppDatabase`
10. Build `HomeActivity`, `HomeViewModel`, layouts
11. Update `ChatViewModel` to write to Room on every message, load on init
12. Add title generation (can be added after the rest of history works)
13. Update `AndroidManifest.xml` to make `HomeActivity` the launcher

**Phase D — Settings Rework**

14. Remove the first-launch gate from `SettingsActivity`
15. Add settings navigation from `HomeActivity` toolbar and sidebar
16. Update `AndroidManifest.xml`

**Phase E — Polish**

17. Add Markwon, with streaming throttle
18. Fix `item_message_assistant.xml` max width
19. Fix `inputContainer` layout structure
20. Add empty state views

---

## 10. New File Manifest

| File | Status | Purpose |
|---|---|---|
| `data/db/AppDatabase.kt` | New | Room database singleton |
| `data/db/ChatDao.kt` | New | Chat table queries |
| `data/db/MessageDao.kt` | New | Message table queries |
| `data/db/ChatEntity.kt` | New | Chat table entity |
| `data/db/MessageEntity.kt` | New | Message table entity |
| `data/ChatStorage.kt` | New | Optional thin wrapper around both DAOs |
| `ui/HomeActivity.kt` | New | Launcher screen with recent chats + sidebar |
| `ui/HomeViewModel.kt` | New | Observes Room Flows for home + sidebar lists |
| `ui/RecentChatAdapter.kt` | New | 5-card recent chat list |
| `ui/SidebarChatAdapter.kt` | New | Grouped all-chats list |
| `res/layout/activity_home.xml` | New | DrawerLayout root for home screen |
| `res/layout/nav_sidebar.xml` | New | Sidebar content |
| `res/layout/item_chat_preview.xml` | New | Shared card row for both lists |
| `res/layout/item_date_header.xml` | New | Date group header for sidebar |
| `data/ChatRepository.kt` | Modify | Add `onThinking`, `generateChatTitle`, fix tool endpoints |
| `data/ToolExecutor.kt` | Modify | Fix all four tool APIs |
| `models/ApiModels.kt` | Modify | Fix response models for all four tools |
| `models/Message.kt` | Modify | Add `thinkingLabel` field |
| `ui/ChatViewModel.kt` | Modify | Add `chatId`, Room writes, `onThinking` wiring |
| `ui/MessageAdapter.kt` | Modify | Fix cursor blink, add thinking label rendering |
| `ui/SettingsActivity.kt` | Modify | Remove first-launch gate, add pre-population |
| `MainActivity.kt` | Rename | Rename to `ChatActivity.kt` for clarity |
| `AndroidManifest.xml` | Modify | Swap launcher to `HomeActivity`, update exports |
| `android/app/build.gradle.kts` | Modify | Remove `BACKEND_URL`, add Room + kapt |

---

*End of document.*
