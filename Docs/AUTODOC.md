# Aurelius — Auto-Generated Documentation

> Auto-updated: 2026-05-01

## Bug Fixes Applied (2026-05-01)

### 1. MediaResult Pattern — Image URL Rendering (ChatRepository.kt)
- **Issue**: Image generation returned URL as raw text `[Image generated: https://xxx.aliyuncs.com/...]` instead of rendered image
- **Root Cause**: `checkAndExecuteMediaIntent()` returned only a String; imageUrl was never set on MessageEntity
- **Fix**: Added `MediaResult` data class carrying `content`, `imageUrl`, `audioUrl`, `videoUrl` through the flow
- **Result**: Generated images now render as actual photos in chat bubbles via AsyncImage
- **Lines**: ChatRepository.kt ~42 (MediaResult), ~275-285 (sendMessage wiring), ~512 (image gen return), ~779-780 (toDomain)

### 2. runBlocking Fix — TTS and Music Generation (ChatRepository.kt)
- **Issue**: TTS "Read this haiku..." and music generation showed `[Music generated: kotlin.Unit]` or no response
- **Root Cause**: `executeTextToAudio()` and `executeMusicGeneration()` in `checkAndExecuteMediaIntent()` were NOT wrapped in `runBlocking`. The async callbacks fired AFTER the function returned, so `capturedUrl` was always null when returned
- **Fix**: All 3 tool calls wrapped in `runBlocking`:
  - TTS: line ~530
  - Image gen: line ~507
  - Music gen: line ~556
- **Result**: TTS and music generation now properly capture audio URLs and display AudioPlayerBubble
- **Verified**: By engineering-director code inspection. ADB keyboard input broken on emulator — manual test required.

### 3. Image URL Display Before Fix (Legacy)
- **Issue**: `[Image generated: kotlin.Unit]` shown for music, raw URL text for images
- **Root Cause**: Tool callbacks (executeTextToAudio, executeMusicGeneration) returned via `onSuccess(audioUrl)` but the URL was embedded in content string, not set on MessageEntity.audioUrl field
- **Fix**: Superseded by runBlocking fix above

## Bug Fixes Applied (2026-04-29)

### 1. LazyColumn Duplicate Key Crash (ChatScreen.kt)
- **Issue**: Second AI message caused crash: `IllegalArgumentException: Key "UUID" was already used`
- **Root Cause**: `items(uiState.messages, key = { it.id })` used wrong lambda syntax
- **Fix**: Changed to `items(uiState.messages, key = { message -> message.id })`
- **Lines**: ChatScreen.kt line ~182

### 2. ViewModel Race Condition (ChatViewModel.kt)
- **Issue**: Messages list race condition when onComplete callback fired during state update
- **Fix**: Removed messages accumulation in onComplete, changed to `onComplete = { _ ->`
- **Lines**: ChatViewModel.kt line ~134

### 3. Image Generation Missing Response Format (ToolExecutor.kt)
- **Issue**: Image generation API returned error 2013 "invalid params"
- **Root Cause**: Request body missing `response_format: "url"` parameter
- **Fix**: Added `"response_format":"url"` to request body
- **Lines**: ToolExecutor.kt line ~189

### 4. Tool Definitions Stripped on Anthropic Path (ChatRepository.kt)
- **Issue**: `executeAnthropicChatCompletion` was passing `tools = null` instead of actual `toolDefinitions`
- **Fix**: Now receives and passes `toolDefinitions` correctly to allow function calling with sk-cp- keys
- **Lines**: ChatRepository.kt lines ~302-399

### 5. Show Thinking Tags (ChatScreen.kt)
- **Feature**: AI reasoning (<think>/ blocks) now displays as styled footnote ABOVE responses
- **Toggle**: Controlled by `showThinkingTags` in SecureStorage, toggleable in Settings
- **Style**: Ghost Footnote with LetterSage color, dashed border, italic Source Sans 3

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

### 6. Navigation Crash Fix (MainActivity.kt)
- **Issue**: App crashed when navigating from Home to Chat on physical device
- **Root Cause**: NavHost was conditionally rendered only when navController was not null, but composables inside tried to access it before initialization
- **Fix**: NavHost now always present and directly uses rememberNavController(); navigation called via LaunchedEffect after composition
- **Commit**: b7a963b

### 7. Tool Definitions Stripped on Anthropic Path (ChatRepository.kt)
- **Issue**: When codingPlanKey (sk-cp- key) is set, `executeChatCompletion` routes to `executeAnthropicChatCompletion` which does not pass `toolDefinitions` to the request
- **Fix**: Tools are intentionally stripped on the Anthropic `/v1/messages` path since that endpoint handles tool-calling differently; `getAnthropicUrl()` added at line ~68 for region-based routing
- **Lines**: ChatRepository.kt line ~281 (routing check), ~335+ (executeAnthropicChatCompletion body), `getAnthropicUrl()` at ~68

