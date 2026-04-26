# Aurelius — Auto-Generated Documentation

> Auto-updated: 2026-04-26

## Bug Fixes Applied (2026-04-26)

### 1. Empty API Key Error Handling (ChatRepository.kt)
- **Issue**: When no API key configured, code silently proceeded with empty Bearer token
- **Fix**: Added validation that calls onError callback with user-friendly message before network call
- **Lines**: ChatRepository.kt lines ~284-294

### 2. Error JSON Detection (ChatRepository.kt)
- **Bug**: Error JSON detection used broken OR condition `|| (body.contains("\"message\"") && !body.contains("\"choices\""))` that incorrectly rejected valid responses containing `"message"` but no `"choices"`
- **Fix**: Simplified to only check for `"error"` field presence
- **Lines**: executeChatCompletion and executeAnthropicChatCompletion methods (ChatRepository.kt)

### 3. Snackbar Clear Timing (ChatScreen.kt)
- **Issue**: clearError() called immediately after showSnackbar(), which cancelled the snackbar display
- **Fix**: Now waits for SnackbarResult.Dismissed before clearing error
- **Lines**: ChatScreen.kt lines 112-118

### 4. Silent Return on Null Chat ID (ChatViewModel.kt)
- **Issue**: sendMessage() silently returned when currentChatId was null, no error shown
- **Fix**: Now sets error state and emits ChatEvent.Error instead of silent return
- **Lines**: ChatViewModel.kt line 108

### 5. Missing SnackbarResult Import (ChatScreen.kt)
- **Fix**: Added `import androidx.compose.material3.SnackbarResult`

### 6. Tool Definitions Stripped on Anthropic Path (ChatRepository.kt)
- **Issue**: When codingPlanKey (sk-cp- key) is set, `executeChatCompletion` routes to `executeAnthropicChatCompletion` which does not pass `toolDefinitions` to the request
- **Fix**: Tools are intentionally stripped on the Anthropic `/v1/messages` path since that endpoint handles tool-calling differently; `getAnthropicUrl()` added at line ~68 for region-based routing
- **Lines**: ChatRepository.kt line ~281 (routing check), ~335+ (executeAnthropicChatCompletion body), `getAnthropicUrl()` at ~68

---

## Current Status

- **AI chat**: WORKING with sk-cp- API key
- **Error handling**: Properly surfaces error messages via snackbar
- **Build verified**: app-debug.apk (19.7 MB) at `app/build/outputs/apk/debug/`
- **Model**: MiniMax-M2.7
- **API key format**: sk-cp- keys route to Anthropic `/v1/messages` endpoint via `executeAnthropicChatCompletion`
- **Endpoint**: `https://api.minimax.io/anthropic/v1/messages` (global) or `https://api.minimax.chat/anthropic/v1/messages` (China)

---

## Known Issues

- Navigation from Home to Chat may have issues on emulator (requires physical device testing)
- QA tests on emulator showed Home screen persisting after "Start Chatting" tap

---

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/.../repository/ChatRepository.kt` | API calls, error handling, sk-cp- key routing to Anthropic endpoint |
| `app/src/main/java/.../remote/ApiModels.kt` | Anthropic API models (AnthropicRequest/Response/Message) for sk-cp- keys |
| `app/src/main/java/.../ui/chat/ChatScreen.kt` | Snackbar display with proper dismiss handling |
| `app/src/main/java/.../ui/chat/ChatViewModel.kt` | Null chat ID error emission |
| `app/build/outputs/apk/debug/app-debug.apk` | Built APK (19.7 MB) |

---

## Recent Commits

- `47831b5` — Add Anthropic API support for sk-cp Coding Plan keys
- `905b2ee` — Fix API endpoint: /v1/chat/completions
- `679969d` — Fix API endpoint, add AureliusTypography
- `0ee6884` — Physical device screenshot — Aurelius v2 complete
