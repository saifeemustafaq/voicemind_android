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

- Offline capture queue and background upload (WorkManager).
- Search (keyword + basic semantic across transcripts).
- Smart Save sheet with suggested destination folder and output toggles.
- Journaling mode (guided prompts + private folder).
- Push notifications for upcoming deadlines / due dates.

---

*Update this doc as features ship or scope changes.*
