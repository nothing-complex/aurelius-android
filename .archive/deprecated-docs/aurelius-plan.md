# Aurelius — Build Plan & Claude Code Prompt

---

## What changed from v1

- Removed fal.ai entirely
- Removed Brave Search entirely
- Added two official MiniMax MCP servers that cover everything:
  - `minimax-mcp-js` (npx) → image gen, video gen, TTS, voice cloning, voice design, music gen
  - `minimax-coding-plan-mcp` (uvx/Python) → web search, image understanding
- Both use the same MINIMAX_API_KEY — no extra third-party accounts needed

---

## Architecture

```
Android App (Kotlin)
        ↕  REST + SSE streaming
Backend (Bun + TypeScript + Hono)
        ↕  MiniMax Chat Completions API (function calling)
        ↕  MCP client (TypeScript SDK, stdio transport)
              ├─ minimax-mcp-js        (npx)  → image gen, video, TTS, music
              └─ minimax-coding-plan   (uvx)  → web search, image analysis
```

## Important: region alignment

MiniMax has two API regions. Key and host MUST match:
- Global:   MINIMAX_API_HOST=https://api.minimaxi.chat
- Mainland: MINIMAX_API_HOST=https://api.minimax.chat

Check your dashboard to confirm which region your key belongs to.

---

## MCP tools available after connecting both servers

From minimax-mcp-js:
  text_to_audio        — TTS with voice selection
  list_voices          — list available voices
  voice_cloning        — clone a voice from audio URL
  voice_design         — create custom voice from text description
  generate_image       — text-to-image
  generate_video       — text-to-video (MiniMax-Hailuo-02, 6s/10s, 768P/1080P)
  image_to_video       — animate a still image
  query_video_task     — poll async video generation status
  music_generation     — text-to-music (music-1.5 model)

From minimax-coding-plan-mcp:
  web_search           — organic web results + related queries
  understand_image     — vision analysis via AI, answers questions about images

---

## Backend: one extra dependency

