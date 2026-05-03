# Aurelius v1 Architecture Audit Report

**Date:** 2026-04-26
**Auditor:** Architecture Auditor Agent
**Version:** v1.2 (versionCode 3)
**Working Directory:** `C:\Users\luka\Projects\Greyloop\Android Apps\Aurelius`

---

## Executive Summary

Aurelius v1 is a production-grade Android chat application with well-documented architecture. It follows a hybrid MVVM/UseCase pattern with Koin DI, Room persistence, and OkHttp-based MiniMax API integration. The most significant security issue is that API keys are stored in **plain SharedPreferences** (no encryption), which is unacceptable for production. The codebase is well-structured with clear separation of concerns, but uses older View-based UI alongside newer Compose UI, indicating an in-progress migration.

---

## API Key Management

**CRITICAL ISSUE: API keys stored in plaintext SharedPreferences**

```kotlin
// ToolExecutor.kt & ChatRepository.kt
private val apiKey: String
    get() = context.getSharedPreferences("aurelius_prefs", Context.MODE_PRIVATE)
        .getString("minimax_api_key", "") ?: ""
```

All API keys are stored in `SharedPreferences` with `Context.MODE_PRIVATE` — no encryption whatsoever. This means:
- Any root-level malware or adb backup can extract API keys
- No EncryptedSharedPreferences or Jetpack DataStore Security Library is used
- Keys are accessed by both `ToolExecutor` and `ChatRepository` via the same SharedPreferences file (`aurelius_prefs`)

**Storage Location:** `SharedPreferences("aurelius_prefs", MODE_PRIVATE)`
**Keys Stored:**
- `minimax_api_key` — MiniMax API key (regular or Coding Plan)
- `region` — "global" or "china" (determines API base URL)
- `plan_type` — "standard" or "coding_plan_plus" (determines TTS model)
- `setup_complete` — Boolean flag

**Recommendation:** Migrate to `EncryptedSharedPreferences` or `Security Library DataStore` with `MASTER_KEY` from AndroidX Security.

---

## MCP Tool Implementation

**ToolExecutor** (`data/ToolExecutor.kt`) is the central hub for all MiniMax MCP tool calls. It uses raw OkHttp with Gson for JSON serialization.

### Supported Tools

| Tool | Endpoint | Key Required |
|------|----------|-------------|
| `generate_image` | POST `/v1/image_generation` | Regular API key |
| `text_to_audio` | POST `/v1/t2a_v2` | Regular API key |
| `web_search` | POST `/v1/coding_plan/search` | **Coding Plan key** (`sk-cp-...`) |
| `generate_video` | POST `/v1/video_generation` + poll + fetch | Regular API key |
| `music_generation` | POST `/v1/music_generation` | Regular API key |
| `understand_image` | POST `/v1/coding_plan/vlm` | **Coding Plan key** (`sk-cp-...`) |

### Key Implementation Details

- **Non-streaming only:** All API calls use `stream=false`. MiniMax's SSE format was difficult to parse on Android, so the app uses batch responses instead.
- **Plan-based model selection:** `text_to_audio` selects `speech-2.8-hd` for standard plan or `speech-2.8-turbo` for Coding Plan Plus.
- **HTTP→HTTPS fix:** ToolExecutor patches `http://` URLs returned by MiniMax to `https://` for Android compatibility with Coil.
- **Video polling:** `generate_video` uses a 3-step async pattern: submit task → poll every 10s (max 30 attempts / 5 min) → fetch file.
- **Override point for testing:** `createClient()` is `protected open` so tests can inject a mock OkHttpClient.
- **Tool definitions:** `getToolDefinitions()` returns JSON Schema-formatted tool descriptions sent to MiniMax in the `tools` array.

### Error Handling
- Each tool has dedicated error handling with Log calls
- `onError` callbacks propagate errors back to the caller (ChatRepository → ViewModel → UI)
- HTTP non-2xx responses are logged with full response bodies

---

## Data Persistence

### Room Database (Schema v5)

**File:** `aurelius.db` (SQLite via Room)

**Entities:**

**ChatEntity** — one row per conversation
- `id: String` (UUID, client-generated — not auto-increment)
- `title: String` (default "New chat")
- `preview: String` (first 60 chars of latest user message)
- `createdAt: Long` (Unix ms)
- `updatedAt: Long` (Unix ms, controls sort order)

