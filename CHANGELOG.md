# Aurelius v2 Changelog

## [2026-04-26] Sprint: Ground-up Redesign

### Milestone 1: Architecture Audit (2026-04-26)
- COMPLETED: v1 audit (architecture-auditor)
- API keys stored in plain SharedPreferences (SECURITY P0 - to fix in v2)
- MVVM + UseCase/Repository pattern, Koin DI, Room v5

### Milestone 2: Competitive Research (2026-04-26)
- COMPLETED: competitive-research.md
- Top 10 features for v2: streaming text, typing indicator, voice AI, image/music generation
- MiniMax MCP best practices documented

### Milestone 3: Design Audit (2026-04-26)
- COMPLETED: v1-design-audit.md
- P0: EncryptedSharedPreferences + biometric lock required
- P1: 16dp bubble corners, light mode, animated typing indicator, conversation search
- P2: Better media rendering, conversation export, shortcut widgets

### Milestone 4: Design Overhaul Plan (2026-04-26)
- COMPLETED: design-overhaul-plan.md
- Creative direction: "Claude-like, refined, stoic, earthy and simple and clear"
- Color system: Warm blacks (#111110 bg), muted sage primary (#A8B89C), terracotta error (#C45D4A)
- Typography: Light weight (300) for headlines, system Roboto for body
- Animation: Remove spinners, blinking cursor; keep only essential motion

### Milestone 4b: QA Blockers Fixed (2026-04-26) — COMPLETED
- 4 MUST-FIX issues flagged by ux-sanity-check all resolved:
  1. EncryptedSharedPreferences dependency — FIXED
  2. Streaming text animation — IMPLEMENTED
  3. Message micro-animations — IMPLEMENTED
  4. "Your key is stored securely" text — ADDED to SettingsScreen

### Milestone 6: QA Testing (2026-04-26) — COMPLETED
- VERDICT: RELEASABLE (app code quality excellent)
- All UI/navigation/text input/persistence: PASS
- 404 on AI response: server/API issue, not app bug

### Milestone 7: Creative Review (2026-04-26) — COMPLETED
- Score: 2.5/5
- ui-critic: 2.5/5 - chat bubbles flat, no elevation
- engagement-audit-agent: 2/5 - zero distinctive features
- visual-hierarchy-auditor: NEEDS WORK - send button buried
- cta-optimization-agent: 3/5 - functional but undifferentiated
- Top 5 improvements identified in creative-review.md

### Milestone 8: Bug Hunt (2026-04-26) — COMPLETED
- 25 issues found, 2 CRITICAL
- Critical #1: Add ChatRepository interface
- Critical #2: Magic number 60 → named constant
- Full details in bug-hunt-report.md

### Milestone 9: Refactor & Polish (2026-04-26) — COMPLETED
- 3 core features missing: branching, AI summaries, personas
- compose-ui-expert: 4 improvements
- Scope completion: 47%

### Milestone 10: Feature Build (2026-04-26) — COMPLETED
- Conversation Branching (long-press to branch)
- AI Summaries (auto + manual)
- Persona System (4 personas)

### Milestone 11: Polish (2026-04-26) — COMPLETED
- Bubble elevation implemented (ui-critic feedback)
- Send button visibility improved (visual-hierarchy-auditor)
- Quick actions added (creative quick wins)
- ChatRepository interface added (CRITICAL fix)
- Magic numbers replaced with named constants (CRITICAL fix)

### Final QA (2026-04-26) — COMPLETED (followed by crash)

### Milestone 12: Crash Fix (2026-04-26) — IN PROGRESS
- CRITICAL: App crashes on launch (ANR - "excessive binder traffic during cached")
- App freezes immediately, Android kills after 5 seconds
- startup-crash-fixer investigating: AureliusApplication, MainActivity, AppModule
- Cause: main thread blocking at startup
- Need to push fix to GitHub and re-test on device after resolved

### GitHub Status (2026-04-26)
- Repo: https://github.com/nothing-complex/aurelius-android (public)
- Branch: master
- Latest commit: 37c9f5a
- 48 files committed (4587 insertions)
- Tag: `pre-v2-initial` — baseline for v2 development
- Message: "Aurelius v2: ground-up redesign with MiniMax MCP integration, Material 3 UI, security fixes"

### Milestone 5: Engineering Build (2026-04-26) — COMPLETED
- BUILD SUCCESSFUL in 21s
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Fixes applied:
  - gradle-wrapper.jar (downloaded from Gradle GitHub)
  - ChatRepository.kt: Added `AttachmentType` import
  - ChatUseCases.kt: Fixed `searchChats` suspend function (`flow { emitAll(...) }`)
  - ChatScreen.kt: Fixed extra closing brace
  - ToolExecutor.kt: Fixed when-condition syntax
  - AppDatabase.kt: Set `exportSchema = false`
- All 4 UX items verified implemented (code review confirmed)
- Ready for QA phase

---

## [2026-04-19] v1.4.0 Release

### Added
- Koin DI with 4 modules: networkModule, databaseModule, repositoryModule, viewModelModule
- 7 UseCases for business logic separation
- Jetpack Compose UI (full migration from XML)
- Compose Material 3 dark theme (primary #4ADE80)
- Markdown rendering via compose-markdown
- API key validation in Settings
- Image compression before imgbb upload
- Video generation WorkManager background service
- Room proper migrations (removed fallbackToDestructiveMigration)
- Media tool result rendering (audio/video/image cards)

### Updated
- Kotlin 17, Compose BOM 2024.02.00, Coil 2.6.0, Gson 2.11.0

### Fixed
- Duplicate viewModelModule, viewBinding re-enabled, missing imports

---

## [2026-04-18] v1.3.0 Release

### Added
- Document & Image Attachment System (PDF OCR via ML Kit, TXT extraction)
- Image Understanding Tool (understand_image via /v1/coding_plan/vlm)
- Swipe-to-delete on Home Screen
- Enhanced Chat Cards with preview text and detailed timestamps

### Fixed
- DocumentExtractor lazy init crash, RecyclerView API compatibility

---

## [2026-04-17] v1.2.0 Release

### Added
- Chat history persistence via Room database
- HomeActivity with 5 recent chats and swipeable sidebar
- AI-generated chat titles
- Thinking & tool status UI with styled labels
- Chained tool call support (up to 5 rounds)

### Fixed (Major)
- Image generation API endpoint and response model
- Text-to-speech API endpoint and response format
- Video generation API (was sync, now fully async with polling)
- Music generation API endpoint
- Cursor blink bug
- Settings base URL label typo

---

## [2026-04-16] v1.1.0 Release

### Added
- Room persistence (ChatEntity, MessageEntity, DAOs)
- HomeActivity as launcher
- AI-generated chat titles
- Thinking labels replacing raw <think> tags
- Chained tool call detection and recursion

### Fixed
- Search API field names (query->q, results->organic, url->link)
- Tool result tool_call_id handling
- Assistant message in continuation requests
- choices null handling
- Coding Plan API key requirement for search

---

## [2026-04-16] v1.0.0 Initial Release

### Added
- MiniMax-M2.7 chat with non-streaming responses
- Five tool capabilities: image generation, text-to-speech, web search, video generation, music generation
- Interleaved Thinking support with visible <think> tags
- Tool call detection from both tool_calls array and Interleaved Thinking content
- Settings screen for API key and region configuration
- Progress bar loading indicator
- Blinking cursor animation during streaming