The Coding Plan MCP uses uvx (Python's uv package runner).
Backend startup needs uv installed:

  curl -LsSf https://astral.sh/uv/install.sh | sh

On Railway: add this as a pre-deploy command, or use a Dockerfile.
Locally: just run it once before bun run dev.

---

## File structure

```
aurelius/
├── backend/
│   ├── src/
│   │   ├── index.ts
│   │   ├── mcp-client.ts
│   │   ├── minimax.ts
│   │   ├── orchestrator.ts
│   │   └── types.ts
│   ├── package.json
│   ├── tsconfig.json
│   ├── .env.example
│   └── README.md
└── android/
    └── (identical to v1 — no changes needed on Android side)
```

---

# Claude Code Prompt (v2)

Paste this verbatim into a new Claude Code session.

---

```
Build a two-part project called Aurelius: a Bun/TypeScript backend that proxies MiniMax's LLM
with MiniMax's own MCP servers for web search and multimodal generation, plus
a Kotlin Android chat app named Aurelius that talks to it.
Project root: aurelius/

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PART 1 — BACKEND (aurelius/backend/)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Tech stack:
- Runtime: Bun
- HTTP server: Hono
- MCP client: @modelcontextprotocol/sdk (official TypeScript SDK)
- LLM client: openai npm package pointed at MiniMax's OpenAI-compatible endpoint
- Config: .env file

## Setup

Run:
  mkdir -p aurelius/backend && cd aurelius/backend
  bun init -y
  bun add hono @modelcontextprotocol/sdk openai

package.json scripts:
  "dev": "bun --watch src/index.ts"
  "start": "bun src/index.ts"

## .env.example

MINIMAX_API_KEY=your_minimax_api_key_here
MINIMAX_MODEL=MiniMax-Text-01
MINIMAX_API_HOST=https://api.minimaxi.chat
PORT=3000

# NOTE: MINIMAX_API_HOST must match the region your API key belongs to.
# Global users: https://api.minimaxi.chat
# Mainland China users: https://api.minimax.chat
# Check your MiniMax dashboard if unsure.

## src/types.ts

export interface ChatMessage {
  role: "user" | "assistant" | "system" | "tool";
  content: string;
  tool_call_id?: string;
  name?: string;
}

export interface ChatRequest {
  messages: ChatMessage[];
  stream?: boolean;
}

export interface ToolSchema {
  type: "function";
  function: {
    name: string;
    description: string;
    parameters: Record<string, unknown>;
  };
}

## src/mcp-client.ts

Manages connections to two MiniMax MCP servers via stdio transport.

Import: Client, StdioClientTransport from @modelcontextprotocol/sdk/client/index.js
        and @modelcontextprotocol/sdk/client/stdio.js

Class McpManager:

  private clients: Map<string, Client> = new Map()
  private toolToClientMap: Map<string, string> = new Map()

  async init():
    Connect to minimax-mcp-js (official JS MCP — image gen, video, TTS, music):
      const jsTransport = new StdioClientTransport({
        command: "npx",
        args: ["-y", "minimax-mcp-js"],
        env: {
          ...process.env,
          MINIMAX_API_KEY: process.env.MINIMAX_API_KEY!,
          MINIMAX_API_HOST: process.env.MINIMAX_API_HOST!,
          MINIMAX_RESOURCE_MODE: "url",   // return URLs not local files
        }
      })
      const jsClient = new Client({ name: "minimax-proxy", version: "1.0.0" })
      await jsClient.connect(jsTransport)
      this.clients.set("minimax-js", jsClient)
      console.log("Connected: minimax-mcp-js")

    Connect to minimax-coding-plan-mcp (web search + image understanding):
      const planTransport = new StdioClientTransport({
        command: "uvx",
        args: ["minimax-coding-plan-mcp", "-y"],
        env: {
          ...process.env,
          MINIMAX_API_KEY: process.env.MINIMAX_API_KEY!,
          MINIMAX_API_HOST: process.env.MINIMAX_API_HOST!,
        }
      })
      const planClient = new Client({ name: "minimax-proxy", version: "1.0.0" })
      await planClient.connect(planTransport)
      this.clients.set("minimax-plan", planClient)
      console.log("Connected: minimax-coding-plan-mcp")

    After connecting each client, list its tools and populate toolToClientMap:
      const toolsResult = await client.listTools()
      for (const tool of toolsResult.tools) {
        this.toolToClientMap.set(tool.name, clientKey)
      }
      console.log(`[${clientKey}] tools:`, toolsResult.tools.map(t => t.name))

    Wrap the entire init in try/catch per server — if one fails to connect
    (e.g. uvx not installed), log a clear warning but continue with the
    remaining servers rather than crashing. This allows the backend to work
    with partial capability.

  async getAllTools(): Promise<ToolSchema[]>
    Iterate all connected clients. Call listTools() on each.
    Convert each MCP tool to OpenAI function-calling schema:
      {
        type: "function",
        function: {
          name: tool.name,
          description: tool.description ?? "",
          parameters: tool.inputSchema ?? { type: "object", properties: {} }
        }
      }
    Return flattened deduplicated array (deduplicate by tool name).

  async callTool(toolName: string, args: unknown): Promise<string>
    Look up clientKey from toolToClientMap.
    If not found: return JSON.stringify({ error: `Unknown tool: ${toolName}` })
    Call: const result = await client.callTool({ name: toolName, arguments: args as any })
    Extract text content:
      result.content
        .filter((c: any) => c.type === "text")
        .map((c: any) => c.text)
        .join("\n")
    If result contains image content (type === "image"), return the URL/data URI.
    Return string. Never throw — return error string if anything fails.

Export singleton: export const mcpManager = new McpManager()

## src/minimax.ts

import OpenAI from "openai"

export const minimaxClient = new OpenAI({
  apiKey: process.env.MINIMAX_API_KEY,
  baseURL: `${process.env.MINIMAX_API_HOST ?? "https://api.minimaxi.chat"}/v1`,
})

export const MODEL = process.env.MINIMAX_MODEL ?? "MiniMax-Text-01"

## src/orchestrator.ts

Tool-call loop. Drives MiniMax until final text response.

import { minimaxClient, MODEL } from "./minimax.ts"
import { mcpManager } from "./mcp-client.ts"
import type { ChatMessage, ToolSchema } from "./types.ts"

export async function* runOrchestrator(
  messages: ChatMessage[],
  tools: ToolSchema[]
): AsyncGenerator<string> {
  const history = [...messages]
  const MAX_TOOL_ROUNDS = 6   // web search + image gen can chain a couple rounds

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const response = await minimaxClient.chat.completions.create({
      model: MODEL,
      messages: history as any,
      tools: tools.length > 0 ? tools : undefined,
      tool_choice: tools.length > 0 ? "auto" : undefined,
      stream: false,
    })

    const choice = response.choices[0]

    if (choice.finish_reason === "tool_calls" && choice.message.tool_calls?.length) {
      // Push assistant message with tool_calls intact
      history.push(choice.message as any)

      for (const toolCall of choice.message.tool_calls) {
        console.log(`[tool] calling ${toolCall.function.name}`)
        let args: unknown
        try {
          args = JSON.parse(toolCall.function.arguments)
        } catch {
          args = {}
        }

        const result = await mcpManager.callTool(toolCall.function.name, args)
        console.log(`[tool] ${toolCall.function.name} → ${result.slice(0, 120)}...`)

        history.push({
          role: "tool",
          tool_call_id: toolCall.id,
          name: toolCall.function.name,
          content: result,
        })
      }
      continue   // back to MiniMax with tool results
    }

    // Final response — re-call with streaming enabled
    const finalStream = await minimaxClient.chat.completions.create({
      model: MODEL,
      messages: history as any,
      stream: true,
    })

    for await (const chunk of finalStream) {
      const token = chunk.choices[0]?.delta?.content
      if (token) yield token
    }

    return
  }

  yield "Could not complete the request after the maximum number of tool rounds."
}

## src/index.ts

import { Hono } from "hono"
import { cors } from "hono/cors"
import { mcpManager } from "./mcp-client.ts"
import { runOrchestrator } from "./orchestrator.ts"
import type { ChatRequest } from "./types.ts"

const app = new Hono()
app.use("*", cors())

console.log("Connecting to MiniMax MCP servers...")
await mcpManager.init()
console.log("Ready.")

app.get("/health", (c) => c.json({ status: "ok" }))

app.post("/chat", async (c) => {
  const body = await c.req.json<ChatRequest>()
  const tools = await mcpManager.getAllTools()

  return new Response(
    new ReadableStream({
      async start(controller) {
        const encoder = new TextEncoder()
        try {
          for await (const token of runOrchestrator(body.messages, tools)) {
            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ token })}\n\n`))
          }
          controller.enqueue(encoder.encode(`data: [DONE]\n\n`))
        } catch (err) {
          const msg = err instanceof Error ? err.message : "Unknown error"
          controller.enqueue(encoder.encode(`data: ${JSON.stringify({ error: msg })}\n\n`))
        } finally {
          controller.close()
        }
      }
    }),
    {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
      }
    }
  )
})