**MessageEntity** — one row per message, CASCADE-deleted with parent chat
- `id: String` (UUID, client-generated)
- `chatId: String` (ForeignKey → ChatEntity, indexed)
- `role: String` ("user" or "assistant")
- `content: String`
- `imageUrl: String?` (from generate_image tool result)
- `audioUrl: String?` (from text_to_audio or music_generation)
- `videoUrl: String?` (from generate_video)
- `attachmentName: String?` (user-attached PDF/TXT filename)
- `attachmentType: String?` (PDF, TEXT, IMAGE)
- `timestamp: Long`

### Migrations
- v1→v2: added `reasoning_label` column (later reverted)
- v2→v3: removed `reasoning_label` column
- v3→v4: added imageUrl/audioUrl/videoUrl columns
- v4→v5: added attachmentName/attachmentType columns

### DAO Pattern
- `ChatDao`: `insert`, `update`, `delete`, `getRecentChats()`, `getAllChats()`, `getChatById()`
- `MessageDao`: `insert`, `getMessages(chatId)`, `deleteMessagesForChat(chatId)`

---

## Architecture Overview

### Layers

```
UI Layer (Activities + Compose Screens + ViewModels)
    ↓
UseCase Layer (SendMessageUseCase, LoadMessagesUseCase, ExecuteToolUseCase, etc.)
    ↓
Repository Layer (ChatRepository — API calls + tool orchestration)
    ↓
Data Layer (ToolExecutor — HTTP calls, Room DAOs — persistence)
```

### Dependency Injection: Koin 3.5.3

**Module:** `viewModelModule` in `di/ViewModelModule.kt`

| Type | Scope | Notes |
|------|-------|-------|
| `AppDatabase` | Singleton | One instance via `getInstance()` |
| `ChatDao` | Singleton | From AppDatabase |
| `MessageDao` | Singleton | From AppDatabase |
| `ToolExecutor` | Factory | New instance per injection |
| `ChatRepository` | Factory | New instance per injection |
| UseCases | Factory | New instance per injection |
| `ChatViewModel` | ViewModel | Per-chat via `parametersOf(chatId)` |
| `HomeViewModel` | ViewModel | Single instance |

### Architecture Pattern
- **MVVM** with **UseCase/Repository pattern**
- ViewModels expose `LiveData` (not StateFlow) to Activities/Views
- Compose screens observe via `observeAsState()` or direct ViewModel injection
- No Hilt — uses Koin instead (lighter weight, simpler setup)

### Chat Flow (SendMessageUseCase)
1. User sends message → `SendMessageUseCase`
2. Persists user message to Room
3. Calls `ChatRepository.streamChat()` with full message history
4. `ChatRepository` sends to MiniMax API, handles tool calls via `ToolExecutor`
5. Tool results fed back to MiniMax in continuation request(s)
6. Final response persisted to Room as assistant message

---

## UI Layer

### Dual UI Stack (Migration in Progress)

**Legacy (View-based):** `ChatActivity.kt` using XML layouts + ViewBinding + RecyclerView
- Being phased out but still active
- Handles document attachment (PDF via ML Kit OCR, image picker)
- Uses `MessageAdapter` with `submitList()` DiffUtil

**New (Compose-based):** `ChatScreen.kt` + `HomeScreen.kt` in `ui.chat` and `ui.home` packages
- `ChatScreen.kt` — Compose chat interface
- `HomeScreen.kt` — Compose home/chat list
- Navigation via `AppNavigation.kt`

**Settings:** `SettingsScreen.kt` — Jetpack Compose

### Theme
- Material 3 via `compose-bom:2024.02.00`
- Custom theme in `ui/theme/` (Color.kt, Theme.kt, Type.kt)
- Color palette: Primary (purple-blue), Background (dark), Surface, Hint

### Navigation
- `AppNavigation.kt` — NavHost with `HomeScreen` and `ChatScreen` routes
- `MainActivity` hosts the Compose navigation graph

---

## Key Files Index

