# Aurelius Android App - Session Brief

## Current State
Aurelius is an Android app (Kotlin/Jetpack Compose) that uses MiniMax's M2.7 model via API calls. The app has:
- Chat functionality with AI responses
- Tool execution (image gen, TTS, music, video, web search, image understanding)
- Parchment-themed UI with swipe navigation
- Secure API key storage via EncryptedSharedPreferences

## What's Working (Verified)
- ✅ M2.7 text chat via MiniMax API
- ✅ Web search tool (via sk-cp- coding plan key)
- ✅ Image understanding tool (via sk-cp- key)
- ✅ Text-to-speech tool
- ✅ Chat crash on second message FIXED (LazyColumn duplicate key + ViewModel race condition)
- ✅ Home→Settings swipe navigation (edge swipe from left)
- ✅ Show Thinking Tags feature (AI reasoning displayed as footnote above responses)

## Just Fixed (Not Yet Tested)
- 🐛 **Image generation** - was missing `response_format: "url"` in request body (caused API error 2013). Fix in `ToolExecutor.kt:189`. Build completed successfully.

## Active API Key (use from SecureStorage, never hardcode)
```
sk-cp-tm5PJ6VjmyFhxKpiN9Yvz8Y-_0lP5If9dce4xttWmfHUY8HXMdsztaDG2qhC1d1AcB3M5NZphmXze43HfVnAnpqqwa-hqRa7j8fse8fK7kWQPdS_qAP4tO4
```
Region: Global (api.minimax.io)

## Goals (Priority Order)

### Goal 1: Verify Image Generation End-to-End
**Prompt to give a team:**
"Send a message in Aurelius chat that triggers the generate_image tool. Ask: 'Generate an image of a cute cat'. Verify the tool executes without error and the image URL is returned. Then use mcp__MiniMax__understand_image to verify the image actually shows a cat. Screenshot the result."

### Goal 2: Verify Text-to-Speech End-to-End
**Prompt to give a team:**
"Send a message that triggers text_to_audio. Ask: 'Read this haiku: Basho style, old pond, frog'. Verify TTS executes and returns an audio URL. Play the audio to confirm it works."

### Goal 3: Verify Music Generation End-to-End
**Prompt to give a team:**
"Send a message that triggers music_generation. Ask: 'Create happy lo-fi music'. Verify it starts a task and eventually returns a music URL. Note: may require polling."

### Goal 4: Full 3-Message Chat (Proves No Crash)
**Prompt to give a team:**
"Start a new chat, send 3 messages. Each should get an AI reply. Screenshot proving no crash after 3rd message. The crash was: IllegalArgumentException 'Key UUID was already used' in LazyColumn."

## Teams to Spawn

### For each goal, spawn a teammate of type `ui-playback-agent` with:
- Emulator: `emulator-5554`
- Full context of what to test
- Instructions to use `mcp__MiniMax__understand_image` for image verification (NOT Read tool)
- Instructions to run `adb logcat` to catch any crashes
- Screenshot requirements

### Coordination approach:
- Spawn all 4 teams in parallel
- Each team reports back with screenshot evidence
- DO NOT do any work yourself - delegate everything

## Key Files (Read Before Touching Anything)

| File | Purpose |
|------|---------|
| `app/src/main/java/com/greyloop/aurelius/data/remote/ToolExecutor.kt` | All tool execution - image gen fix just applied |
| `app/src/main/java/com/greyloop/aurelius/data/repository/ChatRepository.kt` | Chat API calls, tool definitions |
| `app/src/main/java/com/greyloop/aurelius/ui/chat/ChatScreen.kt` | UI with swipe gestures, message bubbles |
| `app/src/main/java/com/greyloop/aurelius/ui/home/HomeScreen.kt` | Chat list, edge swipe to Settings |
| `app/src/main/java/com/greyloop/aurelius/data/remote/ApiModels.kt` | All API request/response models |
| `app/src/main/java/com/greyloop/aurelius/data/security/SecureStorage.kt` | API key storage |
| `SPEC.md` | Full feature spec |
| `CLAUDE.md` | Agent spawning rules |

## Screenshots Folder
ALL test screenshots are in: `screenshots/` (1008 files)
- DO NOT save screenshots to repo root or other locations
- DO NOT commit screenshots to git (add to .gitignore if not already)
- All verification screenshots go in `screenshots/` folder only

## Critical Rules
1. **Never hardcode the API key** - always read from SecureStorage
2. **For images: use `mcp__MiniMax__understand_image` NOT Read tool** - Read hallucinates on images
3. **Never do work yourself - always delegate to a team/agent**
4. **Read the code first** before delegating or making any changes
5. **If an agent spawns, give it specific instructions with expected output**
6. **Check `~/.claude/agents/AGENT_INDEX.md` for pre-built agents before creating new ones**

## Token Plan Features (from token-plan page)
| Feature | Plus Plan | Status |
|---------|-----------|--------|
| M2.7 text | 4,500 req/5hrs | ✅ Working |
| Speech 2.8 | 4,000 chars/day | ✅ Implemented |
| image-01 | 50 images/day | 🐛 Just fixed |
| music-01 | 100 songs/day | ✅ Implemented |
| Video (Max only) | 2/day | ⚠️ Plus won't work |
| Web search MCP | Unlimited | ✅ Working |
| Image understanding | Unlimited | ✅ Working |

## Known Issues
1. HorizontalPager consumes swipe events internally - edge swipe from left edge works but requires precise touch position
2. Video generation requires Max plan ($50/mo) - Plus plan users will get a "limit reached" error
3. Music generation may take up to 30 seconds due to polling

## Session Start
Start by running `ctx stats` and `ctx doctor` to verify context-mode is working. Then immediately spawn all 4 test teams in parallel without doing any work yourself.