export default {
  port: parseInt(process.env.PORT ?? "3000"),
  fetch: app.fetch,
}

## Backend README.md

Include:
1. What this is (2 sentences)
2. Prerequisites:
   - Bun (https://bun.sh)
   - uv — install with: curl -LsSf https://astral.sh/uv/install.sh | sh
     (needed for the Coding Plan MCP server; uvx comes bundled with uv)
   - MiniMax API key with token plan access
3. Setup: cp .env.example .env → fill MINIMAX_API_KEY and confirm MINIMAX_API_HOST
   matches your region → bun install → bun run dev
4. The two MCP servers and what tools each provides (bulleted list)
5. API: POST /chat example with curl
6. Deploying to Railway: set env vars in Railway dashboard, use "bun start" as start
   command, add "curl -LsSf https://astral.sh/uv/install.sh | sh" as a pre-deploy
   command so uvx is available

## Backend self-test

After building:
- [ ] bun install completes without errors
- [ ] bun run dev starts and prints "Connected: minimax-mcp-js" and
      "Connected: minimax-coding-plan-mcp" followed by "Ready."
      (if uvx is not installed, it prints a warning but still starts — acceptable)
- [ ] Console shows the tool list from each server on startup
- [ ] GET localhost:3000/health → { status: "ok" }
- [ ] POST /chat { messages: [{ role: "user", content: "Hello" }] }
      streams back tokens ending with [DONE]
- [ ] POST /chat { messages: [{ role: "user", content: "Search the web for MiniMax AI news" }] }
      logs a web_search tool call and returns web results in the response
- [ ] POST /chat { messages: [{ role: "user", content: "Generate an image of a misty forest" }] }
      logs a generate_image tool call and returns a URL in the response

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PART 2 — ANDROID APP (aurelius/android/)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Identical to v1. Generate a complete Android project. Kotlin, minSdk 26,
targetSdk 34. The app connects to the backend and renders a streaming chat UI.
Android does not need to know anything about MCP, MiniMax keys, or tools —
it just sends messages and receives streamed text.

## build.gradle.kts (project level)

plugins:
  com.android.application version 8.2.0 (apply false)
  org.jetbrains.kotlin.android version 1.9.0 (apply false)

## build.gradle.kts (app level)

plugins:
  com.android.application
  kotlin("android")

android:
  compileSdk 34
  defaultConfig:
    applicationId "com.aurelius.app"
    minSdk 26
    targetSdk 34
    versionCode 1
    versionName "1.0"

  buildFeatures:
    buildConfig true
    viewBinding true

  kotlinOptions:
    jvmTarget = "1.8"

dependencies:
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

## local.properties (append)

BACKEND_URL=http://10.0.2.2:3000

## build.gradle.kts — expose as BuildConfig

In defaultConfig:
  buildConfigField("String", "BACKEND_URL",
    "\"${project.findProperty("BACKEND_URL") ?: "http://10.0.2.2:3000"}\"")

## models/Message.kt

data class Message(
  val id: String = java.util.UUID.randomUUID().toString(),
  val role: Role,
  val content: String,
  val isStreaming: Boolean = false
)

enum class Role { USER, ASSISTANT }

## models/ApiModels.kt

data class ApiMessage(val role: String, val content: String)
data class ChatRequest(val messages: List<ApiMessage>, val stream: Boolean = true)
data class SseToken(val token: String? = null, val error: String? = null)

## ChatRepository.kt

Handles SSE streaming from the backend.

class ChatRepository {
  private val client = OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)   // video/image gen can take a while
    .build()

  fun streamChat(
    messages: List<ApiMessage>,
    onToken: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
  ) {
    val json = Gson().toJson(ChatRequest(messages))
    val request = Request.Builder()
      .url("${BuildConfig.BACKEND_URL}/chat")
      .post(json.toRequestBody("application/json".toMediaType()))
      .build()

    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        onError(e.message ?: "Network error")
      }

      override fun onResponse(call: Call, response: Response) {
        response.body?.source()?.use { source ->
          while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ")
            if (data == "[DONE]") { onComplete(); return }
            try {
              val parsed = Gson().fromJson(data, SseToken::class.java)
              parsed.error?.let { onError(it); return }
              parsed.token?.let { onToken(it) }
            } catch (_: Exception) {}
          }
        }
        onComplete()
      }
    })
  }
}