| File | Purpose |
|------|---------|
| `AureliusApplication.kt` | Koin initialization |
| `di/ViewModelModule.kt` | Koin DI module definitions |
| `data/ToolExecutor.kt` | MCP tool execution (image, TTS, search, video, music, vision) |
| `data/ChatRepository.kt` | MiniMax Chat Completions API + tool loop orchestration |
| `data/DocumentExtractor.kt` | PDF/TXT text extraction via ML Kit |
| `db/AppDatabase.kt` | Room database singleton (v5) |
| `db/ChatEntity.kt` | Chat Room entity |
| `db/MessageEntity.kt` | Message Room entity |
| `db/ChatDao.kt` | Chat CRUD operations |
| `db/MessageDao.kt` | Message CRUD operations |
| `models/ApiModels.kt` | All API request/response models, tool schemas, content blocks |
| `models/Message.kt` | In-memory UI message model |
| `ui/ChatActivity.kt` | Legacy View-based chat screen |
| `ui/MainActivity.kt` | Compose navigation host |
| `ui/chat/ChatScreen.kt` | New Compose chat screen |
| `ui/chat/ChatViewModel.kt` | Chat screen ViewModel |
| `ui/home/HomeScreen.kt` | Compose home screen |
| `ui/home/HomeViewModel.kt` | Home screen ViewModel |
| `ui/settings/SettingsScreen.kt` | Compose settings (API key, region, plan type) |
| `ui/theme/Theme.kt` | Material 3 theme |
| `usecase/SendMessageUseCase.kt` | Core message sending orchestration |
| `usecase/ExecuteToolUseCase.kt` | Single tool execution bridge |
| `usecase/LoadMessagesUseCase.kt` | Message observation |
| `usecase/LoadChatsUseCase.kt` | Chat list observation |
| `usecase/CreateChatUseCase.kt` | New chat creation |
| `usecase/DeleteChatUseCase.kt` | Chat deletion (CASCADE) |
| `usecase/GenerateTitleUseCase.kt` | AI title generation |

---

## Build Configuration

**AGP Version:** Unknown (`.gradle` directory present but no `build.gradle` shown)
**Kotlin Version:** 1.5.1 (Compose compiler extension)
**compileSdk:** 35
**minSdk:** 26
**targetSdk:** 35
**Java Version:** 17
**versionCode:** 3
**versionName:** 1.2

### Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.02.00 | Material 3 UI |
| Navigation Compose | 2.7.5 | Screen navigation |
| Room | 2.6.1 | SQLite persistence |
| Koin | 3.5.3 | Dependency injection |
| OkHttp | 4.12.0 | HTTP client |
| Gson | 2.10.1 | JSON serialization |
| Coil Compose | 2.7.0 | Async image loading |
| ML Kit Text Recognition | 16.0.0 | PDF/TXT OCR |
| Kotlin Coroutines | 1.7.3 | Async operations |
| compose-markdown | 0.5.8 | Markdown rendering |

### Schema Export
Room schema exported to `$projectDir/schemas` for migration tracking.

---

## Issues & Recommendations

### BLOCKER: API Key Storage (Security)
- **Issue:** Plain `SharedPreferences` for API keys — any rooted device or backup extraction reveals keys.
- **Fix:** Migrate to `EncryptedSharedPreferences` using AndroidX Security Library `MASTER_KEY`.

### MAJOR: Dual UI Stack
- `ChatActivity` (XML+ViewBinding) and `ChatScreen` (Compose) both exist, causing maintenance burden.
- Legacy `ChatActivity` still handles attachment flow (PDF OCR, image upload via imgbb).
- **Fix:** Complete the Compose migration and deprecate the legacy Activity.

### MAJOR: Image Understanding Limitation
- `understand_image` tool requires HTTP URL but Android document picker returns `content://` URIs.
- Current workaround: imgbb upload to get public HTTPS URL (requires image upload first).
- **Fix:** Consider implementing proper image hosting or direct content:// handling.

### MINOR: Non-Streaming Only
- No SSE/streaming support; each response is a complete batch.
- User sees no incremental tokens during generation.
- **Fix:** Investigate MiniMax SSE parsing fixes to enable streaming.

### MINOR: `generate_title` Tool Missing
- Title generation is a separate use case with its own API call to `MiniMax-M2.7-highspeed` model.
- No corresponding tool definition exposed to the model for general use.

### MINOR: `plan_type` Logic Duplication
- `ToolExecutor` and `SettingsScreen` both hardcode plan type → model mapping strings.
- **Fix:** Centralize plan-to-model mapping in a single configuration source.

### MINOR: Media URLs via HTTP
- Some MiniMax responses return `http://` URLs which Android's Coil/media cannot load.
- **Workaround:** `url.replaceFirst("http://", "https://")` in ToolExecutor — functional but inelegant.

### MINOR: Max Tool Loop = 5 Rounds
- `executeToolCalls` limits chained tool calls to 5 rounds to prevent infinite loops.
- No user-facing indication that the round limit was hit.

---

## Summary Verdict

| Category | Status |
|----------|--------|
| API Key Security | **BLOCKER** — Plain SharedPreferences |
| Architecture | Solid MVVM + UseCase + Repository |
| Data Persistence | Well-structured Room with migrations |
| MCP Tools | Complete implementation, 6 tools |
| UI Layer | Dual stack (migration in progress) |
| Build Config | Up-to-date (API 35, Compose BOM 2024.02.00) |
| Testability | Good (override `createClient()`, MockK, Turbine) |
| Documentation | Excellent inline KDoc comments |
