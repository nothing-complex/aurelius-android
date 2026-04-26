# Chat Organization Plan — Aurelius

## One-Liner
Smart chat titles and summaries with organizational features (pin, archive, search) for Aurelius home screen.

---

## 1. Current State Analysis

### Existing Data Model (`ChatEntity.kt`)
```
id: String (UUID)
title: String = "New chat"       // Generated from first assistant reply
preview: String = ""             // First 60 chars of most recent user message
createdAt: Long                  // Unix timestamp (ms)
updatedAt: Long                  // Unix timestamp (ms), controls sort order
```

### Existing Title Generation
- **Trigger**: After first assistant response completes
- **Method**: MiniMax API call via `GenerateTitleUseCase`
- **Model**: MiniMax-M2.7-highspeed (fast, cheap)
- **Update**: `chatDao.updateTitle(chatId, title)` persists to Room

### Current HomeScreen UI
- LazyColumn showing `chats.take(10)` (recent 10 only)
- ChatCard displays: title (1 line) + preview (1 line) + delete icon
- Sorted by `updatedAt` descending
- Full chat list in navigation drawer

---

## 2. Title Generation Strategy

### Option A: AI-Generated (Current, Recommended to Keep)
- **Pros**: Context-aware, semantically meaningful
- **Cons**: API cost, latency, occasional failures
- **Enhancement**: Add user-editable override

### Option B: First Message Truncation (Fallback)
- Use first user message, truncate to 50 chars
- Free, instant, deterministic
- Lower quality but zero cost

### Option C: Hybrid (Recommended)
1. **Primary**: AI-generated via MiniMax (current approach)
2. **Fallback**: First message truncation if AI fails
3. **User Override**: Allow manual title editing

### Title Length Strategy
- **Display**: Max 40 chars on home screen, ellipsis beyond
- **Database**: Max 60 chars (already enforced by GenerateTitleUseCase)
- **Generation Prompt**: "Create a short title (5 words or fewer) for this conversation"

---

## 3. Summary Generation Approach

### Definition
"Summary" = 2-3 word topic descriptor (e.g., "Code Review", "Budget Planning", "Recipe Ideas")

### Implementation Options

**Option A: AI-Generated Summary (Recommended)**
```
Prompt: "Extract the main topic in 2-3 words from this conversation.
         Return ONLY the topic phrase, no explanation."
```
- Generate alongside title in same API call
- Store as `topic: String` in ChatEntity

**Option B: Keyword Extraction**
- Extract most common noun phrases from messages
- Simpler but less accurate

**Option C: Persona-Based**
- Use active persona name as topic (e.g., "Coding Assistant", "Creative Writer")
- Works well for persona-switching users

### Summary Display
- Below title on chat card (smaller, muted color)
- Filterable by topic

---

## 4. UI Mockup Concepts

### 4a. Enhanced ChatCard (Home Screen)
```
┌─────────────────────────────────────────────────────┐
│ [PIN] Code Review                          [DELETE] │
│     Summary: API optimization                      │
│     Preview: Can you suggest a more efficient...   │
│     2 hours ago                                    │
└─────────────────────────────────────────────────────┘
```

### 4b. Filter Bar (Above Chat List)
```
[All] [Pinned] [Archived] [Today] [This Week] [Persona▾]
```

### 4c. Search Bar
```
┌─────────────────────────────────────────────────────┐
│ 🔍 Search conversations...                          │
└─────────────────────────────────────────────────────┘
```

### 4d. Long-Press Context Menu (ChatCard)
```
┌─────────────┐
│ 📌 Pin      │
│ 📁 Archive  │
│ ✏️  Edit Title│
│ 🗑️  Delete  │
└─────────────┘
```

### 4e. Archived Section (Drawer)
```
┌─────────────────────────────┐
│ 🗂️ Archived (12)            │
│   └── Collapsed by default  │
└─────────────────────────────┘
```

---

## 5. Data Model Changes

