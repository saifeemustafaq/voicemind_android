# VoiceMind AI — Android Developer Brief

**Purpose:** Complete technical reference for the VoiceMind AI Android app. Covers architecture, Firebase integration, Google Calendar sync, data models, Cloud Functions, every screen, and every flow as currently implemented.

**Related docs:** `mvp.md` (scope), `VoiceMind_AI_Feature_Document.md` (product vision), `Style_Guide_Compose.md` (design system), `DeveloperGuide.md` (engineering practices), `SETUP_GUIDE.md` (first-time Firebase/project setup), `build.md` (release process).

---

## 1. What the app is

VoiceMind AI is a mobile-first voice capture app. The user taps record, speaks, then stops. The app uploads the audio, transcribes it server-side via OpenAI, generates a short title, extracts action items (tasks with optional dates), and can generate an on-demand summary. The user can browse recordings, play them, read transcripts and summaries, organize by folders, manage a checklist of extracted tasks with due dates and deadlines, and sync task dates to Google Calendar.

---

## 2. Architecture overview

```
┌─────────────────────────────────────────────────────┐
│  Android Client (Kotlin / Jetpack Compose)          │
│                                                     │
│  MainActivity ─── AuthViewModel (auth gate)         │
│       │                                             │
│  AppNavHost ─── Screens ─── ViewModels              │
│       │              │                              │
│  AudioRecorder   Repositories ──┐                   │
│                                 │                   │
│  ┌──────────────────────────────┤                   │
│  │  AuthRepository              │ Firebase Auth     │
│  │  RecordingRepository         │ Firestore         │
│  │  FolderRepository            │ Firestore         │
│  │  ActionItemRepository        │ Firestore         │
│  │  StorageRepository           │ Cloud Storage     │
│  │  GoogleCalendarRepository    │ Identity API +    │
│  │                              │ Cloud Functions   │
│  │  NavPreferenceRepository     │ DataStore         │
│  └──────────────────────────────┘                   │
│                                                     │
│  Hilt (DI) ─── AppModule provides Firebase SDK      │
└───────────────────┬─────────────────────────────────┘
                    │
        Firebase SDK (Auth, Firestore,
         Storage, Functions)
                    │
┌───────────────────▼─────────────────────────────────┐
│  Firebase Cloud Functions (TypeScript)              │
│                                                     │
│  processRecording   ─ transcribe + title + tasks    │
│  generateSummary    ─ on-demand summary             │
│  exchangeCalendarAuthCode ─ OAuth token exchange    │
│  disconnectCalendar ─ revoke + cleanup              │
│  syncActionItemToCalendar ─ Firestore trigger       │
│                                                     │
│  Secrets: OPENAI_API_KEY, GOOGLE_CLIENT_SECRET      │
└─────────────────────────────────────────────────────┘
```

| Layer | Tech |
|-------|------|
| Language | Kotlin |
| UI framework | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Architecture | MVVM (ViewModel + StateFlow + Compose) |
| DI | Hilt |
| Backend | Firebase (Auth, Firestore, Storage, Cloud Functions) |
| AI | OpenAI via Cloud Functions (gpt-4o-mini-transcribe, gpt-4o-mini) |
| Audio | MediaRecorder (M4A/AAC, 44.1 kHz, 128 kbps) |
| Playback | MediaPlayer (download URL from Storage) |
| Local preferences | Jetpack DataStore |
| Logging | Timber |
| Min SDK | 28 (Android 9) |
| Target / Compile SDK | 35 |
| Build system | Gradle (Kotlin DSL) + KSP |

---

## 3. Firebase integration

### 3.1 Firebase Auth

**Providers enabled:** Email/Password, Google Sign-In.

**Android implementation:**

- `AuthRepository` wraps `FirebaseAuth`.
- `authStateFlow` — `callbackFlow` from `AuthStateListener`; emits `FirebaseUser?`. Drives the auth gate in `MainActivity`.
- `signInWithEmail(email, password)` — `auth.signInWithEmailAndPassword().await()`.
- `signUpWithEmail(email, password)` — `auth.createUserWithEmailAndPassword().await()`.
- `signInWithGoogleCredential(idToken)` — builds `GoogleAuthProvider.getCredential(idToken, null)`, calls `auth.signInWithCredential().await()`.
- `signOut()` — `auth.signOut()`.
- Returns `sealed interface AuthResult { Success(user) | Error(message) }`.

**Google Sign-In flow (Credential Manager):**

