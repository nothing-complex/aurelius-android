# Bug Hunt Report — Aurelius v2

## Consolidated Report (All Agents)

---

## error-triage-agent: 4 issues found

- [MEDIUM] ChatRepository.kt:187 - executeToolCall uses mutable var result with async callbacks - potential race condition if multiple tools called simultaneously
- [MEDIUM] ToolExecutor.kt:pollForTaskCompletion - Long-running polling loop (30 attempts x 10s = 5 min) has no cancellation mechanism on ViewModel scope
- [LOW] ChatRepository.kt:108 - Continuation error is caught and logged but not propagated to caller, user may not know message failed
- [LOW] ToolExecutor.kt:executeRequest - HTTP error response body logged as "Unknown error" when response.code is unavailable

---

## memory-leak-detective: 2 issues found

- [MEDIUM] HomeViewModel.kt:48 - onSearchQueryChange launches new coroutine without tracking/cancellation when query changes multiple times rapidly
- [LOW] HomeViewModel.kt:37 - stateIn uses WhileSubscribed(5000) but loadChats() is called in init{} without cancellation path

---

## code-reviewer: 5 issues found

- [MEDIUM] ToolExecutor.kt:59 - Magic number 30 for maxAttempts, should be named constant
- [MEDIUM] ToolExecutor.kt:60 - Magic number 10000 for intervalMs (10 seconds), should be named constant
- [MEDIUM] ToolExecutor.kt:73 - Magic number 5 for default numResults, should be named constant
- [LOW] ChatRepository.kt:72-78 - Hardcoded base URL fallback to "https://api.minimax.chat/v1/text/chatcompletion_v2"
- [LOW] SecureStorage.kt:37-41 - API key preference keys are string constants but not extracted to companion object constants

---

## code-reviewer anti-pattern scan: 14 issues found

### CRITICAL

- [CRITICAL] ChatRepository.kt:31 — Class not implementing any interface; violates repository pattern
  - Evidence: `class ChatRepository(` — no `interface IChatRepository`
  - DAOs ARE interfaces (ChatDao, MessageDao) but repository itself is not

- [CRITICAL] ChatRepository.kt:134 — Magic number used for preview truncation
  - Evidence: `preview = content.take(60),` — hardcoded 60 with no named constant

### HIGH

- [HIGH] ToolExecutor.kt:29-31 — Magic numbers for network timeouts repeated in two places
  - `.connectTimeout(60, TimeUnit.SECONDS)`, `.readTimeout(120, TimeUnit.SECONDS)`, `.writeTimeout(60, TimeUnit.SECONDS)`
  - Also found repeated in ChatRepository.kt:44-46

- [HIGH] ToolExecutor.kt:283-284 — Polling magic numbers not named
  - Evidence: `maxAttempts: Int = 30,` and `intervalMs: Long = 10000`

- [HIGH] SettingsScreen.kt:235,253 — Hardcoded API endpoint strings
  - Evidence: `"api.minimaxi.chat"` and `"api.minimax.chat"`

### MEDIUM

- [MEDIUM] ChatUseCases.kt — Use cases depend on concrete `ChatRepository` class, not an interface
  - Evidence: `class GetChatsUseCase(private val repository: ChatRepository)`

- [MEDIUM] ToolExecutor.kt:65-67 — Hardcoded tool model names as strings
  - `TOOL_MUSIC_GENERATION -> "music-01"`, `TOOL_GENERATE_VIDEO -> "video-01"`, `TOOL_UNDERSTAND_IMAGE -> "MiniMax-VL-01"`

- [MEDIUM] ChatScreen.kt:433 — "Streaming..." label hardcoded as UI string

- [MEDIUM] SettingsScreen.kt:176 — Documentation string with key format hint hardcoded
  - Evidence: `text = "Required for web search and image understanding. Format: sk-cp-..."`

- [MEDIUM] ChatScreen.kt:213 — Placeholder text "Type a message..." hardcoded

### LOW

- [LOW] ToolExecutor.kt — OkHttp client configured in two places independently

- [LOW] HomeScreen.kt:104 — "Search conversations..." placeholder hardcoded

- [LOW] AppModule.kt — Koin module uses single `module { }` block; no modular grouping

---

## Total Issue Count

| Source | Count |
|--------|-------|
| error-triage-agent | 4 |
| memory-leak-detective | 2 |
| code-reviewer (prior) | 5 |
| code-reviewer (anti-pattern) | 14 |
| **Grand Total** | **25** |

---

## Priority Fix List (Blockers First)

1. [CRITICAL] ChatRepository.kt:31 — Define `IChatRepository` interface; have ChatRepository implement it
2. [CRITICAL] ChatRepository.kt:134 — Extract magic number 60 to named constant (PREVIEW_MAX_LENGTH)
3. [MEDIUM] ChatRepository.kt:187 — Use suspendCoroutine or proper async pattern instead of mutable var result
4. [MEDIUM] ToolExecutor.kt:pollForTaskCompletion — Add cancellation check to prevent 5-minute hang
5. [MEDIUM] HomeViewModel.kt:48 — Cancel previous search coroutine before starting new one
6. [MEDIUM] ChatUseCases.kt — Make use cases depend on interface, not concrete class
7. [HIGH] ToolExecutor.kt + ChatRepository.kt — Extract repeated network timeout constants to shared config
8. [HIGH] SettingsScreen.kt:235,253 — Extract hardcoded API endpoints to constants
9. [LOW] Move UI strings to stringResource in Compose