## ChatViewModel.kt

class ChatViewModel : ViewModel() {
  private val repository = ChatRepository()
  private val _messages = MutableLiveData<List<Message>>(emptyList())
  val messages: LiveData<List<Message>> = _messages
  private val _isLoading = MutableLiveData(false)
  val isLoading: LiveData<Boolean> = _isLoading

  fun sendMessage(userText: String) {
    if (userText.isBlank() || _isLoading.value == true) return
    val userMsg = Message(role = Role.USER, content = userText)
    val streamingMsg = Message(role = Role.ASSISTANT, content = "", isStreaming = true)
    val currentList = _messages.value.orEmpty() + userMsg + streamingMsg
    _messages.value = currentList
    _isLoading.value = true

    val apiMessages = currentList
      .filter { !it.isStreaming }
      .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }

    repository.streamChat(
      messages = apiMessages,
      onToken = { token ->
        viewModelScope.launch(Dispatchers.Main) {
          val list = _messages.value.orEmpty().toMutableList()
          val idx = list.indexOfLast { it.isStreaming }
          if (idx >= 0) {
            list[idx] = list[idx].copy(content = list[idx].content + token)
            _messages.value = list
          }
        }
      },
      onComplete = {
        viewModelScope.launch(Dispatchers.Main) {
          val list = _messages.value.orEmpty().toMutableList()
          val idx = list.indexOfLast { it.isStreaming }
          if (idx >= 0) list[idx] = list[idx].copy(isStreaming = false)
          _messages.value = list
          _isLoading.value = false
        }
      },
      onError = { err ->
        viewModelScope.launch(Dispatchers.Main) {
          val list = _messages.value.orEmpty().toMutableList()
          val idx = list.indexOfLast { it.isStreaming }
          if (idx >= 0) list[idx] = list[idx].copy(content = "Error: $err", isStreaming = false)
          _messages.value = list
          _isLoading.value = false
        }
      }
    )
  }
}