### Enhanced ChatEntity
```kotlin
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String = "New chat",
    val preview: String = "",
    val topic: String = "",                    // NEW: 2-3 word summary
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,             // NEW: pin state
    val isArchived: Boolean = false,           // NEW: archive state
    val pinnedAt: Long? = null,                // NEW: for sort order
    val archivedAt: Long? = null               // NEW: for archive sort
)
```

### New Fields Explained
| Field | Purpose |
|-------|---------|
| `topic` | Short summary for filtering/display |
| `isPinned` | Pins chat to top of list |
| `isArchived` | Hides from main list (archive section) |
| `pinnedAt` | Timestamp for pinned chats sort order |
| `archivedAt` | Timestamp for archived list sort order |

---

## 6. New Database Operations

### DAO Additions
```kotlin
@Dao interface ChatDao {
    // Existing
    fun getRecentChats(): Flow<List<ChatEntity>>
    fun getAllChats(): Flow<List<ChatEntity>>
    suspend fun updateTitle(chatId: String, title: String)

    // NEW: Organization
    suspend fun updateTopic(chatId: String, topic: String)
    suspend fun togglePin(chatId: String)
    suspend fun toggleArchive(chatId: String)
    suspend fun unarchive(chatId: String)  // Restore from archive

    // NEW: Query filters
    fun getPinnedChats(): Flow<List<ChatEntity>>
    fun getArchivedChats(): Flow<List<ChatEntity>>
    fun searchChats(query: String): Flow<List<ChatEntity>>

    // NEW: Batch operations
    suspend fun archiveAllOlderThan(timestamp: Long)
}
```

---

## 7. Implementation Sequence

### Phase 1: Data Model (Foundation)
1. Add new fields to ChatEntity (topic, isPinned, isArchived, pinnedAt, archivedAt)
2. Add migration to Room database
3. Add new DAO methods

### Phase 2: Basic UI (MVP)
1. Display topic below title on ChatCard
2. Add pin icon to ChatCard (filled when pinned)
3. Long-press menu with Pin/Unpin, Archive/Unarchive, Edit Title

### Phase 3: Organization Features
1. Pin toggle updates `pinnedAt` for sort order
2. Archived chats move to drawer section
3. Filter bar with All/Pinned/Archived/Date filters

### Phase 4: Search
1. Search bar with real-time filtering
2. Search across title, preview, and topic
3. Highlight matching text

### Phase 5: Summary Generation
1. Extend GenerateTitleUseCase to also generate topic
2. Single API call returns both title and topic
3. Fallback to first-message keyword if API fails

---

## 8. Sort Order Logic

### Main List (Default)
1. Pinned chats (sorted by `pinnedAt` DESC, newest first)
2. Unpinned chats (sorted by `updatedAt` DESC, newest first)
3. Archived chats excluded

### Pinned Section
1. Sorted by `pinnedAt` DESC

### Archived Section
1. Sorted by `archivedAt` DESC

---

## 9. Feature Priority Matrix

| Feature | Priority | Complexity | Value |
|---------|----------|------------|-------|
| Display topic on ChatCard | P0 | Low | Medium |
| Pin/Unpin chats | P0 | Low | High |
| Edit title | P0 | Low | High |
| Archive/Unarchive chats | P1 | Medium | Medium |
| Filter bar | P1 | Medium | Medium |
| Search | P1 | Medium | High |
| AI topic generation | P2 | Medium | Medium |
| Auto-archive old chats | P3 | Medium | Low |

---

## 10. Recommendations Summary

1. **Keep AI title generation** — it's working and valuable
2. **Add topic field** — single API call alongside title
3. **Implement pin first** — high value, low complexity
4. **Add edit title** — users often want to personalize
5. **Archive as secondary** — useful but can wait
6. **Search last** — requires more UI work

### Quick Win (MVP)
- Add `topic` field to ChatEntity
- Extend GenerateTitleUseCase to return topic
- Show topic below title on ChatCard
- Add pin icon and long-press menu with Pin/Edit/Delete