1. User taps "Continue with Google" on `SignInScreen`.
2. `CredentialManager.getCredential()` with `GetGoogleIdOption(serverClientId = WEB_CLIENT_ID, filterByAuthorizedAccounts = false)`.
3. On success: `GoogleIdTokenCredential.createFrom(result.credential.data)` extracts the ID token.
4. `AuthViewModel.signInWithGoogle(idToken)` → `AuthRepository.signInWithGoogleCredential(idToken)` → Firebase Auth.
5. Error handling: `GetCredentialCancellationException` (silent), `NoCredentialException` ("No Google accounts found"), other exceptions shown as error.

**Web Client ID:** `685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com`

**Auth gate (MainActivity):**

```
if (isSignedIn) → AppNavHost
else → SignInScreen
```

`isSignedIn` is a `StateFlow<Boolean>` mapped from `authStateFlow`. Sign-out triggers immediate navigation to `SignInScreen`.

### 3.2 Cloud Firestore

All data is scoped under `users/{uid}/`. The UID comes from `FirebaseAuth.currentUser?.uid`.

**Collections:**

| Collection path | Document type | Listener |
|----------------|---------------|----------|
| `users/{uid}/recordings` | Recording | Snapshot listener (real-time) |
| `users/{uid}/folders` | Folder | Snapshot listener (real-time) |
| `users/{uid}/actionItems` | ActionItem | Snapshot listener (real-time) |
| `users/{uid}` | User profile | Snapshot listener for `calendarConnected` |
| `calendarTokens/{uid}` | Calendar tokens | Server-side only (Cloud Functions) |

All snapshot listeners use `callbackFlow` → `Flow<List<T>>`, collected in ViewModels via `collectAsStateWithLifecycle()`.

**Offline persistence:** Enabled by default. Reads may work offline; writes queue and sync when back online.

### 3.3 Firebase Cloud Storage

- Audio uploaded to: `users/{uid}/audio/{recordingId}.m4a`
- `StorageRepository.uploadAudio(recordingId, file)` — `ref.putFile(Uri.fromFile(file)).await()`, returns the storage path.
- `StorageRepository.getDownloadUrl(audioPath)` — `ref.downloadUrl.await()`, returns a `Uri` for playback or sharing.

### 3.4 Firebase Cloud Functions

All functions live in `functions/src/index.ts` (TypeScript). Deployed to Firebase, called from the Android app via `FirebaseFunctions.getInstance().getHttpsCallable("functionName")`.

**Secrets (stored in Google Cloud Secret Manager):**

| Secret | Used by |
|--------|---------|
| `OPENAI_API_KEY` | `processRecording`, `generateSummary` |
| `GOOGLE_CLIENT_SECRET` | `exchangeCalendarAuthCode`, `disconnectCalendar`, `syncActionItemToCalendar` |

**Global config:** `maxInstances: 10`.

#### Function: `processRecording`

- **Type:** `onCall` (callable)
- **Timeout:** 120 seconds
- **Input:** `{ recordingId: string, timezone?: string }`
- **Pipeline:**
  1. **Transcribe** — downloads audio from Storage, sends to OpenAI `gpt-4o-mini-transcribe` (`POST /v1/audio/transcriptions`, multipart, `response_format: text`). Writes `transcription` to the recording document.
  2. **Auto-title** — sends first 2000 chars of transcript to `gpt-4o-mini` with prompt: "reply with a single short title, max 25 characters." Writes `title` to the recording document.
  3. **Extract action items** — sends first 3000 chars of transcript to `gpt-4o-mini` with a detailed system prompt that understands relative dates, deadlines vs due dates, and timezone context. Parses JSON response into `{ title, dueDate?, deadline? }` objects. Batch-writes to `users/{uid}/actionItems` with `recordingId`, `completed: false`, `createdAt` (server timestamp), and optional `dueDate` / `deadline` as Firestore `Timestamp` values.
- **Error handling:** Each step is independent. If transcription fails, returns `{ success: false }`. If title or extraction fails, logs error but recording and transcript are preserved.

#### Function: `generateSummary`

- **Type:** `onCall` (callable)
- **Timeout:** 60 seconds
- **Input:** `{ recordingId: string }`
- **Logic:** If recording already has a `summary`, returns it (idempotent). Otherwise, sends first 4000 chars of transcript to `gpt-4o-mini` with prompt: "Summarize concisely in 3-5 sentences." Writes `summary` to the recording document.
- **Called from:** `RecordingRepository.generateSummary(recordingId)`, triggered when the user taps the Summary tab for the first time.

