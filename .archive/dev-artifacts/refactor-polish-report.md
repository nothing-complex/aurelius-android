# Refactor & Polish Report - Aurelius v2

**Date:** 2026-04-26
**Review Scope:** Phase 2 Aurelius app — ChatScreen.kt, HomeScreen.kt, SettingsScreen.kt

---

## design-conformity-auditor: 5 deviations found

### Critical Deviations (must fix)

1. **Conversation Branching NOT implemented**
   - Expected: Visual tree navigation for branching conversations (SPEC.md Core Feature #3)
   - Actual: HomeScreen.kt shows a flat `LazyColumn` list of chats with no branching UI, tree visualization, or branch navigation
   - Impact: Core v2 feature missing

2. **AI Summaries NOT implemented**
   - Expected: Generate conversation summaries (SPEC.md Core Feature #4 — marked "key differentiator")
   - Actual: No summary generation logic in HomeScreen or ChatScreen; chat titles appear to be manual/placeholder
   - Impact: Key differentiating feature absent

3. **Persona System NOT implemented**
   - Expected: Character personas with avatars (SPEC.md Core Feature #5)
   - Actual: No persona selection, avatar display, or persona switching UI in any screen
   - Impact: Core v2 feature missing

### Minor Deviations (should fix)

4. **No bottom navigation bar** (SPEC.md Layout Approach specifies "Bottom navigation bar: Home, Explore, Settings")
   - HomeScreen.kt has `TopAppBar` only; no `NavigationBar`
   - This is a significant layout departure from spec

5. **No Quick Action Buttons in input bar** (SPEC.md UI/UX Feature #16)
   - Input bar is text field + send button only
   - Image, voice, music shortcuts not present in ChatScreen

---

## compose-ui-expert: 4 improvements possible

1. **Unstable lambda in LazyColumn items** (`ChatScreen.kt:237`)
   ```kotlin
   items(uiState.messages, key = { it.id }) { message ->
       MessageBubble(message = message)
   ```
   - The `message` lambda parameter can cause unnecessary recompositions when `messages` list reference changes but contents are same
   - Recommendation: Use `remember(message) { message.id }` or extract stable key outside

2. **Missing `derivedStateOf` for scroll-to-bottom logic** (`ChatScreen.kt`)
   ```kotlin
   LaunchedEffect(messages.size) {
       if (messages.isNotEmpty()) {
           listState.animateScrollToItem(messages.size - 1)
       }
   }
   ```
   - Fires on every messages.size change; if messages grow by 1, this scrolls unnecessarily on intermediate states
   - Recommendation: Use `derivedStateOf { messages.lastOrNull() }` to only scroll on actual new messages

3. **TypingIndicator animation could use GraphicsLayer** (`ChatScreen.kt`)
   - Current: `AnimatedVisibility` + `fadeIn`/`slideIn`
   - For smoother animation without overdraw: wrap dots in `GraphicsLayer` with `alpha` animation instead of full `AnimatedVisibility`

4. **SettingsScreen.kt line 89** — `fillMaxWidth()` modifier appears before `padding()` on `OutlinedTextField`
   - Correct order: `padding()` then `fillMaxWidth()` so padding is absorbed into measured width
   - Current: `modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)`
   - Should be: `modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()`

---

## feature-creep-sentinel: Verdict

**Features added (useful):**
- `AnimatedVisibility` for message bubble slide-in animations — adds delight
- `TypingIndicator` composable — adds perceived responsiveness
- `MarkdownText` composable for rendering — improves UX
- Proper MVVM with StateFlow and Koin DI — better architecture than v1

**Bloat to remove / address:**
- None — v2 actually removed complexity vs v1 (cleaned up dual UI stack, removed stale BACKEND_URL code)

**Incomplete features (not bloat, but not finished):**
- Conversation branching (Core Feature #3) — 0% implemented
- AI Summaries (Core Feature #4) — 0% implemented
- Persona System (Core Feature #5) — 0% implemented
- Media features (Voice Input, Music Playback, Web Search display) — partial at best

**Scope assessment:**
- v1 had: AI chat, basic settings, tool execution
- v2 planned: 17 total features (5 core + 5 media + 7 UI/UX)
- v2 implemented: ~8 features (AI chat, conversation list, markdown, typing indicator, basic settings)
- **Scope completion: ~47%** — significant work remaining

**Risk:** Medium. The 3 unimplemented core features (branching, summaries, personas) were the "key differentiators" per SPEC.md. Current implementation is a functional chat app but missing the v2 ambitions.

---

## Recommended Refactors (priority order)

### P0 — Core Feature Gaps (don't ship without these)

1. **Implement conversation branching OR descope it explicitly**
   - If keeping: Add branching model to Room schema, visual tree UI in HomeScreen
   - If removing: Update SPEC.md to remove Core Feature #3, update Phase2 brief

2. **Implement AI Summaries OR mark as future**
   - If keeping: Add `generate_title` tool call after chat creation, store in `ChatEntity.summary`
   - If removing: Update SPEC.md

3. **Implement Persona System OR remove from spec**
   - If keeping: Add `PersonaEntity`, persona picker UI, avatar display in `MessageBubble`
   - If removing: Update SPEC.md

### P1 — Compose Quality

4. Fix modifier order on SettingsScreen `OutlinedTextField`
5. Add `derivedStateOf` for scroll-to-bottom optimization
6. Consider `GraphicsLayer` for TypingIndicator animation performance

### P2 — Spec Alignment

7. Add `NavigationBar` with Home/Explore/Settings tabs OR document why bottom nav was replaced with top bar
8. Add Quick Action Buttons to ChatScreen input bar OR remove from spec

---

## Summary

**Overall verdit:** ~60% complete on v2 ambitions. Chat screen is well-built with solid Compose practices. HomeScreen is functional for flat chat lists but missing the branching/summary/persona features that define v2. SettingsScreen is clean.

**Primary risk:** Shipping as "v2" with core differentiating features missing. Recommend either (a) implementing the 3 core features before release, or (b) marketing current build as "v1.5" and moving core features to v2.1.