## res/values/themes.xml

Material3 dark theme.
  Theme.Aurelius extends Theme.Material3.Dark.NoActionBar
  colorPrimary: #4ADE80
  colorSurface: #141414
  colorBackground: #0A0A0A
  colorOnSurface: #E8E8E8
  colorOutline: #2A2A2A

## res/layout/activity_main.xml

ConstraintLayout, background #0A0A0A.

Components top to bottom:
1. Toolbar (32dp tall, background #141414, title "Aurelius" in #AAAAAA, no elevation)
   — 1dp divider below in #2A2A2A
2. RecyclerView (id: rvMessages) fills remaining space, background #0A0A0A
3. Bottom input row (background #141414, top 1dp border #2A2A2A, 56dp tall):
   - EditText (id: etInput): hint "Message…", hint color #555555, text color #E8E8E8,
     background transparent, no underline, single line false, maxLines 4
   - ImageButton (id: btnSend): send icon, tint #4ADE80, 48×48dp

## res/layout/item_message.xml

USER bubble:
  MaterialCardView, cardBackgroundColor #1C1C1C, strokeColor #2A2A2A, strokeWidth 1dp,
  cornerRadius 0dp, layout_gravity end, marginEnd 12dp, marginBottom 8dp
  Contains: TextView (text color #E8E8E8, 14sp, padding 10dp 8dp)

ASSISTANT bubble:
  Plain layout, layout_gravity start, marginStart 12dp, marginBottom 8dp
  Contains: TextView (text color #E8E8E8, 14sp)
  When isStreaming=true: append blinking cursor "▌" via Handler toggling every 500ms.

## MessageAdapter.kt

RecyclerView.Adapter, two ViewTypes USER/ASSISTANT. DiffUtil for updates.
Blink cursor animation on streaming ASSISTANT items.

## MainActivity.kt

- ViewBinding
- RecyclerView with LinearLayoutManager, scroll to bottom on new messages
- Observe messages → update adapter, auto-scroll
- Observe isLoading → toggle send button
- Send button: get text, call viewModel.sendMessage(), clear input
- INTERNET permission in AndroidManifest.xml

## AndroidManifest.xml

  <uses-permission android:name="android.permission.INTERNET" />

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FINAL CHECKLIST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Backend:
- [ ] bun install completes
- [ ] bun run dev starts — MCP connections logged
- [ ] Tool list printed on startup (shows web_search, generate_image, etc.)
- [ ] GET /health → { status: "ok" }
- [ ] Plain chat message streams and completes
- [ ] "Search the web for X" triggers web_search tool call in console
- [ ] "Generate an image of X" triggers generate_image and returns a URL
- [ ] .env.example committed, .env in .gitignore

Android:
- [ ] Gradle sync passes in Android Studio
- [ ] assembleDebug builds without errors
- [ ] App launches on emulator — dark chat UI visible
- [ ] Message sends and assistant bubble streams tokens
- [ ] Blinking cursor during stream, disappears on completion
- [ ] Conversation history maintained across turns
- [ ] Send button disabled during active stream
- [ ] readTimeout 120s handles slow image/video generation without disconnecting

Push to GitHub:
  git init && git add . && git commit -m "Initial build: Aurelius v1.0"
  gh repo create caca294/aurelius --private --source=. --remote=origin --push
```