#### Function: `exchangeCalendarAuthCode`

- **Type:** `onCall` (callable)
- **Input:** `{ authCode: string }`
- **Logic:** Uses `googleapis` OAuth2 client to exchange the authorization code for tokens. Stores `refreshToken` in `calendarTokens/{uid}`. Sets `calendarConnected: true` on `users/{uid}`.

#### Function: `disconnectCalendar`

- **Type:** `onCall` (callable)
- **Logic:** Reads `calendarTokens/{uid}`, revokes the refresh token via Google OAuth2, deletes the token document, sets `calendarConnected: false` on `users/{uid}`.

#### Function: `syncActionItemToCalendar`

- **Type:** `onDocumentWritten` (Firestore trigger)
- **Trigger path:** `users/{uid}/actionItems/{itemId}`
- **Logic:**
  - Skips if the only field that changed is `calendarEventId` (prevents infinite loops).
  - Checks if `calendarTokens/{uid}` exists (user has calendar connected).
  - **Document deleted** → deletes the Google Calendar event if `calendarEventId` exists.
  - **Dates removed** → deletes the calendar event and removes `calendarEventId` from the document.
  - **Dates present, no existing event** → creates a new Google Calendar event and writes `calendarEventId` back to the action item document.
  - **Dates present, existing event** → updates the calendar event. If 404, creates a new one.
  - **Event format:** `dueDate` → timed event (30-min duration); `deadline` → all-day event. Event summary = task title, description = "Created by VoiceMind AI".
  - **Token expiration:** If 401, auto-disconnects calendar (deletes tokens, sets `calendarConnected: false`).

---

## 4. Google Calendar integration (end-to-end)

### Connect flow

1. User toggles "Google Calendar" switch ON in Settings.
2. `SettingsViewModel.connectCalendar(activity)` → `GoogleCalendarRepository.requestCalendarAccess(activity)`.
3. Repository builds `AuthorizationRequest` with `Scope("https://www.googleapis.com/auth/calendar.events")` and `requestOfflineAccess(WEB_CLIENT_ID)`.
4. Calls `Identity.getAuthorizationClient(activity).authorize(request).await()`.
5. If consent is needed → returns `CalendarConnectResult.NeedsConsent(result)` → SettingsScreen launches the consent `PendingIntent` via `ActivityResultLauncher`.
6. User grants consent → `onConsentResultHandled(result)` extracts `serverAuthCode`.
7. `exchangeAuthCode(authCode)` calls Cloud Function `exchangeCalendarAuthCode`.
8. Server exchanges code for refresh token, stores it in `calendarTokens/{uid}`, sets `calendarConnected: true`.
9. `observeCalendarConnected()` (Firestore listener on `users/{uid}`) emits `true` → UI updates.

### Disconnect flow

1. User toggles switch OFF → `SettingsViewModel.disconnectCalendar()`.
2. Calls Cloud Function `disconnectCalendar` → server revokes token, deletes `calendarTokens/{uid}`, sets `calendarConnected: false`.
3. Firestore listener emits `false` → UI updates.

### Sync behavior

- Fully server-side via the `syncActionItemToCalendar` Firestore trigger.
- The Android app never calls the Google Calendar API directly.
- When a user sets a `dueDate` or `deadline` on a task (TaskDetailScreen), the Firestore write triggers the Cloud Function, which creates/updates the calendar event and writes `calendarEventId` back to the action item.
- Deleting dates removes the calendar event. Deleting the task deletes the event.
- The TaskDetailScreen shows a "Synced to Google Calendar" label when `calendarEventId` is present.

---

## 5. Data models

### 5.1 Recording

**Firestore path:** `users/{uid}/recordings/{id}`

| Field | Type | Default | Annotations | Notes |
|-------|------|---------|-------------|-------|
| `id` | `String` | `""` | `@DocumentId` | Format: `rec-{timestamp}-{random}` |
| `title` | `String` | `""` | — | Max 25 chars. Auto-generated from transcript |
| `folderId` | `String` | `"unfiled"` | — | References a folder document ID |
| `createdAt` | `Timestamp?` | `null` | `@ServerTimestamp` | Set on creation |
| `transcription` | `String?` | `null` | — | Filled by `processRecording` |
| `summary` | `String?` | `null` | — | Filled by `generateSummary` |
| `audioPath` | `String` | `""` | — | Storage path: `users/{uid}/audio/{id}.m4a` |

### 5.2 Folder

**Firestore path:** `users/{uid}/folders/{id}`

