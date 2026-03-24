# VoiceMind AI - Widget Ideas

This document outlines every home-screen widget concept that could make VoiceMind AI faster and more intuitive to use without opening the app. Each widget is described with its purpose, size, interactions, underlying data, priority, and technical notes.

---

## Current State

VoiceMind has a single **Recording Widget** (Glance, 3x2). It shows a mic button when idle and switches to timer + discard/pause/stop controls during an active recording. It also handles signed-out and missing-mic-permission states. All recording actions route through `RecordingService` intents.

---

## 1. Recording Widgets

### 1A. Quick Record (1x1)

**Size:** 1x1 (60x60 dp min)

**What it shows:** A single large microphone button. Tapping it starts a recording immediately. While recording, the button changes to a pulsing stop icon.

**Interactions:**
- Tap mic -> starts `RecordingService` (foreground)
- Tap stop -> stops and saves the recording
- Long-press -> opens the app to Recordings tab

**Data sources:** `RecordingStateRepository` (active recording state), `AuthRepository` (signed-in check)

**Why it matters:** Many users just want to capture a thought as fast as possible. A 1x1 widget takes minimal home-screen space and removes every friction point -- no unlock-to-app-to-FAB flow.

**Priority:** HIGH

**Technical notes:** Straightforward simplification of the existing `RecordingWidget`. Uses the same `actionStartService` pattern. Only two visual states (idle mic / active stop). Falls back to "open app" if not signed in or mic permission missing.

---

### 1B. Recording + Recent (2x2)

**Size:** 2x2 (180x180 dp min)

**What it shows:** A record button at the top and the 2-3 most recent recordings listed below it with title, duration, and a play/pause icon.

**Interactions:**
- Tap record button -> start recording (same service flow)
- Tap a recording row -> opens `RecordingDetailScreen` for that recording
- Tap play icon on a row -> starts playback via `PlaybackService`

**Data sources:** `RecordingRepository.observeRecordings()` (latest 3), `RecordingStateRepository`, `PlaybackService`

**Why it matters:** Users who record frequently want to both capture new thoughts and quickly replay the last thing they recorded -- all without opening the app.

**Priority:** HIGH

**Technical notes:** Requires a Glance worker or `WorkManager` periodic job to refresh the recording list from Firestore. Widget state would need additional preference keys for recent recording titles, IDs, and durations. Playback can be triggered via `PlaybackService` intents.

---

### 1C. Now Playing / Active Session (3x2)

**Size:** 3x2 (270x180 dp min)

**What it shows:** During an active recording: live timer with animated recording indicator, waveform visualization (simplified), and full controls (discard, pause/resume, stop-save). During active playback: recording title, playback progress, and play/pause/skip controls. When idle: shows the standard mic button with a "Tap to record" label.

**Interactions:**
- Full recording controls (same as current widget)
- Playback transport controls (play/pause, skip back 5s, skip forward 5s)
- Tap title during playback -> opens `RecordingDetailScreen`

**Data sources:** `RecordingStateRepository`, `PlaybackService` state, `RecordingWidgetStateKeys`

**Why it matters:** Combines recording and playback into one widget. Users who listen back to recordings while multitasking get transport controls without pulling up the app.

**Priority:** MEDIUM

**Technical notes:** Extends the existing recording widget with a third mode (playback). `PlaybackService` would need to push widget state (current position, duration, title) similar to how `RecordingService.pushWidgetState()` works. Waveform visualization is limited in Glance -- a simplified level-bar or progress bar would substitute.

---

## 2. Checklist / Tasks Widgets

### 2A. Checklist Widget (4x2 or 4x3)

**Size:** 4x2 (min 340x180 dp) or resizable up to 4x3

**What it shows:** A scrollable list of incomplete (TO-DO) action items with checkboxes. Each row shows the task title and, if present, a due-date chip. An "Add task" button sits at the bottom or top.

**Interactions:**
- Tap checkbox -> marks the `ActionItem` as completed in Firestore (optimistic toggle)
- Tap task title -> opens `TaskDetailScreen` in the app
- Tap "Add task" -> opens the app to the Checklist tab with the add-task dialog pre-opened
- Pull/swipe a task (if Glance supports) -> delete (with undo)

**Data sources:** `ActionItemRepository.observeActionItems()` filtered to `completed == false`, sorted by `createdAt` descending