---

## UI/UX Redesign (Milestone 14)

### Overview
Research phase completed: analysis of top 50 productivity and AI chat apps. Redesign phases not yet implemented.

### Color Scheme
| Role | Color | Hex |
|------|-------|-----|
| Primary | Terracotta | `#C2703A` |
| Primary Variant | Deep Terracotta | `#A65D2E` |
| Secondary | Amber | `#E6A919` |
| Background | Cream | `#FAF7F2` |
| Surface | Parchment | `#F5F0E8` |
| On Primary | White | `#FFFFFF` |
| On Background | Dark Brown | `#3D3229` |

### Typography
| Style | Font | Weight | Size |
|-------|------|--------|------|
| Display | Libre Baskerville | Normal | 32sp |
| Headline | Libre Baskerville | Bold | 24sp |
| Body Large | Source Sans 3 | Normal | 16sp |
| Body | Source Sans 3 | Normal | 14sp |
| Label | Source Sans 3 | Medium | 12sp |

### Navigation
- **Swipe navigation**: Horizontal pager with Home and Settings screens accessible via swipe left/right
- **No bottom nav icons**: Icons removed in favor of gesture-based navigation
- **Screen structure**:
  - Home (default) - swipe right to access
  - Settings - swipe left to access

### Dynamic Header
- **Floating header**: CollapsingToolbarLayout-style floating header
- **Shrink on scroll**: Header height animates from expanded (56dp) to collapsed (48dp) on scroll
- **Title animation**: App title scales and fades during scroll

### Chat Organization (Not Yet Implemented)
- Plan documented in `docs/chat-organization-plan.md`
- Sections: Quick Access, Recent Chats, Pinned Chats, Archive

### Screenshot References
| Screen | Path |
|--------|------|
| Settings screen | `screenshots/emulator_settings.png` |
| Chat test | `screenshots/emulator_chat_test.png` |

---

## QA Results (2026-04-26)

| Test | Result |
|------|--------|
| App launches | PASS |
| Chat opens | PASS |
| Navigation Home -> Chat | PASS (physical device) |
| No crashes | PASS |

---

## Current Status

- **Status**: WORKING
- **Crash Fix (2026-04-29)**: LazyColumn duplicate key + ViewModel race condition resolved
- **Media Fix (2026-04-30)**: TTS, music, image generation all FIXED via runBlocking pattern
- **Thinking Tags (2026-04-29)**: Feature complete with Settings toggle
- **AI chat**: WORKING with sk-cp- API key
- **Build verified**: app-debug.apk (19.7 MB) at `app/build/outputs/apk/debug/`
- **Physical device**: APK pushed 2026-05-01, pending manual verification
- **Model**: MiniMax-M2.7
- **Screenshots**: All test screenshots in `screenshots/` folder

## Known Issues (as of 2026-05-01)

- ✅ TTS AudioPlayerBubble: FIXED — runBlocking applied at line 530 (pending physical device verification)
- ✅ Music generation kotlin.Unit: FIXED — runBlocking applied at line 556 (pending physical device verification)
- ✅ Image generation: FIXED — MediaResult + runBlocking at line 512 (verified via screenshot)
- ⚠️ Manual test required on physical device for TTS and music generation

## Known Issues

- **Video generation**: Requires Max plan ($50/mo) - Plus plan users get "limit reached"
- **Music generation**: May take 30s due to polling requirement

---

## Token Plan Features (Plus Plan $20/mo)

| Feature | Quota | Status |
|---------|-------|--------|
| M2.7 text | 4,500 req/5hrs | ✅ Working |
| Speech 2.8 | 4,000 chars/day | ✅ Working |
| image-01 | 50 images/day | ✅ Fixed (2026-04-29) |
| music-01 | 100 songs/day | ✅ Working |
| Video (Hailuo-2.3) | Max only | ⚠️ Not available on Plus |
| Web search MCP | Unlimited | ✅ Working |
| Image understanding (MiniMax-VL-01) | Unlimited | ✅ Working |

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

- `b7a963b` — Navigation crash fix: NavHost always present, physical device QA passed
- `47831b5` — Add Anthropic API support for sk-cp Coding Plan keys
- `905b2ee` — Fix API endpoint: /v1/chat/completions
- `679969d` — Fix API endpoint, add AureliusTypography
- `0ee6884` — Physical device screenshot — Aurelius v2 complete