| Field | Type | Default | Annotations | Notes |
|-------|------|---------|-------------|-------|
| `id` | `String` | `""` | `@DocumentId` | Stable IDs for defaults |
| `name` | `String` | `""` | — | Display name |
| `createdAt` | `Timestamp?` | `null` | `@ServerTimestamp` | Set on creation |

**Companion object constants:**

- `UNFILED_ID = "unfiled"` — cannot be deleted or renamed.
- `DEFAULTS` — 7 folders seeded on first launch: Work, Meetings, Ideas, Personal, Journal, Archive, Unfiled.

### 5.3 ActionItem

**Firestore path:** `users/{uid}/actionItems/{id}`

| Field | Type | Default | Annotations | Notes |
|-------|------|---------|-------------|-------|
| `id` | `String` | `""` | `@DocumentId` | Auto-generated |
| `title` | `String` | `""` | — | Max 200 chars, short task phrase |
| `completed` | `Boolean` | `false` | — | To-do vs done |
| `recordingId` | `String?` | `null` | — | Links to source recording |
| `createdAt` | `Timestamp?` | `null` | `@ServerTimestamp` | Set on creation |
| `dueDate` | `Timestamp?` | `null` | — | Scheduled datetime (when to do it) |
| `deadline` | `Timestamp?` | `null` | — | Finish-by date |
| `notes` | `String?` | `null` | — | User-added notes |
| `calendarEventId` | `String?` | `null` | — | Google Calendar event ID (set by Cloud Function) |

### 5.4 User profile document

**Firestore path:** `users/{uid}`

| Field | Type | Notes |
|-------|------|-------|
| `calendarConnected` | `Boolean` | Whether Google Calendar is linked |

### 5.5 Calendar tokens (server-side only)

**Firestore path:** `calendarTokens/{uid}`

| Field | Type | Notes |
|-------|------|-------|
| `refreshToken` | `String` | Google OAuth2 refresh token |
| `connectedAt` | `Timestamp` | Server timestamp |

---

## 6. Repositories

### AuthRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `currentUser` | `val: FirebaseUser?` | Current Firebase user |
| `authStateFlow` | `val: Flow<FirebaseUser?>` | Reactive auth state via `AuthStateListener` |
| `signInWithEmail` | `suspend (email, password) → AuthResult` | `signInWithEmailAndPassword` |
| `signUpWithEmail` | `suspend (email, password) → AuthResult` | `createUserWithEmailAndPassword` |
| `signInWithGoogleCredential` | `suspend (idToken) → AuthResult` | `GoogleAuthProvider` credential → `signInWithCredential` |
| `signOut` | `fun ()` | `auth.signOut()` |

### RecordingRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `observeRecordings` | `fun () → Flow<List<Recording>>` | Snapshot listener, ordered by `createdAt` desc |
| `observeByFolder` | `fun (folderId) → Flow<List<Recording>>` | Filtered snapshot listener |
| `createRecording` | `suspend (recording) → String` | Sets document with server timestamp |
| `updateTitle` | `suspend (recordingId, title)` | Trims to 25 chars |
| `moveToFolder` | `suspend (recordingId, folderId)` | Updates `folderId` |
| `deleteRecording` | `suspend (recording)` | Deletes Firestore doc + Storage file |
| `getRecording` | `suspend (recordingId) → Recording?` | Single document get |
| `generateSummary` | `suspend (recordingId) → String` | Calls `generateSummary` Cloud Function |
| `reassignFolder` | `suspend (fromFolderId, toFolderId)` | Batch update recordings when folder is deleted |

### FolderRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `observeFolders` | `fun () → Flow<List<Folder>>` | Snapshot listener, ordered by `createdAt` asc |
| `seedDefaultsIfEmpty` | `suspend ()` | Seeds 7 default folders if collection is empty |
| `createFolder` | `suspend (name) → String` | Creates doc with auto ID |
| `renameFolder` | `suspend (folderId, newName)` | Skips if `UNFILED_ID` |
| `deleteFolder` | `suspend (folderId)` | Skips if `UNFILED_ID` |

### ActionItemRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `observeActionItems` | `fun () → Flow<List<ActionItem>>` | Snapshot listener, ordered by `createdAt` desc |
| `observeActionItem` | `fun (itemId) → Flow<ActionItem?>` | Single-document snapshot listener |
| `toggleCompleted` | `suspend (itemId, completed)` | Updates `completed` |
| `updateTitle` | `suspend (itemId, title)` | Updates `title` |
| `updateDueDate` | `suspend (itemId, dueDate: Timestamp?)` | Updates `dueDate` (triggers calendar sync) |
| `updateDeadline` | `suspend (itemId, deadline: Timestamp?)` | Updates `deadline` (triggers calendar sync) |
| `updateNotes` | `suspend (itemId, notes: String?)` | Updates `notes` |
| `deleteItem` | `suspend (itemId)` | Deletes document (triggers calendar event deletion) |
| `getByRecordingId` | `suspend (recordingId) → List<ActionItem>` | Query by `recordingId` |

### StorageRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `uploadAudio` | `suspend (recordingId, file) → String` | Uploads to `users/{uid}/audio/{recordingId}.m4a` |
| `getDownloadUrl` | `suspend (audioPath) → Uri` | Returns download URL |

### GoogleCalendarRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `requestCalendarAccess` | `suspend (activity) → CalendarConnectResult` | Identity API authorization request |
| `handleConsentResult` | `suspend (AuthorizationResult) → CalendarConnectResult` | Extracts auth code after user consent |
| `disconnectCalendar` | `suspend () → Boolean` | Calls `disconnectCalendar` Cloud Function |
| `observeCalendarConnected` | `fun () → Flow<Boolean>` | Firestore listener on `users/{uid}.calendarConnected` |

`CalendarConnectResult`: `Success | NeedsConsent(result) | Error(message)`

### NavPreferenceRepository

| Method | Signature | What it does |
|--------|-----------|--------------|
| `useSidebar` | `val: Flow<Boolean>` | DataStore key `use_sidebar_nav`, default `false` |
| `setUseSidebar` | `suspend (value: Boolean)` | Persists preference |

---

## 7. Dependency injection (Hilt)

**`VoiceMindApp`** — `@HiltAndroidApp` application class.

**`MainActivity`** — `@AndroidEntryPoint`, injects `NavPreferenceRepository`.

**`AppModule`** — `@Module @InstallIn(SingletonComponent::class)`, provides as `@Singleton`:

| Provider | Returns |
|----------|---------|
| `provideFirebaseAuth()` | `FirebaseAuth.getInstance()` |
| `provideFirestore()` | `FirebaseFirestore.getInstance()` |
| `provideStorage()` | `FirebaseStorage.getInstance()` |
| `provideFunctions()` | `FirebaseFunctions.getInstance()` |

All repositories use `@Singleton @Inject constructor`. All ViewModels use `@HiltViewModel @Inject constructor`. `AudioRecorder` uses `@Singleton @Inject constructor`.

---

## 8. Screens and navigation

### 8.1 Navigation structure

**Routes** (sealed class with `route`, `label`, `icon`):

| Route | Screen | Parameters |
|-------|--------|------------|
| `home` | HomeScreen | — (start destination) |
| `recordings` | RecordingsScreen | — |
| `checklist` | ChecklistScreen | — |
| `folders` | FoldersScreen | — |
| `settings` | SettingsScreen | — |
| `folder_detail/{folderId}` | FolderDetailScreen | `folderId` |
| `task_detail/{itemId}` | TaskDetailScreen | `itemId` |

**Navigation modes** (user-selectable in Settings, persisted via DataStore):

- **Bottom bar** (`useSidebar = false`, default) — Material 3 bottom navigation.
- **Sidebar drawer** (`useSidebar = true`) — `ModalNavigationDrawer` with `SidebarDrawer`.

**Navigation behavior:** `popUpTo` start destination with `saveState`, `launchSingleTop`, `restoreState`. Sidebar closes the drawer before navigating.

### 8.2 Authentication screen

**SignInScreen** — email/password fields (sign in or create account, toggled), "Continue with Google" button, loading state, error Snackbar.

### 8.3 Home

- Collapsible folder list with per-folder recording counts.
- Recent recordings section (latest, ordered by `createdAt` desc).
- Fixed record FAB at bottom.
- Tapping a folder navigates to `folder_detail/{folderId}`.
- Tapping a recording opens playback / transcript actions.

### 8.4 Recordings

- Full list of recordings (or filtered by folder when accessed via folder detail).
- Per-recording actions: play/pause, view transcript sheet, rename, move to folder, delete, share audio, copy transcript, share transcript.
- **Transcript sheet** — bottom sheet with three tabs:
  - **Transcript** — full transcription text.
  - **Summary** — on-demand (generated via `generateSummary` Cloud Function on first view; cached after).
  - **Tasks** — action items extracted from this recording.