**Why it matters:** This is the single most requested widget concept. Checking off tasks without opening the app turns VoiceMind from a "record and forget" tool into an active task manager that lives on the home screen.

**Priority:** CRITICAL (highest)

**Technical notes:** Glance supports `LazyColumn` for scrollable lists. Each checkbox tap fires a `GlanceActionCallback` that calls `ActionItemRepository.toggleComplete(itemId)`. Widget refresh is triggered by a Firestore snapshot listener running in a `CoroutineWorker`. Overdue items can be highlighted with `WidgetColors.Destructive`. The widget needs auth state (same pattern as recording widget). State keys: list of item IDs, titles, completion states, due dates.

---

### 2B. Overdue Tasks Widget (2x2)

**Size:** 2x2 (180x180 dp min)

**What it shows:** A count badge with the number of overdue tasks, followed by a short list (top 2-3) of overdue task titles. Uses red/destructive coloring to create urgency.

**Interactions:**
- Tap anywhere -> opens the Checklist tab in the app (filtered or scrolled to overdue)
- Tap individual task -> opens `TaskDetailScreen`

**Data sources:** `ActionItemRepository.observeActionItems()` filtered to `completed == false && dueDate < now`

**Why it matters:** Users who set due dates on tasks need a persistent nudge. This widget acts as a passive reminder that catches the eye every time they look at their home screen.

**Priority:** MEDIUM

**Technical notes:** Simple count + list. Refreshes via periodic `WorkManager` job (every 15-30 min) or triggered when the app writes to Firestore. The "0 overdue" state shows a green checkmark with "All caught up."

---

### 2C. Today's Tasks Widget (3x2)

**Size:** 3x2 (270x180 dp min)

**What it shows:** Header "Today" with the current date. Below it, tasks due today (or overdue) with checkboxes. A progress indicator (e.g., "3 of 5 done") at the bottom.

**Interactions:**
- Tap checkbox -> toggles completion
- Tap task -> opens `TaskDetailScreen`
- Tap header -> opens Checklist tab

