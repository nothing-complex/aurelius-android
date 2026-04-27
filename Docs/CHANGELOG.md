# Aurelius Changelog

## [Unreleased] — Milestone 15: Chat Organization

### Planned Features
- **Smart 1-line titles** — AI-generated or first-message truncation, user-editable
- **Topic summaries** — 2-3 word descriptors for each chat (e.g., "Code Review", "Budget Planning")
- **Pin/Unpin chats** — highlight important conversations, sorted to top
- **Archive chats** — hide from main list, accessible in drawer
- **Search** — filter by title, preview, and topic
- **Filter bar** — All/Pinned/Archived/Today/This Week/Persona dropdown

### Data Model Changes (planned)
```kotlin
// New ChatEntity fields
topic: String = ""                    // 2-3 word summary
isPinned: Boolean = false             // pin state
isArchived: Boolean = false           // archive state
pinnedAt: Long? = null               // for sort order
archivedAt: Long? = null             // for archive sort
```

### Sort Order (planned)
1. Pinned chats (by `pinnedAt` DESC)
2. Unpinned chats (by `updatedAt` DESC)
3. Archived excluded from main list

### Full Plan
See `Docs/chat-organization-plan.md` for detailed implementation sequence.

---

## [Milestone 14] — 2026-04-26

### UI/UX Redesign Complete

#### Color Scheme
- Primary: Terracotta `#C2703A` (warm, earthy)
- Secondary: Amber `#E6A919`
- Background: Warm cream `#FAF7F2`
- Surface: Parchment `#F5F0E8`
- Added subtle noise/grain texture overlay

#### Typography
- Display/Headlines: **Libre Baskerville** (serif, elegant)
- Body/Labels: **Source Sans 3** (readable sans-serif)

#### Navigation
- Swipe-based: Home/Settings as horizontal swipes (no bottom nav icons)
- Subtle page indicator dots
- Chat as separate route via NavHost

#### Dynamic Header
- Floating header with serif "Aurelius" branding
- Shrinks from 56dp to 48dp on scroll down
- Fades to 60% alpha when collapsed
- Spring-based animation

### Bug Fixes Applied
1. Empty API key validation — now checks EITHER codingPlanKey OR minimaxApiKey
2. Error JSON detection — simplified to check `"error"` field only
3. Snackbar clear timing — waits for SnackbarResult.Dismissed before clearing
4. Silent return on null chatId — now sets error state instead of silent return
5. NavHost always present — prevents crash when navigating to chat

### QA Results
| Test | Result |
|------|--------|
| App loads | ✅ |
| Chat opens | ✅ |
| AI responds to "hello" | ✅ |
| Swipe navigation | ✅ |
| Dynamic header | ✅ |
| No crashes | ✅ |

---

## [Milestone 13] — Earlier
- Anthropic API support for sk-cp- Coding Plan keys
- `/anthropic/v1/messages` endpoint with MiniMax-M2.7 model
- UI polish and spacing fixes

---

## Older Milestones
See git history for full commit log.