- Uses `MediaPlayer` for audio playback with download URL from Storage.
- Context menu / long-press for rename, move, delete, share.
- `RecordingDialogsHost` orchestrates `TranscriptSheet`, `RenameRecordingDialog`, `MoveToFolderDialog`, `DeleteRecordingDialog`.

### 8.5 Recording flow (critical path)

1. **Start:** User taps the record FAB → `RECORD_AUDIO` permission check → `AudioRecorder.start()`.
   - Creates temp file in cache: `recording_{timestamp}.m4a`.
   - `MediaRecorder` configured: source `MIC`, format `MPEG_4`, encoder `AAC`, sample rate 44100, bit rate 128000.
   - Recording bottom sheet appears: editable title (default: formatted date/time), elapsed timer, Pause / Resume, Stop & Save, Discard.

2. **Pause / Resume:** `MediaRecorder.pause()` / `resume()`, timer pauses/resumes. `RecorderState` sealed interface: `Idle | Recording | Paused`.

3. **Stop and save:**
   - `AudioRecorder.stop()` → returns the audio file.
   - UI shows "Saving..." (buttons disabled).
   - Generate recording ID: `rec-{System.currentTimeMillis()}-{random 1000..9999}`.
   - `StorageRepository.uploadAudio(recordingId, file)` → Firebase Storage.
   - `RecordingRepository.createRecording(Recording(id, title.trim().take(25), folderId, audioPath))` → Firestore.
   - `FirebaseFunctions.getHttpsCallable("processRecording").call({ recordingId, timezone })` → triggers server-side AI pipeline.
   - Delete local temp file.
   - Reset UI state, close bottom sheet.
   - Firestore snapshot listeners automatically update the recordings list as `transcription`, `title`, and action items are written by the Cloud Function.

4. **Discard:** `AudioRecorder.discardAndRelease()` → stops recorder, deletes temp file, closes sheet.

5. **Cleanup:** `RecordingViewModel.onCleared()` cancels timer and releases `AudioRecorder`.

### 8.6 Folders

- List all folders with recording counts.
- Create: dialog → `FolderRepository.createFolder(name)`.
- Rename: dialog → `FolderRepository.renameFolder(folderId, newName)`. Blocked for `unfiled`.
- Delete: confirmation → `RecordingRepository.reassignFolder(folderId, "unfiled")` then `FolderRepository.deleteFolder(folderId)`. Blocked for `unfiled`.
- Folder detail: top bar with folder name + `RecordingsScreen` filtered to that folder.

### 8.7 Checklist

- Two sections: **TO-DO** (`completed == false`) and **DONE** (`completed == true`). Separate cards, no vertical gap.
- Each item: checkbox, title, "From recording" link showing the recording title (darker blue, `#1E5A9E`).
- Mark complete / incomplete: `ActionItemRepository.toggleCompleted(itemId, completed)`.
- Tapping an item navigates to `task_detail/{itemId}`.
- Recording title resolution: `RecordingsViewModel` builds a `recordingId → title` map.

### 8.8 Task detail

- Single task view: editable title, completion toggle, due date picker, time picker, deadline picker, notes text field, delete button.
- Updates are written to Firestore via `ActionItemRepository` (`updateTitle`, `updateDueDate`, `updateDeadline`, `updateNotes`, `toggleCompleted`, `deleteItem`).
- Shows "Synced to Google Calendar" label when `calendarEventId != null`.
- Date/time changes trigger `syncActionItemToCalendar` Cloud Function automatically.

### 8.9 Settings

- **Account:** displays user email or display name. Sign Out button.
- **Navigation:** "Use sidebar navigation" toggle (switch), persisted via `NavPreferenceRepository`.
- **Integrations:** "Google Calendar" toggle (switch). When ON, initiates the calendar connect flow. When OFF, calls `disconnectCalendar`. Shows loading spinner during operations. Shows description text: "Task dates sync to your calendar" (connected) / "Sync task dates to Google Calendar" (disconnected).
- **Version:** `BuildConfig.VERSION_NAME (BuildConfig.VERSION_CODE)` at the bottom.

---

## 9. Audio recording details

**`AudioRecorder`** (`@Singleton`, `@Inject`):

| Config | Value |
|--------|-------|
| Audio source | `MediaRecorder.AudioSource.MIC` |
| Output format | `MediaRecorder.OutputFormat.MPEG_4` |
| Audio encoder | `MediaRecorder.AudioEncoder.AAC` |
| Sample rate | 44,100 Hz |
| Bit rate | 128,000 bps |
| Output file | `{cacheDir}/recording_{timestamp}.m4a` |

