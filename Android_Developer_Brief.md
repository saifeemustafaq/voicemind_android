# VoiceMind AI — Android Developer Brief

**Purpose:** Read this to know exactly what to build and how it should behave. The Android app should achieve **MVP 0 parity** with the existing web app: same features, same flows, but backed by **Firebase** instead of a REST API.

**References:** `mvp.md` (scope), `VoiceMind_AI_Feature_Document.md` (vision, section 4.0 = current implementation), `Style_Guide_Compose.md` (visuals), `DeveloperGuide.md` (engineering practices).

---

## 1. What the app is

- **VoiceMind AI** is a mobile-first voice capture app. User taps record, speaks, then stops. The app uploads audio, transcribes it (via OpenAI `gpt-4o-mini-transcribe`), generates a short title, and extracts action items (tasks) from the transcript. The user can browse recordings, play them, read transcripts, organize by folders, and manage a checklist of tasks pulled from their voice notes.
- **Infrastructure:** Google Firebase.
  - **Auth:** Firebase Authentication (email/password or Google Sign-In for MVP; expand later).
  - **Database:** Cloud Firestore for recordings, folders, action items metadata.
  - **Storage:** Firebase Cloud Storage for audio files.
  - **Functions (optional):** Firebase Cloud Functions for server-side transcription and action-item extraction if you prefer not to call OpenAI directly from the client.
- **Transcription model:** OpenAI `gpt-4o-mini-transcribe` (Audio API, `/v1/audio/transcriptions`).

---

## 2. Firebase data models

All collections live in Firestore. Use the authenticated user's UID to scope data.

### 2.1 Collection: `users/{uid}/recordings`

Each document = one recording.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Document ID (auto-generated or `rec-{timestamp}-{random}`). |
| `title` | `String` | Max 25 chars. Default: date/time (e.g. "Feb 19 - 3:45 pm"). Updated by auto-title after transcription. |
| `folderId` | `String` | References a folder doc ID. Default: `"unfiled"`. |
| `createdAt` | `Timestamp` | Firestore server timestamp. |
| `transcription` | `String?` | Nullable. Filled after transcription completes. |
| `audioPath` | `String` | Path in Cloud Storage, e.g. `users/{uid}/audio/{id}.m4a`. |

### 2.2 Collection: `users/{uid}/folders`

Each document = one folder.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Document ID. |
| `name` | `String` | Folder display name. |
| `createdAt` | `Timestamp` | Firestore server timestamp. |

