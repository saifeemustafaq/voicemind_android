# VoiceMind AI — MVP tracking

Reference: [VoiceMind_AI_Feature_Document.md](./VoiceMind_AI_Feature_Document.md) (vision, full catalog, section 4.0 for current implementation).

---

## MVP 0 — Implemented

What we have shipped so far.

### Capture & recording
- One-tap record from Home via fixed mic button (FAB); recording starts with default title (date/time).
- Recording drawer (bottom sheet): editable title, elapsed timer, Pause / Resume, Stop and save, Delete; drawer above mic so controls are usable.
- On stop: audio uploaded (WebM), stored in `data/audio/`, recording row created. No offline queue.

### Transcription & AI
- Transcription via OpenAI (gpt-4o-transcribe) after save; transcript stored on recording and shown in app.
- Auto-title from transcript (short phrase, max 25 chars) via GPT; title editable in drawer before stop.
- Action-item extraction at save time: LLM extracts explicit tasks (“remind me to…”, “I need to…”, etc.); creates checklist items (max 5 per recording), stored with optional `recordingId`.

### Organization
- Manual folders: list, create, rename, delete. Default folders on first run (Work, Meetings, Ideas, Personal, Journal, Archive, Unfiled).
- Move recordings to folder from Recordings list (context menu). Folder detail page shows recordings in that folder.
- New recordings go to Unfiled (no AI-suggested folder yet).

### Checklist (in-app task list)
- Checklist screen in sidebar: two sections, To-do and Done (separate cards, no vertical gap).
- Items from recordings appear in To-do; user can mark complete (moves to Done), uncheck (back to To-do), or delete.
- “From recording” shows recording title (darker blue), links to Recordings. List refreshes when new recordings are saved.

### Recordings list & playback
- Recordings page: list all (or by folder). Per row: play/pause, view transcript (modal), rename, move to folder, delete, share audio (WebM), copy transcript, share transcript. Long-press / context menu. Audio from `GET /api/recordings/[id]/audio`.

### Home
- Collapsible folder list (navigate into folders) and recent recordings. Fixed record button at bottom.

### Settings
- Settings page present; placeholder only (“Settings will be available here”).

### APIs & data
- **Recordings:** GET (list, optional `folderId`), POST (multipart: file + title + folderId, or JSON create), PATCH/DELETE by id, GET `[id]/audio`.
- **Folders:** GET list, POST create, PATCH/DELETE by id; deleting folder reassigns recordings to Unfiled.
- **Action items:** GET list, PATCH (completed, title), DELETE by id; items created server-side on recording save.
- **Transcribe:** POST multipart (standalone); main flow uses internal transcribe + title + action extraction.
- **Storage:** `data/recordings.json`, `data/folders.json`, `data/action-items.json`, `data/audio/*.webm`.

---

## MVP 1 — Planned (from PRD)

- Offline capture queue and background upload.
- Clean summary (we have transcript + title only today).
- Search (keyword + basic semantic).
- Journaling mode (simple prompts + private folder).
- Smart Save sheet with suggested destination and output toggles.

---

*Update this doc as features ship or scope changes.*
