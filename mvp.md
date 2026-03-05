# VoiceMind AI — MVP Tracking

Reference: [VoiceMind_AI_Feature_Document.md](./VoiceMind_AI_Feature_Document.md) (vision, full catalog).

---

## Platform & Architecture

- **Native Android** — Jetpack Compose, Kotlin, MVVM
- **Backend** — Firebase (Auth, Firestore, Storage, Cloud Functions)
- **DI** — Hilt
- **Min SDK** 28 (Android 9) / **Target SDK** 35
- **Version** — `0.0.2` (versionCode auto-incremented on release via `version.properties`)
- **Distribution** — Firebase App Distribution

---

## MVP 0 — Implemented

Everything the app can do today.

### Authentication

- Email/password sign-in and sign-up.
- Google Sign-In via Credential Manager (OAuth).
- Persistent auth state; auto-redirect to Home when signed in.
- Sign out from Settings.

### Capture & Recording

- One-tap record from Home via fixed mic FAB.
- `RECORD_AUDIO` runtime permission requested before first recording.
- Audio recorded as **M4A / AAC** (44.1 kHz, 128 kbps) via `MediaRecorder`.
- Recording bottom sheet: editable title, elapsed timer, Pause / Resume, Stop & Save, Discard.
- On save: audio uploaded to **Firebase Storage** (`users/{uid}/audio/{recordingId}.m4a`), recording document created in **Firestore** (`users/{uid}/recordings`).
- No offline queue — recording requires network.

### Transcription & AI (Cloud Functions)

- `processRecording(recordingId, timezone)` — server-side transcription and action-item extraction triggered on save.
- Transcript stored on the recording document and displayed in-app.
- Auto-title generated from transcript (short phrase, max ~25 chars); title editable in the recording sheet before save.
- Action-item extraction: LLM identifies explicit tasks ("remind me to…", "I need to…"); creates up to 5 checklist items per recording, stored in `users/{uid}/actionItems` with a `recordingId` link.
- `generateSummary(recordingId)` — on-demand summary generation; summary stored on the recording and viewable in the Transcript sheet.

### Organization (Folders)

- Manual folders: list, create, rename, delete.
- Default folders seeded on first launch: Work, Meetings, Ideas, Personal, Journal, Archive, Unfiled.
- Move recordings to a folder from the Recordings list (context actions).
- Folder detail screen shows recordings filtered to that folder.
- Deleting a folder reassigns its recordings to Unfiled.
- New recordings default to Unfiled (no AI-suggested folder yet).

### Checklist (Action Items)

- Checklist screen: two sections — TO-DO and DONE.
- Items extracted from recordings appear in TO-DO; user can mark complete (moves to DONE), uncheck (back to TO-DO), or delete.
- "From recording" label shows recording title, linking back to Recordings.
- **Task detail screen** — editable title, completion toggle, due date & time, deadline, notes, delete.
- `calendarEventId` on items synced to Google Calendar (displayed as "Synced to Google Calendar" label).

### Recordings List & Playback

- Recordings screen: list all or filter by folder.
- Per-recording actions: play/pause, view transcript (sheet with tabs: **Transcript**, **Summary**, **Tasks**), rename, move to folder, delete, share audio file, copy transcript, share transcript.
- Audio playback via `MediaPlayer`; download URL fetched from Firebase Storage.
- On-demand summary generation from the transcript sheet.

### Home

- Collapsible folder list with per-folder recording counts.
- Recent recordings section.
- Fixed record FAB at bottom.

### Settings

- Account info display (email / display name).
- Sign out.
- Navigation mode toggle: **sidebar drawer** vs **bottom navigation bar** (persisted via DataStore Preferences).
- Google Calendar: connect and disconnect (OAuth via Identity API, token exchange via Cloud Function `exchangeCalendarAuthCode`, disconnect via `disconnectCalendar`).

### Google Calendar Integration

- OAuth consent requesting `calendar.events` scope.
- Token exchange handled server-side via Cloud Function.
- Calendar connection status stored in Firestore (`users/{uid}.calendarConnected`) and observed reactively.
- Action items with a `calendarEventId` display a sync indicator in the task detail screen.
- Actual event creation/sync is handled by Cloud Functions, not the Android client.