- API 31+ uses `MediaRecorder(context)` constructor; older APIs use deprecated `MediaRecorder()`.
- `RecorderState` sealed interface: `Idle`, `Recording`, `Paused`.
- `discardAndRelease()` stops recorder and deletes the temp file.
- Files are deleted from cache after successful upload to Storage.
- `FileProvider` (authorities: `${applicationId}.fileprovider`, paths: `cache-path` named `audio_cache`) enables secure sharing of audio files.

---

## 10. ViewModels

| ViewModel | Screen | Key state | Key actions |
|-----------|--------|-----------|-------------|
| `AuthViewModel` | SignInScreen | `isSignedIn: StateFlow<Boolean>`, `AuthUiState(isLoading, error, isCreateAccount)` | `signInWithEmail`, `signUpWithEmail`, `signInWithGoogle`, `signOut`, `toggleCreateAccount` |
| `HomeViewModel` | HomeScreen | Folders, recent recordings, folder counts | Load folders and recent recordings |
| `RecordingViewModel` | RecordingBottomSheet | `RecordingUiState(isRecording, isPaused, isSaving, title, elapsedSeconds, showSheet)` | `startRecording`, `pauseRecording`, `resumeRecording`, `stopAndSave`, `discardRecording` |
| `RecordingsViewModel` | RecordingsScreen | Recordings list, playback state, transcript/summary | Play/pause, rename, move, delete, share, generate summary |
| `ChecklistViewModel` | ChecklistScreen | To-do items, done items, recording title map | Toggle completed |
| `TaskDetailViewModel` | TaskDetailScreen | Single `ActionItem`, editable fields | Update title, due date, deadline, notes, completion, delete |
| `FoldersViewModel` | FoldersScreen | Folders list with counts | Create, rename, delete |
| `SettingsViewModel` | SettingsScreen | `useSidebar`, `calendarConnected`, `calendarLoading`, `calendarError` | `toggleNavMode`, `connectCalendar`, `disconnectCalendar` |

---

## 11. Reusable UI components

| Component | File | Purpose |
|-----------|------|---------|
| `VoiceMindTopAppBar` | `ui/components/` | Top app bar with title, icon, optional menu/back |
| `RecordFab` | `ui/components/` | Mic FAB with `RECORD_AUDIO` permission handling |
| `GlassCard` | `ui/components/` | Card surface with rounded corners |
| `EmptyStateCard` | `ui/components/` | Empty state with icon and message |
| `PrimaryButton` | `ui/components/` | Primary action button |
| `VoiceMindTextFieldColors` | `ui/components/` | `voiceMindTextFieldColors()` for `OutlinedTextField` |
| `RecordingDialogsHost` | `ui/components/` | Orchestrates transcript, rename, move, delete dialogs |

---

## 12. Theme and design system

| File | Content |
|------|---------|
| `ui/theme/Color.kt` | iOS-inspired palette: `IosBackground`, `IosAccent` (`#007AFF`), `IosDestructive`, `IosSuccess`, `IosLabel`, `IosSecondaryLabel`, `IosSeparator`, etc. |
| `ui/theme/Type.kt` | Inter font family. Typography scale aligned to iOS sizing: headline, title, body, label |
| `ui/theme/Theme.kt` | `VoiceMindAITheme` composable. Custom `IosColorScheme`, `VmTypography`, rounded shapes |

Visual language is shared between Android and iOS apps. See `Style_Guide_Compose.md` for full design system and `io_style.md` for the iOS counterpart.

---

## 13. Firebase security rules

