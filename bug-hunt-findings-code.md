# Bug Hunt Report — Aurelius v2 (Code Review)

## Anti-Pattern Scan Results

---

### CRITICAL

- [CRITICAL] `ChatRepository.kt:31` — Class not implementing any interface; violates repository pattern
  - Evidence: `class ChatRepository(` — no `interface IChatRepository` or similar
  - The DAOs ARE interfaces (ChatDao, MessageDao) but the repository itself is not

- [CRITICAL] `ChatRepository.kt:134` — Magic number used for preview truncation
  - Evidence: `preview = content.take(60),` — hardcoded 60 with no named constant

---

### HIGH

- [HIGH] `ToolExecutor.kt:29-31` — Magic numbers for network timeouts repeated in two places
  - Evidence:
    ```
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    ```
  - Also found repeated in `ChatRepository.kt:44-46`

- [HIGH] `ToolExecutor.kt:283-284` — Polling magic numbers not named
  - Evidence: `maxAttempts: Int = 30,` and `intervalMs: Long = 10000`

- [HIGH] `SettingsScreen.kt:235,253` — Hardcoded API endpoint strings
  - Evidence: `"api.minimaxi.chat"` and `"api.minimax.chat"`

---

### MEDIUM

- [MEDIUM] `ChatUseCases.kt` — Use cases depend on concrete `ChatRepository` class, not an interface
  - Evidence: `class GetChatsUseCase(private val repository: ChatRepository)`
  - This couples use cases to implementation details; should depend on interface

- [MEDIUM] `ToolExecutor.kt:65-67` — Hardcoded tool model names as strings
  - Evidence:
    ```
    TOOL_MUSIC_GENERATION -> "music-01"
    TOOL_GENERATE_VIDEO -> "video-01"
    TOOL_UNDERSTAND_IMAGE -> "MiniMax-VL-01"
    ```

- [MEDIUM] `ChatScreen.kt:433` — "Streaming..." label hardcoded as UI string
  - Evidence: `text = "Streaming...",`

- [MEDIUM] `SettingsScreen.kt:176` — Documentation string with key format hint hardcoded
  - Evidence: `text = "Required for web search and image understanding. Format: sk-cp-..."`

- [MEDIUM] `ChatScreen.kt:213` — Placeholder text "Type a message..." hardcoded
  - Evidence: `placeholder = { Text("Type a message...") }`

---

### LOW

- [LOW] `ToolExecutor.kt` — OkHttp client configured in two places (ChatRepository + ToolExecutor)
  - Both configure their own timeouts independently
  - Consider extracting to a shared OkHttpClient configuration

- [LOW] `HomeScreen.kt:104` — "Search conversations..." placeholder hardcoded
  - Evidence: `placeholder = { Text("Search conversations...") }`

- [LOW] `AppModule.kt` — Koin module uses only single `module { }` block; no modular grouping
  - All dependencies in one file; works but could be organized by feature

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 2 |
| HIGH | 4 |
| MEDIUM | 5 |
| LOW | 3 |
| **Total** | **14** |

## Top Recommendations

1. Extract network constants (timeouts, model names, endpoints) to a `NetworkConfig` or `ApiConstants` object
2. Define `IChatRepository` interface; have `ChatRepository` implement it; use cases depend on interface
3. Extract magic numbers into named companion object constants
4. Move UI strings to `stringResource` in Compose (externalized)
5. Consolidate OkHttpClient configuration into single shared instance in DI module