### Navigation

- Dual navigation modes (user-selectable in Settings):
  - **Sidebar** — full-height drawer with nav items.
  - **Bottom bar** — standard Material 3 bottom navigation.
- Routes: Home, Recordings, Checklist, Folders, Settings, Folder Detail (`folder_detail/{folderId}`), Task Detail (`task_detail/{itemId}`).
- Start destination: Home.

### Firebase Integration Summary

| Service | Usage |
|---------|-------|
| **Auth** | Email/password and Google sign-in; auth state flow |
| **Firestore** | Per-user collections: `recordings`, `folders`, `actionItems`; user doc for calendar status |
| **Storage** | Audio files at `users/{uid}/audio/{recordingId}.m4a` |
| **Cloud Functions** | `processRecording`, `generateSummary`, `exchangeCalendarAuthCode`, `disconnectCalendar` |

### Data Models

| Model | Key Fields |
|-------|------------|
| **Recording** | `id`, `title`, `folderId`, `createdAt`, `transcription`, `summary`, `audioPath` |
| **Folder** | `id`, `name`, `createdAt` |
| **ActionItem** | `id`, `title`, `completed`, `recordingId`, `createdAt`, `dueDate`, `deadline`, `notes`, `calendarEventId` |

### Theme & Design System

- iOS-inspired palette: `IosBackground`, `IosAccent`, `IosDestructive`, `IosSuccess`, `IosLabel`, etc.
- Inter font family with typography aligned to iOS sizing (headline, title, body, label).
- Shared design language across Android and iOS (see `Style_Guide_Compose.md` and `io_style.md`).
- Reusable components: `GlassCard`, `EmptyStateCard`, `PrimaryButton`, `VoiceMindTopAppBar`, `RecordFab`.

### Build & Release

- `assembleRelease` auto-bumps `VERSION_CODE` in `version.properties`.
- Firebase App Distribution configured with tester list.
- ProGuard disabled for now.
- Release signing via dedicated keystore.

---

## MVP 1 — Planned

### Admin Portal

- Web-based admin dashboard (Next.js + Firebase Admin SDK) for managing users, content, and feature access.
- User management: list, search, disable/enable, delete (cascading), view-as-user mode.
- Content inspection: browse all recordings, transcripts, summaries, and action items across users.
- Global feature flags (`config/features`): toggle transcription, summaries, calendar sync, action item extraction, maintenance mode, min app version.
- Per-user feature overrides and usage limits (max recordings, max storage).
- Admin roles: Super Admin, Admin, Viewer with permission matrix.
- Analytics: user growth, recording activity, task completion rates, feature adoption.
- System monitoring: Cloud Function health, storage trends, cost estimation proxies.
- Audit log for all administrative actions.

### Offline Support

- Offline capture queue: record without network, audio saved locally.
- Background upload via WorkManager when connectivity is restored.
- Firestore offline persistence already provides cached reads; extend to handle queued writes gracefully.

### Widgets

- Home screen widget: one-tap record button (starts recording directly from the widget).
- Recent recordings widget: shows last 3-5 recordings with tap-to-play.
- Quick-capture widget: minimal footprint, always visible on home screen.

### Quick Actions (Swipe / Slider Gestures)

- Swipe actions on recording rows: swipe-to-delete, swipe-to-move-to-folder, swipe-to-share.
- Swipe actions on folder rows: swipe-to-rename, swipe-to-delete.
- Swipe actions on checklist items: swipe-to-complete, swipe-to-delete.
- Long-press drag to reorder folders.

### Sharing Between Users

- Share a recording (audio + transcript + summary) with another VoiceMind user via invite link or email.
- Shared recordings appear in the recipient's Recordings list with a "Shared by" label.
- Shared folders: invite collaborators to a folder; all recordings in the folder are visible to invited users.
- Permission levels for shared content: view-only vs edit (can rename, move, add recordings).
- Shared content stored under a top-level `shared` collection or via access-control lists on existing documents.

### Calendar & Sign-In Improvements

