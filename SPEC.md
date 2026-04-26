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