**Data sources:** `ActionItemRepository.observeActionItems()` filtered to tasks where `dueDate` falls on today (using user's app timezone from `NavPreferenceRepository`)

**Why it matters:** Gives users a daily-planner feel. Seeing "3 of 5 done" creates a completion motivation loop.

**Priority:** HIGH

**Technical notes:** Date comparison uses the app's timezone setting (`NavPreferenceRepository.appTimezone`). Progress bar can be a simple Glance `LinearProgressIndicator`. Empty state: "No tasks for today -- enjoy your day" with a relaxed icon.

---

### 2D. Quick Add Task (4x1)

**Size:** 4x1 (340x60 dp min)

**What it shows:** A thin bar with a text-input-style area and a "+" button. Tapping the input opens the app's Checklist tab with the add-task dialog pre-focused.

**Interactions:**
- Tap the input area -> opens the app to Checklist with add-task dialog
- Tap "+" button -> same behavior

**Data sources:** None for display; `ActionItemRepository.createItem()` on submission

**Why it matters:** The fastest path from "I need to remember this" to a saved task. Paired with the Checklist Widget (2A), this covers both input and output for tasks.

**Priority:** MEDIUM

**Technical notes:** Glance doesn't support actual text input in widgets. The widget is a visual affordance that launches `MainActivity` with an intent extra (e.g., `EXTRA_OPEN_ADD_TASK`) to pre-open the dialog. Visually styled to look like a text field for discoverability.

---

## 3. Recordings & Playback Widgets

### 3A. Recent Recordings Widget (4x2)

**Size:** 4x2 (340x180 dp min), resizable to 4x3

**What it shows:** A vertical list of the 3-5 most recent recordings. Each row: title (truncated), folder chip (colored), relative timestamp ("2h ago"), duration, and a play button.

**Interactions:**
- Tap play -> starts playback via `PlaybackService`
- Tap row (non-play area) -> opens `RecordingDetailScreen`
- Tap header/title "Recent Recordings" -> opens Recordings tab

**Data sources:** `RecordingRepository.observeRecordings()` (limit 5, ordered by `createdAt` desc), `FolderRepository` (for folder name resolution)

**Why it matters:** Users who record meeting notes or ideas throughout the day want to quickly find and replay their latest capture. This widget turns the home screen into a quick-access playback queue.

**Priority:** HIGH

**Technical notes:** Folder name resolution can be cached in widget preferences to avoid extra Firestore reads. Each row stores `recordingId`, `title`, `folderId`, `folderName`, `durationSeconds`, `createdAt` in Glance preferences. Play button sends an intent to `PlaybackService`. List refresh via `WorkManager` periodic task or Firestore listener in a coroutine worker.

---

### 3B. Pinned Recording Widget (2x2)

**Size:** 2x2 (180x180 dp min)

**What it shows:** A single user-selected recording with its title, folder, duration, and a large play/pause button. The user picks which recording to pin during widget configuration.

**Interactions:**
- Tap play/pause -> toggles playback of this specific recording
- Tap title area -> opens `RecordingDetailScreen`
- Long-press -> opens widget configuration to pick a different recording

**Data sources:** `RecordingRepository` (single document by ID), `PlaybackService`

**Why it matters:** When a user has one important recording they keep going back to (e.g., a key meeting, a lecture, an important idea), pinning it to the home screen eliminates navigation entirely.

**Priority:** LOW

**Technical notes:** Requires a `GlanceAppWidgetConfigurationActivity` for the initial setup -- user picks from a list of recordings. The selected `recordingId` is stored in widget preferences. Needs a refresh mechanism if the recording is deleted (fall back to "Recording removed -- tap to reconfigure").

---

### 3C. Folder Quick Access (2x2)

**Size:** 2x2 (180x180 dp min)

**What it shows:** A single folder's name, icon, and recording count. Configurable at widget setup time to point to any folder.

**Interactions:**
- Tap -> opens `FolderDetailScreen` for that folder
- Badge shows unplayed/new recording count (recordings added since last app open)

**Data sources:** `FolderRepository`, `RecordingRepository.observeRecordingsByFolder(folderId)` (count)

**Why it matters:** Users who organize recordings by project or context (e.g., "Work," "Meetings") can jump straight to the folder they care about. A "Meetings" widget on the home screen means one tap to see all meeting recordings.

**Priority:** LOW

**Technical notes:** Configuration activity lets user pick a folder. Count is refreshed periodically. If the folder is deleted, widget shows "Folder removed" with a reconfigure prompt.

---

## 4. Summary & Insights Widgets

### 4A. Latest Summary Widget (4x2)

**Size:** 4x2 (340x180 dp min)

**What it shows:** The most recent `CollectiveSummary` with a truncated preview (first 3-4 lines of the summary text), the date it was generated, and the number of source recordings.

**Interactions:**
- Tap -> opens the Summaries tab in the app (or directly to the summary detail sheet)
- Swipe left/right -> cycles through recent summaries (if Glance supports pager-like behavior)

**Data sources:** `CollectiveSummaryRepository` (latest by `createdAt`), `recordingTitles` for source count

**Why it matters:** Summaries are one of VoiceMind's most powerful AI features, but users have to open the app and navigate to the Summaries tab to see them. Surfacing the latest summary on the home screen means AI-generated insights are always visible.

**Priority:** MEDIUM

**Technical notes:** Summary text is plain markdown in Firestore. For the widget, strip markdown formatting and truncate to ~150 characters with "..." Read more. Store the latest summary's `id`, `summary` (truncated), `createdAt`, and `recordingTitles.size` in widget preferences.

---

### 4B. Daily Digest Widget (4x3)

**Size:** 4x3 (340x270 dp min)

**What it shows:** A combined dashboard for the day:
- **Top section:** Today's date and a greeting
- **Recordings row:** "X recordings today" with a mic icon
- **Tasks row:** "X tasks pending / Y completed today" with a checklist icon
- **Summary preview:** Latest summary snippet (2 lines)
- **Quick record button** at the bottom

**Interactions:**
- Tap recordings row -> opens Recordings tab
- Tap tasks row -> opens Checklist tab
- Tap summary preview -> opens Summaries tab
- Tap record button -> starts recording

**Data sources:** `RecordingRepository` (today's count), `ActionItemRepository` (pending/completed counts), `CollectiveSummaryRepository` (latest), `RecordingStateRepository`

**Why it matters:** This is the "everything at a glance" widget. It answers "What did I capture today? What do I need to do? What has the AI synthesized?" in a single look. It is the home-screen equivalent of the app's main navigation but without opening it.

**Priority:** HIGH

**Technical notes:** Most complex widget. Data aggregation happens in a `CoroutineWorker` that queries multiple repositories and writes a flattened state to Glance preferences. Refresh on app foreground, after recording save, and periodically (every 30 min via `WorkManager`). The greeting can be time-aware ("Good morning," "Good afternoon," etc.).

---

### 4C. AI Insight of the Day (3x2)

**Size:** 3x2 (270x180 dp min)

**What it shows:** A single highlighted insight extracted from the latest summary or a key action item. Rotates daily. Styled with a card-like appearance and a lightbulb or sparkle icon.

**Interactions:**
- Tap -> opens the source summary or task detail
- Swipe or auto-rotate -> next insight

**Data sources:** `CollectiveSummaryRepository` (parsed into sentences/bullets), `ActionItemRepository` (high-priority items)

**Why it matters:** Gives VoiceMind a "smart assistant" feel. Instead of the user seeking out information, the app proactively surfaces what might be important today. This is the kind of ambient intelligence that makes voice-first apps feel magical.

**Priority:** LOW

**Technical notes:** Requires light NLP to extract individual insights from summary text (split on bullet points or sentences). A daily `WorkManager` job selects the insight and writes it to widget preferences. Fallback if no recent summaries: show the most recent uncompleted task instead.

---

## 5. Productivity & Stats Widgets

### 5A. Weekly Stats Widget (2x2)

**Size:** 2x2 (180x180 dp min)

**What it shows:** Three stats in a compact card:
- Recordings this week (number + small bar chart showing daily distribution)
- Tasks completed this week
- Current streak (consecutive days with at least one recording)

**Interactions:**
- Tap -> opens the app (Recordings tab)

**Data sources:** `RecordingRepository` (filtered by `createdAt` within current week), `ActionItemRepository` (filtered by completion within current week)

**Why it matters:** Gamification through visibility. Users who see "12 recordings this week, 5-day streak" are motivated to keep going. Especially valuable for journaling use cases where consistency matters.

**Priority:** LOW

**Technical notes:** Stats are computed in a `WorkManager` job that runs once daily (or on recording save). Streak calculation walks backwards through `createdAt` dates. Minimal Firestore reads needed -- just counts with date range queries.

---

### 5B. Voice Journal Widget (3x2)

**Size:** 3x2 (270x180 dp min)

**What it shows:** Today's date in large text (e.g., "Monday, March 23"), the count of recordings made today, and a prominent "Record" button. Below the date, a motivational prompt rotates daily (e.g., "What's on your mind today?", "Capture your thoughts", "How was your day?").

**Interactions:**
- Tap record -> starts recording with title pre-set to today's date
- Tap date/count area -> opens Recordings tab filtered to today

**Data sources:** `RecordingRepository` (today's count), `RecordingService`

**Why it matters:** Targets users who treat VoiceMind as a voice journal. The daily prompt and date-centric layout reinforce the journaling habit. The pre-set title (e.g., "March 23 - Journal") reduces friction.

**Priority:** MEDIUM

**Technical notes:** The recording intent can include an extra for a default title (e.g., `EXTRA_DEFAULT_TITLE`). Prompts are a static list rotated by day-of-year. Today's recording count refreshes when the app writes a new recording.

---

## 6. Glanceable Status Widgets

### 6A. At-a-Glance Bar (4x1)

**Size:** 4x1 (340x60 dp min)

**What it shows:** A thin horizontal bar divided into three sections:
- Left: Pending tasks count with checklist icon (e.g., "5 tasks")
- Center: Recordings today with mic icon (e.g., "3 today")
- Right: Next deadline with clock icon (e.g., "Due in 2h" or "No deadlines")

**Interactions:**
- Tap left section -> opens Checklist
- Tap center section -> opens Recordings
- Tap right section -> opens `TaskDetailScreen` for the nearest-deadline task

**Data sources:** `ActionItemRepository` (pending count + nearest deadline), `RecordingRepository` (today's count)

**Why it matters:** The most space-efficient widget. Takes up just one row on the home screen but delivers three key data points. Ideal for users who want information density without sacrificing home-screen real estate.

**Priority:** HIGH

**Technical notes:** Three distinct click targets in a `Row`. Each section is a separate `Column` within the row, each with its own `clickable` action. Refresh via `WorkManager`. Deadline formatting uses relative time ("in 2h", "tomorrow", "overdue").

---

### 6B. Upcoming Deadlines (3x2)

**Size:** 3x2 (270x180 dp min)

**What it shows:** The next 3 tasks with deadlines, sorted by deadline date ascending. Each row shows: task title, deadline date, and a countdown label (e.g., "2 days left", "Tomorrow", "TODAY" in red, "OVERDUE" in red bold).

**Interactions:**
- Tap a task -> opens `TaskDetailScreen`
- Tap header -> opens Checklist tab

**Data sources:** `ActionItemRepository.observeActionItems()` filtered to `deadline != null && completed == false`, sorted by `deadline` ascending, limit 3

**Why it matters:** Deadlines are the highest-urgency data in VoiceMind. Users who set deadlines on voice-extracted tasks need persistent visibility into what's coming up. This widget is the difference between missing a deadline and catching it with time to spare.

**Priority:** MEDIUM

**Technical notes:** Countdown is computed at render time from `deadline` timestamp vs current time (using app timezone). Color coding: green for 3+ days, yellow/warning for 1-2 days, red/destructive for today or overdue. Empty state: "No upcoming deadlines" with a relaxed icon.

---

## Priority Summary

| Priority | Widget | Size |
|----------|--------|------|
| CRITICAL | Checklist Widget (2A) | 4x2 / 4x3 |
| HIGH | Quick Record (1A) | 1x1 |
| HIGH | Recording + Recent (1B) | 2x2 |
| HIGH | Today's Tasks (2C) | 3x2 |
| HIGH | Recent Recordings (3A) | 4x2 |
| HIGH | Daily Digest (4B) | 4x3 |
| HIGH | At-a-Glance Bar (6A) | 4x1 |
| MEDIUM | Now Playing (1C) | 3x2 |
| MEDIUM | Overdue Tasks (2B) | 2x2 |
| MEDIUM | Quick Add Task (2D) | 4x1 |
| MEDIUM | Latest Summary (4A) | 4x2 |
| MEDIUM | Voice Journal (5B) | 3x2 |
| MEDIUM | Upcoming Deadlines (6B) | 3x2 |
| LOW | Pinned Recording (3B) | 2x2 |
| LOW | Folder Quick Access (3C) | 2x2 |
| LOW | AI Insight of the Day (4C) | 3x2 |
| LOW | Weekly Stats (5A) | 2x2 |

---

## Recommended Implementation Order

**Phase 1 -- Core value (ship first):**
1. **Checklist Widget (2A)** -- the most-requested concept; turns the home screen into an active task manager
2. **Quick Record 1x1 (1A)** -- trivial to build from the existing widget; fills the "fastest capture" niche

**Phase 2 -- Daily driver:**
3. **At-a-Glance Bar (6A)** -- minimal space, maximum information density
4. **Today's Tasks (2C)** -- daily-planner feel with completion motivation
5. **Recent Recordings (3A)** -- quick replay without opening the app

**Phase 3 -- Power user:**
6. **Daily Digest (4B)** -- the "everything" widget for heavy users
7. **Recording + Recent (1B)** -- capture + replay in one widget
8. **Now Playing (1C)** -- playback controls on the home screen

**Phase 4 -- Polish and delight:**
9. Overdue Tasks, Quick Add Task, Latest Summary, Voice Journal, Upcoming Deadlines
10. Pinned Recording, Folder Quick Access, AI Insight, Weekly Stats

---

## Shared Technical Considerations

**State management:** All widgets follow the pattern established by `RecordingWidget` -- Glance preferences written by services/workers, read by `currentState<Preferences>()` in the widget composable.

**Data refresh strategy:** Firestore is the source of truth. A `CoroutineWorker` registered with `WorkManager` periodically queries the needed repositories and flattens results into Glance preference keys. Additionally, `RecordingService` and repository write operations should trigger immediate widget updates via `GlanceAppWidgetManager.updateAll()`.

**Auth handling:** Every widget must check `IS_SIGNED_IN` and show a "Sign in" fallback (same as current recording widget).

**Theming:** Use `GlanceTheme` and migrate `WidgetColors.kt` to dynamic color support (as noted in the UI overhaul doc) so widgets match the user's system theme.

**Configuration activities:** Widgets that require user selection (Pinned Recording, Folder Quick Access) need a `GlanceAppWidgetConfigurationActivity`. Most other widgets work without configuration.

**Glance limitations to keep in mind:**
- No real text input -- "Quick Add Task" must launch the app
- Limited animation support -- waveforms and pulsing indicators use static frame swaps on refresh
- `LazyColumn` is available for scrollable lists but performance degrades beyond ~10 items
- Click targets must be reasonably large (48dp minimum) for accessibility