- Calendar sync improvements: two-way sync (changes in Google Calendar reflect back in the app), recurring event support, multiple calendar selection.
- Calendar event preview in task detail screen (show event title, time, link to Google Calendar).
- Sign-in improvements: "Remember me" / biometric unlock (fingerprint / face), sign-in with Apple (future iOS parity), account linking (merge email + Google accounts).
- Token refresh handling: proactive refresh before expiration, clearer error messages on auth failures.

### Search

- Keyword search across recording titles, transcripts, and summaries.
- Basic semantic search (find recordings by meaning, not just exact words).
- Search results with highlighted matches and tap-to-navigate.

### Multi-Select & Collective Summarization ([#9](https://github.com/saifeemustafaq/voicemind_android/issues/9))

- Multi-select mode on Recordings screen: long-press to activate, checkboxes on rows, select all / deselect all.
- Top bar transforms during multi-select to show selection count and action icons.
- Bulk delete: cascading delete of all selected recordings (Firestore docs + Storage audio files).
- Bulk move to folder: folder picker, moves all selected recordings at once.
- Collective summarization: concatenate transcripts of selected recordings, generate a single combined summary via a new `generateCollectiveSummary` Cloud Function (OpenAI gpt-4o-mini).
- New Firestore collection `users/{uid}/collectiveSummaries` storing summary text, source recording IDs/titles, and creation timestamp.
- New **Summaries** screen accessible from bottom nav / sidebar: list of all collective summaries with preview, source recordings, and creation date.
- Full summary view with source recording links, share, copy to clipboard, and delete actions.

### Per-Day Combine Summary ([#19](https://github.com/saifeemustafaq/voicemind_android/issues/19))

- Summarize icon (✨) on each date group header in the Recordings list for one-tap collective summarization of all recordings from that day.
- Hidden when a date group has only 1 recording (nothing to combine).
- Reuses existing `collectiveSummarize()` flow; navigates to Summaries screen on success.
- Complements (does not replace) the multi-select summarize workflow.

### Summarization Loading UX ([#21](https://github.com/saifeemustafaq/voicemind_android/issues/21))

- Replace full-screen loading overlay with a non-blocking floating popup displaying "Generating Summary" with a sparkling/shimmering multicolor animation.
- Popup includes a "Hide" button — dismisses the popup while summarization continues in the background.
- On completion, show a top slide-down toast ("Summarization complete") instead of auto-navigating; tappable to go to Summaries.
- User can continue using the app (scroll, interact) while summarization runs.

### Pagination

- Paginated recent recordings on Home screen (load more on scroll instead of showing all at once).
- Paginated recordings list on the Recordings screen (Firestore cursor-based pagination).
- Paginated action items on the Checklist screen.
- Paginated folder detail recordings.

### Notification Recording Controls

- Persistent foreground notification while recording is active with media-style controls (pause/resume, stop, delete).
- Real-time recording duration displayed in the notification.
- Lock screen playback-style controls for recording management.
- Tapping the notification body navigates to the active recording screen.
- Notification non-dismissible during active recording; dismissible once stopped.

### Settings in Top App Bar

- Move Settings out of the bottom nav bar and sidebar drawer.
- Add a Settings gear icon to the right side of `VoiceMindTopAppBar`, visible on all main screens (Home, Recordings, Checklist, Folders).
- Settings icon hidden during multi-select mode (replaced by `MultiSelectTopBar` actions).
- Frees a slot in the bottom nav for higher-priority destinations.

### Folder Sorting & Unfiled Row Fix ([#20](https://github.com/saifeemustafaq/voicemind_android/issues/20))

- Two sort toggle buttons in the Folders top bar: sort by most recently used (latest recording timestamp per folder) or sort by recording count (highest first).
- No visual partition — just seamless reordering.
- Bug fix: Unfiled folder row height too short (bare `Icon` instead of `IconButton` for trailing element).
- Bug fix: Unfiled recording count misaligned to the right vs. other folders (same root cause — inconsistent trailing element width).

### Other

- Smart Save sheet with suggested destination folder and output toggles.
- Journaling mode (guided prompts + private folder).
- Push notifications for upcoming deadlines / due dates.

---

*Update this doc as features ship or scope changes.*
