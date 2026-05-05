# Aurelius v2 - Specification Document

**Date:** 2026-04-26
**Version:** 2.0
**Build Target:** Android API 35 (minSdk 26)

---

## 1. Project Overview

**Project Name:** Aurelius
**Type:** AI Chat Android Application
**Core Functionality:** Multi-tool AI chat app with MiniMax MCP integration for text, voice, image, music, and video generation. Features conversation branching, summarization, and persona system.

---

## 2. Technology Stack & Choices

### Framework & Language
- **Language:** Kotlin 1.9.x
- **UI Framework:** Jetpack Compose (BOM 2024.02.00)
- **Min SDK:** 26 (Android 8.0)
- **Target/Compile SDK:** 35

### Key Libraries/Dependencies
| Category | Library | Version |
|----------|---------|---------|
| UI | Jetpack Compose BOM | 2024.02.00 |
| UI | Material 3 | (via BOM) |
| Navigation | Navigation Compose | 2.7.5 |
| DI | Koin | 3.5.3 |
| Database | Room | 2.6.1 |
| Networking | OkHttp | 4.12.0 |
| Serialization | Kotlinx Serialization | 1.6.2 |
| Image Loading | Coil Compose | 2.7.0 |
| Security | AndroidX Security Crypto | 1.1.0-alpha06 |
| Async | Kotlin Coroutines | 1.7.3 |
| Markdown | compose-markdown | 0.5.8 |

### State Management
- **Pattern:** MVVM with StateFlow
- **UI State:** Compose State + ViewModel StateFlow
- **Side Effects:** Kotlin Channels for one-time events

### Architecture Pattern
- **Clean Architecture** with 3 layers:
  - **UI Layer:** Compose screens, ViewModels
  - **Domain Layer:** Use cases, domain models
  - **Data Layer:** Repositories, Room DAOs, ToolExecutor, security

---

## 3. Feature List

### Core Features
1. **AI Chat** - Send/receive text messages with streaming response display
2. **Conversation Management** - Create, rename, delete, list conversations
3. **Conversation Branching** - Visual tree navigation for branching conversations
4. **AI Summaries** - Generate conversation summaries (key differentiator)
5. **Persona System** - Character personas with avatars
6. **Show Thinking Tags** - Toggle to display AI reasoning as styled footnote above response bubble (Settings → AI Responses)

### MiniMax MCP Tools
1. **text_to_audio** - Voice AI responses with emotion control
2. **understand_image** - Analyze images user sends
3. **web_search** - Live information queries
4. **text_to_image** - Generate images from prompts
5. **music_generation** - Create music from prompts
6. **generate_video** - Create short videos

### Media Features
7. **Voice Input** - Voice-to-text input
8. **Image Upload** - Attach images for AI analysis
9. **Image Generation Preview** - View generated images in chat
10. **Music Playback** - Play generated music in-app
11. **Web Search Results** - Display search results in chat

### UI/UX Features
12. **Streaming Text Animation** - Character-by-character display
13. **Typing Indicator** - Animated typing indicator with personality
14. **Dark/Light Mode** - Material 3 dynamic theming
15. **Message Reactions** - Like, love, etc. on messages
16. **Conversation Search** - Search through chat history
17. **Quick Action Buttons** - Image, voice, music shortcuts

---

## 4. UI/UX Design Direction

### Overall Visual Style
- **Design System:** Material Design 3
- **Theme:** Modern, clean, AI-focused
- **Motion:** Subtle animations for messages, typing indicator, transitions

### Color Scheme
- **Primary:** Deep Purple (#6750A4) / Purple-Blue gradient
- **Background Dark:** Near-black (#1C1B1F)
- **Background Light:** Off-white (#FFFBFE)
- **Accent:** Tertiary colors for AI responses
- **Chat Bubbles:** Distinct colors for user vs. AI

### Layout Approach
- **Navigation:** Bottom navigation bar (Home, Explore, Settings)
- **Home Screen:** Conversation list with branching visualization
- **Chat Screen:** Full-screen chat with bottom input bar
- **Input Bar:** Text field + send button + quick action buttons
- **Message Bubbles:** Rounded corners, proper spacing, avatar for AI

### Typography
- **Headlines:** Medium weight, larger sizes
- **Body:** Regular weight, readable sizes
- **Code/Technical:** Monospace for tool outputs

---

## 5. Security Requirements

### API Key Storage
- **CRITICAL:** Use `EncryptedSharedPreferences` with AndroidX Security Library
- **MASTER_KEY:** Required for encryption
- **Keys never hardcoded** - always read from secure storage at runtime

### API Key Separation
- **MiniMax API Key:** For standard tools (image, audio, video, music)
- **Coding Plan Key (sk-cp-...):** For web_search and understand_image tools
- Both stored encrypted, selected based on tool requirements

---

## 6. Data Schema (Room v5)

### ChatEntity
- id: String (UUID)
- title: String
- preview: String
- createdAt: Long
- updatedAt: Long
- parentBranchId: String? (for branching)

### MessageEntity
- id: String (UUID)
- chatId: String (FK)
- role: String ("user" / "assistant")
- content: String
- imageUrl: String?
- audioUrl: String?
- videoUrl: String?
- attachmentName: String?
- attachmentType: String?
- timestamp: Long
- parentMessageId: String? (for branching)

---

## 7. Build Configuration

```
compileSdk = 35
minSdk = 26
targetSdk = 35
versionCode = 2
versionName = "2.0.0"
```

### Proguard Rules
- Keep Kotlin metadata
- Keep Room entities
- Keep OkHttp
- Keep Kotlinx Serialization

---

## 8. Sprint v5 QA Report (2026-05-04)

**RECOMMENDATION: NO-GO — 5 blockers require engineering fix before release**

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Test pass rate | 20/25 (80%) | >90% | FAIL |
| Crash-free rate | 100% | >99% | PASS |
| State preservation | 1/2 FAIL | 0 failures | FAIL |
| Network error handling | FAIL | Graceful degradation | FAIL |
| Category B coverage | 0% (DEFERRED) | 100% | BLOCKED |

### Release Blockers

| Priority | Issue | Description |
|----------|-------|-------------|
| CRITICAL | E-04 | ViewModel state NOT preserved across rotation — user input lost |
| HIGH | C-02 | No input validation feedback for invalid API key |
| HIGH | E-01 | No network error handling in airplane mode |
| MEDIUM | A-02 | Search icon tap has no snackbar feedback |
| MEDIUM | A-10 | FAB/delete icon overlap in landscape mode |

### FAIL Details

1. **A-02 (Search snackbar missing):** Search icon tap shows no UI feedback. Fix: Add Snackbar.make() on search tap.
2. **A-10 (Landscape FAB overlap):** FAB overlaps delete icons in landscape. Fix: Adjust ConstrainedLayout for rotation.
3. **C-02 (No API key validation):** TextField without onValueChange validation. Fix: Add validation feedback on invalid key.
4. **E-01 (No network error handling):** Airplane mode shows no error banner/toast. Fix: Add connectivity check before loading.
5. **E-04 (State lost on rotation):** Input text "RotationTest123" lost after rotation. Fix: SaveStateHandle for input text.

### Deferred Testing

**Category B (MCP Tools):** All 5 MiniMax media tools unavailable (text_to_image, text_to_audio, music_generation, generate_video, voice_clone). Testing deferred until MCP configuration is fixed.