**Default folders:** On first app launch (or when the user's `folders` collection is empty), seed: Work, Meetings, Ideas, Personal, Journal, Archive, Unfiled. Use stable IDs (e.g. `"work"`, `"meetings"`, ..., `"unfiled"`).

**Reserved:** `"unfiled"` -- cannot be deleted or renamed.

### 2.3 Collection: `users/{uid}/actionItems`

Each document = one action item (task from a recording).

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Document ID. |
| `title` | `String` | Short task phrase, max 200 chars. |
| `completed` | `Boolean` | Default: `false`. |
| `recordingId` | `String?` | Nullable. Links to source recording doc ID. |
| `createdAt` | `Timestamp` | Firestore server timestamp. |

---

## 3. Transcription and AI pipeline

### 3.1 Flow after recording stops

1. **Upload audio** to Cloud Storage at `users/{uid}/audio/{recordingId}.m4a`.
2. **Create Firestore document** in `recordings` with `title`, `folderId` ("unfiled"), `audioPath`, `createdAt`. `transcription` is `null` initially.
3. **Transcribe:** Call OpenAI Audio API (`POST https://api.openai.com/v1/audio/transcriptions`) with model `gpt-4o-mini-transcribe`, attaching the audio file. Response: transcription text.
4. **Update recording** with `transcription` field in Firestore.
5. **Auto-title:** Call OpenAI Chat API (`gpt-4o-mini`) with prompt: "Based on this transcript, reply with a single short title (max 25 characters). Output only the title, no quotes or punctuation." Update `title` in Firestore.
6. **Extract action items:** Call OpenAI Chat API (`gpt-4o-mini`) with prompt: "Extract only explicit action items from this transcript: reminders, commitments, deadlines. Return a JSON array of strings; if none, return []. No other text." Parse the JSON array. For each item (max 5), create a document in `actionItems` with `title`, `completed: false`, `recordingId`, `createdAt`.

**Where to run this (two options):**
- **Option A (Cloud Function):** A Firebase Cloud Function triggered on audio upload or called via HTTPS. Keeps the OpenAI API key server-side. Recommended.
- **Option B (client-side):** The Android app calls OpenAI directly after upload. Simpler to build; API key must be stored securely (e.g. Firebase Remote Config, not hardcoded). Acceptable for MVP.

If transcription or extraction fails, log the error. The recording is still saved; `transcription` remains `null` and no action items are created. Do not block the user.

### 3.2 OpenAI API details

**Transcription:**
```
POST https://api.openai.com/v1/audio/transcriptions
Headers: Authorization: Bearer {OPENAI_API_KEY}
Body (multipart/form-data):
  file: (audio file)
  model: "gpt-4o-mini-transcribe"
  response_format: "text"
```
Response: plain text transcription.

**Auto-title:**
```
POST https://api.openai.com/v1/chat/completions
Headers: Authorization: Bearer {OPENAI_API_KEY}
Body (JSON):
  model: "gpt-4o-mini"
  messages: [{ role: "user", content: "Based on this transcript, reply with a single short title that identifies the content. Use at most 25 characters. Output only the title, no quotes or punctuation. Transcript:\n\n{transcript_text (first 2000 chars)}" }]
  max_tokens: 30
```

**Action-item extraction:**
```
POST https://api.openai.com/v1/chat/completions
Headers: Authorization: Bearer {OPENAI_API_KEY}
Body (JSON):
  model: "gpt-4o-mini"
  messages: [{ role: "user", content: "Extract only explicit action items from this transcript: reminders, commitments, deadlines, or phrases like 'I need to', 'I should', 'don't forget', 'remind me to'. One short phrase per item. Skip vague or purely conversational content. Return a JSON array of strings only; if none, return []. No other text. Transcript:\n\n{transcript_text (first 3000 chars)}" }]
  max_tokens: 150
```

---

## 4. Screens and navigation

Use a **bottom navigation bar** (Material 3) or a **navigation drawer**. All screens must be phone-first (readable, touch-friendly, consistent with Style_Guide_Compose.md).

1. **Home** -- Entry screen. Shows folder list (tappable, collapsible or grid) and recent recordings (latest 5-10). Fixed record button (FAB) at bottom-right.
2. **Recordings** -- Full list of recordings (or filtered by folder). Per row: play/pause, view transcript, rename, move to folder, delete, share. Long-press or overflow menu for actions.
3. **Checklist** -- To-do and Done sections; mark complete, delete, link to recording title.
4. **Folders** -- Manage folders: list, create, rename, delete. Tapping a folder opens Recordings filtered by that folder.
5. **Settings** -- Placeholder screen ("Settings will be available here") for now. Reserve for future: account, notifications, API key config.

---

## 5. How each feature works

### 5.1 Authentication

- On app launch, check Firebase Auth state. If not signed in, show a sign-in screen (email/password and/or Google Sign-In).
- After sign-in, use the user's UID to scope all Firestore and Storage paths.
- Provide a sign-out option (in Settings or profile area).

### 5.2 Recording flow (critical path)

1. **Start:** User taps the record FAB. App checks `RECORD_AUDIO` permission (request if needed). Start recording with `MediaRecorder` (output format: M4A / AAC, or OGG). Show a bottom sheet or full-screen recording UI:
   - Editable **title** (default: current date/time, e.g. "Feb 19 - 3:45 pm").
   - **Elapsed timer** (0:00, 0:01, ...).
   - **Pause** and **Resume**.
   - **Stop and save** -- primary action.
   - **Delete** -- discard recording without saving.

2. **Stop and save:**
   - Stop `MediaRecorder`. Get the audio file.
   - Show "Saving..." state (disable buttons).
   - Generate a recording ID (e.g. `rec-{System.currentTimeMillis()}-{random}`).
   - Upload audio to Cloud Storage: `users/{uid}/audio/{id}.m4a`.
   - Create Firestore document in `recordings` with `title`, `folderId` ("unfiled"), `audioPath`, `createdAt`. `transcription = null`.
   - Close recording UI. Show the recording in the list.
   - **In background (or Cloud Function):** Run transcription pipeline (transcribe -> auto-title -> extract action items -> update Firestore). UI updates reactively via Firestore snapshot listeners.

3. **Delete (during recording):** Stop recorder, delete temp file, close UI. No Firestore/Storage writes.

4. **Audio format:** Record to M4A (AAC) or OGG. OpenAI accepts mp3, mp4, m4a, wav, webm, ogg, flac. M4A is recommended (good quality, small size, native Android support).

### 5.3 Recordings list and playback

- **List:** Query `users/{uid}/recordings` ordered by `createdAt` desc. Optionally filter with `.whereEqualTo("folderId", folderId)`. Use a Firestore snapshot listener for real-time updates.
- **Playback:** Download audio from Cloud Storage URL (or use a signed URL / download URL). Use `MediaPlayer` or `ExoPlayer` to play. Show play/pause and a progress indicator.
- **Transcript:** Show `transcription` in a bottom sheet or detail screen. If null, show "No transcript".
- **Rename:** Update Firestore document: `title = newTitle.trim().take(25)`.
- **Move to folder:** Update Firestore document: `folderId = newFolderId`.
- **Delete:** Delete Firestore document and Cloud Storage file at `audioPath`.
- **Share audio:** Download audio file to a temp/cache directory, then launch Android share sheet with the file URI. Use `FileProvider` for secure sharing.
- **Copy / share transcript:** Copy `transcription` to clipboard or share as text via Android share sheet.

Long-press or overflow menu (three-dot) for rename/move/delete/share is recommended.

### 5.4 Folders

- **List:** Query `users/{uid}/folders` ordered by `createdAt` or `name`. Seed defaults on first launch if collection is empty.
- **Create:** Add Firestore document with `name`, `createdAt`.
- **Rename:** Update `name` field. Do not allow rename of `"unfiled"`.
- **Delete:** Confirm with user. Reassign all recordings in that folder to `"unfiled"` (batch update `folderId`). Then delete the folder document. Do not allow delete of `"unfiled"`.
- **Folder detail:** When user taps a folder, show Recordings filtered by that folder's ID.

### 5.5 Checklist (action items)

- **Fetch:** Query `users/{uid}/actionItems` ordered by `createdAt` desc. Use a snapshot listener for real-time updates.
- **Display:** Split into two sections: `completed == false` (To-do) and `completed == true` (Done). Two separate cards with **no vertical gap** between them (panels touch, no background strip -- see Style Guide).
  - Each item: checkbox (Material checkbox or custom checkmark icon), title text, and optional "From recording" link showing the **recording title** (not just "From recording"). To resolve: build a map of `recordingId -> title` from the recordings you've fetched; if the item has a `recordingId`, show the title and make it tappable (navigate to that recording or Recordings screen).
  - Link text color: darker blue (`#1E5A9E`).
- **Mark complete / incomplete:** Update Firestore `completed` field. Item moves between To-do and Done in UI.
- **Delete:** Delete Firestore document.
- **Refresh:** Snapshot listener handles real-time updates. After saving a new recording, action items appear automatically when Cloud Function / client writes them.

Action items are created when a recording is saved (server-side via Cloud Function, or client-side after transcription). The user does not create them manually.

### 5.6 Home

- **Folders:** Show folders from Firestore. Tappable rows or collapsible sections that navigate to folder's recordings.
- **Recent recordings:** Query recordings ordered by `createdAt` desc, limit 5-10. Show title and date. Tapping opens playback or recording detail.
- **Record button:** FAB at bottom-right. Starts the recording flow.

### 5.7 Settings

- Placeholder screen: "Settings will be available here."
- Show signed-in user email/name and a Sign Out button.
- Reserve for future: notification preferences, API key config, theme.

---

## 6. Firebase security rules (starter)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{uid}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

These ensure each user can only access their own data. Tighten as needed (e.g. validate document shapes, limit file sizes).

---

## 7. Error handling and edge cases

- **Network errors:** Show a clear message (e.g. Snackbar: "Could not load recordings. Check your connection.") and allow retry (pull-to-refresh or retry button). Firestore has offline persistence by default; reads may work offline, writes will queue.
- **Transcription failure:** Recording is saved without `transcription`. Show "No transcript" in UI. Do not crash or block.
- **Action items extraction failure:** Recording is saved; checklist has no new items for that recording. No special UI needed.
- **Microphone permission denied:** Before starting record, explain why the app needs the mic. If denied, show a message directing to Settings.
- **Unfiled folder:** Do not show "Delete" or "Rename" for the folder with ID `"unfiled"`.
- **Auth state changes:** If user signs out or token expires, navigate to sign-in screen. Use `AuthStateListener`.
- **Storage quota:** Handle upload failures gracefully; show retry option.

---

## 8. Summary checklist for the developer

- [ ] Firebase project setup: Auth, Firestore, Cloud Storage, (optional) Cloud Functions.
- [ ] Authentication: sign-in screen (email/password and/or Google), sign-out, auth state listener.
- [ ] Data models: `Recording`, `Folder`, `ActionItem` as Firestore documents under `users/{uid}/`.
- [ ] Recordings: list (snapshot listener), filter by folder, play (Cloud Storage download + MediaPlayer/ExoPlayer), transcript view, rename, move to folder, delete, share audio, copy/share transcript.
- [ ] Recording flow: FAB -> bottom sheet/screen with title, timer, Pause/Resume, Stop, Delete -> upload audio to Storage + create Firestore doc -> run transcription pipeline -> update recording + create action items.
- [ ] Transcription pipeline: OpenAI `gpt-4o-mini-transcribe` for transcript, `gpt-4o-mini` for auto-title and action-item extraction. Via Cloud Function or client-side.
- [ ] Folders: list, create, rename, delete (not Unfiled); seed defaults on first launch; folder detail = recordings filtered by folder.
- [ ] Checklist: snapshot listener on actionItems; To-do and Done sections (no gap between cards); mark complete/incomplete; delete; show "from recording" with recording title (darker blue); real-time updates.
- [ ] Home: folders + recent recordings + FAB record.
- [ ] Settings: placeholder + sign-out.
- [ ] Firestore security rules + Storage security rules.
- [ ] Visual alignment with `Style_Guide_Compose.md` (glass-style, palette colors, touch targets, no emoji, Material Icons).

Once this is done, the Android app will match MVP 0 behavior and be ready for production with Firebase.