### Firestore

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /calendarTokens/{uid} {
      allow read, write: if false; // server-side only via Cloud Functions
    }
  }
}
```

### Storage

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

---

## 14. Build and release

| Item | Value |
|------|-------|
| Version name | `VERSION_NAME` in `android/version.properties` (e.g. `0.0.2`) |
| Version code | `VERSION_CODE` in `android/version.properties` (auto-incremented on `assembleRelease`) |
| Release command | `cd android && ./gradlew assembleRelease appDistributionUploadRelease` |
| Distribution | Firebase App Distribution with tester email list in `app/build.gradle.kts` |
| Signing | Release keystore at `~/voicemind-release.jks` |
| ProGuard | Disabled |

---

## 15. Error handling

| Scenario | Behavior |
|----------|----------|
| **Network errors** | Snackbar with message. Firestore offline persistence provides cached reads. |
| **Transcription failure** | Recording saved; `transcription` remains `null`. UI shows "No transcript". |
| **Action item extraction failure** | Recording saved; no items created. No special UI. |
| **Summary generation failure** | Error shown in transcript sheet. Recording and transcript unaffected. |
| **Mic permission denied** | Explanation prompt, Settings link. No crash. |
| **Auth token expired** | `AuthStateListener` detects sign-out, navigates to SignInScreen. |
| **Calendar token expired** | Cloud Function auto-disconnects: deletes tokens, sets `calendarConnected: false`. |
| **Storage upload failure** | Error logged. Recording partially created (Firestore doc exists but no audio). |
| **Unfiled folder** | Cannot be deleted or renamed. UI hides those options for `UNFILED_ID`. |

---

## 16. Package structure

```
com.voicemind/
  VoiceMindApp.kt                  @HiltAndroidApp
  MainActivity.kt                  @AndroidEntryPoint, auth gate
  audio/
    AudioRecorder.kt               MediaRecorder wrapper
  data/
    model/
      Recording.kt                 Firestore data class
      Folder.kt                    Firestore data class + defaults
      ActionItem.kt                Firestore data class
    repository/
      AuthRepository.kt            Firebase Auth
      RecordingRepository.kt       Firestore + Functions
      FolderRepository.kt          Firestore
      ActionItemRepository.kt      Firestore
      StorageRepository.kt         Cloud Storage
      GoogleCalendarRepository.kt  Identity API + Functions
      NavPreferenceRepository.kt   DataStore Preferences
  di/
    AppModule.kt                   Hilt module (Firebase providers)
  ui/
    auth/
      SignInScreen.kt              Auth screen
      AuthViewModel.kt             Auth state + actions
    home/
      HomeScreen.kt                Folders + recent recordings
      HomeViewModel.kt             Home data
    recording/
      RecordingsScreen.kt          Recordings list + playback
      RecordingsViewModel.kt       List, filter, play, actions
      RecordingBottomSheet.kt      Recording controls
      RecordingViewModel.kt        Record lifecycle
      RecordingDialogs.kt          Transcript sheet, rename, move, delete dialogs
    checklist/
      ChecklistScreen.kt           To-do / Done sections
      ChecklistViewModel.kt        Action items state
      TaskDetailScreen.kt          Single task editing
      TaskDetailViewModel.kt       Task detail state
    folders/
      FoldersScreen.kt             Folder management
      FolderDetailScreen.kt        Folder + filtered recordings
      FoldersViewModel.kt          Folders state
    settings/
      SettingsScreen.kt            Account, nav mode, calendar
      SettingsViewModel.kt         Settings state
    navigation/
      Routes.kt                    Route definitions
      AppNavHost.kt                NavHost + drawer/bottom bar
      SidebarDrawer.kt             Drawer content
      BottomNavBar.kt              Bottom bar content
    components/
      VoiceMindTopAppBar.kt        Shared top bar
      RecordFab.kt                 Record button
      GlassCard.kt                 Card component
      EmptyStateCard.kt            Empty state
      PrimaryButton.kt             Button component
      VoiceMindTextFieldColors.kt  TextField theming
      RecordingDialogsHost.kt      Dialog orchestrator
    theme/
      Color.kt                     Palette
      Type.kt                      Typography (Inter)
      Theme.kt                     VoiceMindAITheme

functions/
  src/
    index.ts                       All Cloud Functions
```

---

## 17. Dependencies

| Category | Libraries |
|----------|-----------|
| **Core Android** | Core KTX, Lifecycle Runtime Compose, Lifecycle ViewModel Compose, Activity Compose |
| **Compose** | BOM 2024.12.01, UI, Material 3, Material Icons Extended, Navigation Compose |
| **Hilt** | Hilt Android, Hilt Compiler (KSP), Hilt Navigation Compose |
| **Firebase** | BOM 33.7.0, Auth, Firestore, Storage, Functions |
| **Google Auth** | Credential Manager, Credentials Play Services Auth, Google ID, Play Services Auth |
| **Networking** | Retrofit, Retrofit Gson Converter, OkHttp, OkHttp Logging Interceptor (currently unused in app, available) |
| **Media** | Media3 ExoPlayer, Media3 UI (available; playback currently uses MediaPlayer) |
| **Data** | DataStore Preferences 1.1.1 |
| **Logging** | Timber |
| **Cloud Functions** | firebase-functions (Node.js), googleapis, firebase-admin |

---

*This document reflects the app as currently implemented. Update it as features ship or architecture changes.*
