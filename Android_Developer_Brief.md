# VoiceMind AI — Android Architecture Reference

**Purpose:** High-level map of the Android codebase — layers, data flow, sync, services, navigation, and key infrastructure. Use this to orient before touching any part of the app.

**Related docs:** `DeveloperGuide.md` (engineering practices), `Style_Guide.md` (design system), `Patterns_Guide.md` (UI recipes and interaction conventions).

> The code is the source of truth for field-level details. This document captures the *shape* — architecture decisions, data flow directions, and where things live.

---

## 1. Architecture overview

```
┌──────────────────────────────────────────────────────────────┐
│  Android Client (Kotlin / Jetpack Compose)                   │
│                                                              │
│  MainActivity ─── AuthViewModel (auth gate)                  │
│       │            DeviceSetupScreen (first-run)             │
│       │                                                      │
│  AppNavHost ─── Screens ─── ViewModels                       │
│       │              │                                       │
│  Services        Repositories ──┐                            │
│  (Recording,         │          │                            │
│   Playback,      Room DAOs      │ Firebase SDKs              │
│   FCM)               │          │                            │
│                 ┌────┴──────────┤                            │
│                 │  AppDatabase  │ Firestore (live listeners) │
│                 │  (Room v2)    │ Cloud Storage              │
│                 └───────────────┤ Cloud Functions             │
│                                 │ Firebase Auth               │
│  Sync layer                     │                            │
│  ├─ FirestoreSyncService        │                            │
│  ├─ SyncWorker                  │                            │
│  ├─ InitialSyncManager          │                            │
│  ├─ BulkDownloadWorker          │                            │
│  └─ SyncScheduler               │                            │
│                                 │                            │
│  Hilt (DI) ─── AppModule        │                            │
│  Widget ─── Glance RecordingWidget                           │
└───────────────────┬──────────────────────────────────────────┘
                    │
        Firebase SDK (Auth, Firestore,
         Storage, Functions, Messaging)
                    │
┌───────────────────▼──────────────────────────────────────────┐
│  Firebase Cloud Functions (TypeScript)                       │
│                                                              │
│  processRecording        ─ transcribe + title + tasks        │
│  generateSummary         ─ on-demand summary                 │
│  generateCollectiveSummary ─ multi-recording summary         │
│  exchangeCalendarAuthCode ─ OAuth token exchange             │
│  disconnectCalendar      ─ revoke + cleanup                  │
│  syncActionItemToCalendar ─ Firestore trigger                │
│                                                              │
│  Secrets: OPENAI_API_KEY, GOOGLE_CLIENT_SECRET               │
└──────────────────────────────────────────────────────────────┘
```

| Layer | Tech |
|-------|------|
| Language | Kotlin |
| UI framework | Jetpack Compose (BOM 2025.12.00), Material 3 |
| Architecture | MVVM (ViewModel + StateFlow + Compose) |
| DI | Hilt (including hilt-work for workers) |
| Local database | Room (v2, 5 entities) |
| Backend | Firebase (Auth, Firestore, Storage, Functions, Messaging) |
| AI | OpenAI via Cloud Functions (gpt-4o-mini-transcribe, gpt-4o-mini) |
| Audio | MediaRecorder (M4A/AAC, 44.1 kHz, 128 kbps) |
| Playback | MediaPlayer (local-first, fallback to download URL) |
| Sync | WorkManager + Firestore snapshot listeners |
| Local preferences | Jetpack DataStore |
| Logging | Timber |
| Markdown | compose-richtext |
| Widget | Glance 1.1.1 |
| Min SDK | 28 (Android 9) |
| Target / Compile SDK | 35 |
| Build system | Gradle (Kotlin DSL) + KSP |

---

## 2. Data flow: local-first with Firestore sync

The app follows a **Room-first** pattern. UI always reads from Room. Writes go to Room immediately, then sync to Firestore in the background.

```
┌─────────┐   collectAsStateWithLifecycle   ┌────────────┐
│  Screen  │ ◄──────────────────────────────── │  ViewModel │
└─────────┘                                  └──────┬─────┘
                                                    │
                                             ┌──────▼─────┐
                                             │ Repository  │
                                             └──┬──────┬──┘
                                                │      │
                                     ┌──────────▼┐  ┌──▼──────────┐
                                     │  Room DAO  │  │  Firestore  │
                                     │ (reads +   │  │ (push sync) │
                                     │  writes)   │  └─────────────┘
                                     └────────────┘
```

### Read path
Repositories expose `Flow<List<T>>` from Room DAOs. ViewModels collect these. Firestore snapshot listeners in `FirestoreSyncService` merge cloud changes *into* Room, so the UI updates reactively without ever reading Firestore directly.

### Write path
User actions write to Room with a `SyncStatus` (`PENDING_UPLOAD`, `PENDING_UPDATE`, `PENDING_DELETE`). `SyncScheduler` enqueues a `SyncWorker` which pushes pending changes to Firestore and resolves the status to `SYNCED`.

### SyncStatus enum
```
SYNCED          – In sync with Firestore
PENDING_UPLOAD  – New, not yet pushed (e.g., offline recording)
PENDING_UPDATE  – Modified locally, push needed
PENDING_DELETE  – Marked for deletion, pending server confirmation
```

### Conflict resolution
`FirestoreSyncService` listeners apply cloud changes to Room. Cloud-owned fields (transcription, summary, processingFailed) always overwrite Room. User-owned fields (title, folderId, notes) only overwrite when `syncStatus == SYNCED` — local pending changes win.

### Soft deletes
Deletes write to a `pending_deletes` outbox table (`PendingDeleteEntity`) and are pushed by `SyncWorker` as `isDeleted: true` + `deletedAt` server timestamp on Firestore. Cloud listener hard-deletes from Room when it sees `isDeleted` from the server.

---

## 3. Sync infrastructure

| Component | Type | Role |
|-----------|------|------|
| `FirestoreSyncService` | Singleton, Firestore listeners | Pulls cloud changes into Room in real-time. Four listeners: recordings, actionItems, folders, collectiveSummaries. |
| `SyncWorker` | HiltWorker / CoroutineWorker | Pushes local pending changes (recordings, action items, folders, deletes) to Firestore. Retries on failure. |
| `InitialSyncManager` | One-shot | First-launch hydration: fetches all non-deleted docs from Firestore into Room. Runs once per account. |
| `SyncScheduler` | Singleton | Enqueues `SyncWorker` as unique work with network constraint and exponential backoff. |
| `BulkDownloadWorker` | HiltWorker, foreground | Downloads missing local audio files after device setup. Progress notification. |
| `ConnectivityObserver` | Singleton, StateFlow | Tracks network state via `ConnectivityManager`. `MainActivity` uses it to trigger sync on reconnect. |

---

## 4. Services

| Service | Type | Purpose |
|---------|------|---------|
| `RecordingService` | Foreground (`MICROPHONE`) | Manages `MediaRecorder` lifecycle, `MediaSession` for hardware buttons, saves to Room with `PENDING_UPLOAD` when offline, uploads + calls `processRecording` when online, updates Glance widget state. |
| `PlaybackService` | Foreground (`MEDIA_PLAYBACK`) | Notification with skip/play-pause controls via `PlaybackActionReceiver`. No `MediaSession`. |
| `VoiceMindMessagingService` | FCM | Receives push notifications (sharing, etc.). |

---

## 5. Local audio

`LocalAudioManager` manages files under `filesDir/`:
- **Own audio:** `audio/{recordingId}.m4a` — saved after recording, used for local-first playback.
- **Shared audio:** `shared_audio/{shareId}.m4a` — downloaded for shared recordings.
- Provides size stats for storage management UI.

Playback prefers the local file when available, falling back to Firestore Storage download URL.

---

## 6. Navigation

### Routes
Primary tabs: **Recordings**, **Checklist**, **Summaries**, **Folders**. Settings is a detail route.

| Route | Screen | Parameters |
|-------|--------|------------|
| `recordings` | RecordingsScreen | — |
| `checklist` | ChecklistScreen | — |
| `summaries` | SummariesScreen | — |
| `folders` | FoldersScreen | — |
| `settings` | SettingsScreen | — |
| `folder_detail/{folderId}` | FolderDetailScreen | folderId |
| `task_detail/{itemId}` | TaskDetailScreen | itemId |
| `recording_detail/{recordingId}` | RecordingDetailScreen | recordingId |
| `shared_items` | SharedItemsScreen | — |
| `shared_by_me` | SharedByMeScreen | — |
| `shared_recording/{ownerUid}/{recordingId}` | SharedRecordingDetailScreen | ownerUid, recordingId |
| `shared_summary/{ownerUid}/{summaryId}` | SharedSummaryDetailScreen | ownerUid, summaryId |

### Navigation modes
User-selectable via Settings, persisted in DataStore:
- **Bottom bar** (default): `HorizontalPager` for swipe between tabs + `BottomNavBar`.
- **Sidebar drawer**: `ModalNavigationDrawer` with `SidebarDrawer`.

Tab order and default landing page are user-configurable.

---

## 7. Startup flow (MainActivity)

1. Auth gate: signed out → `SignInScreen`; signed in → continue.
2. Device setup: if `!isDeviceSetupComplete` and Room is empty → `DeviceSetupScreen` (local storage consent + initial sync).
3. Post-setup: register FCM token, run `InitialSyncManager`, start `FirestoreSyncService` listeners.
4. Connectivity: `LaunchedEffect` tracks online/offline; triggers `SyncScheduler` on reconnect.
5. Cleanup: `DisposableEffect` stops Firestore listeners on dispose.
6. Permissions: mic + notifications (API 33+), Google Tasks prompt, local storage consent dialog.
7. Deep links: notification extras for opening recordings or shared items.

---

## 8. Room database

**Database:** `AppDatabase` (v2, `voicemind.db`)

**Entities:**

| Entity | Table | Key local-only fields |
|--------|-------|----------------------|
| `RecordingEntity` | `recordings` | `localAudioPath`, `syncStatus` |
| `ActionItemEntity` | `action_items` | `syncStatus` |
| `FolderEntity` | `folders` | `syncStatus` |
| `CollectiveSummaryEntity` | `collective_summaries` | `syncStatus` |
| `PendingDeleteEntity` | `pending_deletes` | `entityType`, `entityId` |

Each entity maps to/from its Firestore model via `EntityMappers.kt`. The `syncStatus` field exists only on Room entities — it is `@get:Exclude`d from Firestore serialization on domain models.

**Type converters:** `SyncStatus` ↔ String, `List<String>` ↔ JSON (Gson).

---

## 9. Repositories

| Repository | Data source | Key responsibility |
|------------|-------------|-------------------|
| `AuthRepository` | Firebase Auth | Auth state flow, sign-in/up, Google credential, FCM token |
| `RecordingRepository` | Room + Firestore + Storage + Functions | CRUD recordings, Room-first reads, dual-write, upload, process |
| `FolderRepository` | Room + Firestore | CRUD folders, seed defaults, Room-first |
| `ActionItemRepository` | Room + Firestore + Functions | CRUD action items, observe by recording, shared tasks |
| `CollectiveSummaryRepository` | Room + Firestore + Functions | Multi-recording summaries, `generateCollectiveSummary` callable |
| `StorageRepository` | Cloud Storage | Upload/download audio, get download URLs |
| `SharingRepository` | Firestore | Share items with users, lookup, revoke, observe shared items |
| `GoogleCalendarRepository` | Identity API + Functions | Calendar connect/disconnect, observe status |
| `GoogleTasksRepository` | Functions | Google Tasks connect/disconnect/status |
| `NavPreferenceRepository` | DataStore | All user preferences (landing page, tab order, timezone, sync flags, consent flags, etc.) |
| `UserSettingsRepository` | Firestore | User profile settings (discoverable, NTS, calendar/tasks connected) |
| `RecordingStateRepository` | In-memory | Lightweight ephemeral state for recording UI |

---

## 10. ViewModels

| ViewModel | Screen | Key concerns |
|-----------|--------|-------------|
| `AuthViewModel` | SignInScreen | Auth state, sign-in/up, Google credential |
| `MainViewModel` | App-level | Google Tasks prompt, pending consent |
| `DeviceSetupViewModel` | DeviceSetupScreen | Consent, initial sync trigger, bulk download |
| `RecordingViewModel` | RecordingBottomSheet | Record lifecycle (start/pause/resume/stop/discard) |
| `RecordingsViewModel` | RecordingsScreen | List, playback, multi-select, bulk ops, sharing, collective summarize |
| `RecordingDetailViewModel` | RecordingDetailScreen | Waveform, playback position/speed |
| `ChecklistViewModel` | ChecklistScreen | To-do/done split, selection mode, bulk delete, add task |
| `TaskDetailViewModel` | TaskDetailScreen | Single task editing, dates, delete |
| `FoldersViewModel` | FoldersScreen + FolderDetailScreen | Folder CRUD, counts, sort |
| `SummariesViewModel` | SummariesScreen | Summary list, detail selection, delete |
| `SettingsViewModel` | SettingsScreen | All settings, calendar/tasks connect, storage management, account deletion |
| `ShareViewModel` | ShareDialog | User lookup, share, revoke |
| `SharedItemsViewModel` | SharedItemsScreen | Shared-with-me items |
| `SharedByMeViewModel` | SharedByMeScreen | Items shared by current user |
| `SharedRecordingDetailViewModel` | SharedRecordingDetailScreen | Shared recording playback/detail |
| `SharedSummaryDetailViewModel` | SharedSummaryDetailScreen | Shared summary display |

---

## 11. Hilt modules

`AppModule` (`@InstallIn(SingletonComponent)`) provides as `@Singleton`:
- Firebase instances: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `FirebaseFunctions`
- Room: `AppDatabase` (with `MIGRATION_1_2` + destructive fallback) + all five DAOs

All repositories use `@Singleton @Inject constructor`. All ViewModels use `@HiltViewModel @Inject constructor`. Workers use `@HiltWorker`.

---

## 12. Firebase integration

### Auth
Email/password + Google Sign-In (Credential Manager). Auth state drives the app gate in `MainActivity`.

### Firestore
All user data under `users/{uid}/`. Collections: `recordings`, `folders`, `actionItems`, `collectiveSummaries`. Shared items use cross-user document references. Offline persistence enabled (default).

### Cloud Storage
Audio at `users/{uid}/audio/{recordingId}.m4a`. Upload via `StorageRepository`, download URL for remote playback fallback.

### Cloud Functions
| Function | Type | Purpose |
|----------|------|---------|
| `processRecording` | onCall, 120s | Transcribe + title + extract tasks |
| `generateSummary` | onCall, 60s | On-demand single-recording summary |
| `generateCollectiveSummary` | onCall | Multi-recording summary |
| `exchangeCalendarAuthCode` | onCall | Google Calendar OAuth token exchange |
| `disconnectCalendar` | onCall | Revoke calendar tokens |
| `syncActionItemToCalendar` | onDocumentWritten | Auto-sync tasks to Google Calendar |

### FCM
`VoiceMindMessagingService` handles push notifications. Token registered on sign-in.

---

## 13. Glance widget

`RecordingWidget` — start/stop recording from home screen. State communicated via DataStore preferences (`RecordingWidgetStateKeys`). Colors from `WidgetColors` (brand-seeded M3 values, light-mode only — Glance limitation).

---

## 14. Package structure

```
com.voicemind/
  VoiceMindApp.kt                  @HiltAndroidApp
  MainActivity.kt                  @AndroidEntryPoint, auth gate + setup
  audio/
    AudioRecorder.kt               MediaRecorder wrapper
  data/
    model/                         Firestore-serializable domain models
      Recording.kt, Folder.kt, ActionItem.kt,
      CollectiveSummary.kt, SharedItem.kt, MyShare.kt
    local/                         Room database layer
      AppDatabase.kt               Room database (v2)
      AppTypeConverters.kt         SyncStatus + JSON converters
      EntityMappers.kt             Entity ↔ domain model mapping
      LocalAudioManager.kt         Local file storage for audio
      SyncStatus.kt                SYNCED | PENDING_UPLOAD | PENDING_UPDATE | PENDING_DELETE
      dao/                         Room DAOs
        RecordingDao.kt, FolderDao.kt, ActionItemDao.kt,
        CollectiveSummaryDao.kt, PendingDeleteDao.kt
      entity/                      Room entities
        RecordingEntity.kt, FolderEntity.kt, ActionItemEntity.kt,
        CollectiveSummaryEntity.kt, PendingDeleteEntity.kt
    repository/                    Data access layer
      AuthRepository.kt, RecordingRepository.kt, FolderRepository.kt,
      ActionItemRepository.kt, CollectiveSummaryRepository.kt,
      StorageRepository.kt, SharingRepository.kt,
      GoogleCalendarRepository.kt, GoogleTasksRepository.kt,
      NavPreferenceRepository.kt, UserSettingsRepository.kt,
      RecordingStateRepository.kt
    sync/                          Background sync infrastructure
      FirestoreSyncService.kt      Live Firestore → Room merge
      SyncWorker.kt                Push pending changes to Firestore
      InitialSyncManager.kt        First-launch Room hydration
      SyncScheduler.kt             WorkManager enqueue helper
      BulkDownloadWorker.kt        Bulk audio download after setup
  di/
    AppModule.kt                   Hilt module (Firebase + Room providers)
  service/
    RecordingService.kt            Foreground recording service
    PlaybackService.kt             Foreground playback service
    PlaybackActionReceiver.kt      Notification action handler
    PlaybackCommandRepository.kt   Playback command bus
    VoiceMindMessagingService.kt   FCM handler
  ui/
    auth/                          SignInScreen, AuthViewModel
    main/                          MainViewModel (app-level concerns)
    setup/                         DeviceSetupScreen, DeviceSetupViewModel
    recording/                     RecordingsScreen, RecordingDetailScreen,
                                   RecordingBottomSheet, RecordingDialogs,
                                   RecordingsViewModel, RecordingViewModel,
                                   RecordingDetailViewModel, WaveformExtractor
    checklist/                     ChecklistScreen, TaskDetailScreen,
                                   ChecklistViewModel, TaskDetailViewModel
    folders/                       FoldersScreen, FolderDetailScreen, FoldersViewModel
    summaries/                     SummariesScreen, SummariesViewModel
    settings/                      SettingsScreen, SettingsViewModel, TimeZonePickerDialog
    sharing/                       ShareDialog, SharedItemsScreen, SharedByMeScreen,
                                   SharedRecordingDetailScreen, SharedSummaryDetailScreen,
                                   + corresponding ViewModels
    navigation/                    Routes, AppNavHost, BottomNavBar, SidebarDrawer
    common/                        OfflineBanner
    components/                    Shared composables (see below)
    theme/                         Color, Type, Dimens, Theme
  util/
    ConnectivityObserver.kt        Network state tracking
    AppTimeZone.kt                 Timezone provider
    DateFormatting.kt              Date display helpers
    RecordingExtensions.kt         Recording model extensions
    TimeFormat.kt                  Time formatting
  widget/
    RecordingWidget.kt             Glance widget
    RecordingWidgetStateKeys.kt    Widget DataStore keys
    WidgetColors.kt                Widget color constants
```

---

## 15. Shared UI components

| Component | Purpose |
|-----------|---------|
| `GlassCard` | Primary card surface (`surfaceContainerLow`, `tonalElevation = 1.dp`) |
| `VoiceMindTopAppBar` | App bar with icon, title, optional drawer/back/settings/info actions |
| `PrimaryButton` | Full-width M3 button |
| `EmptyStateCard` | Empty state with icon + message |
| `RecordFab` | Record FAB with mic permission handling |
| `InlinePlayerControls` | Slider + transport controls for inline playback |
| `AudioWaveform` | Canvas waveform with drag-to-seek |
| `SpeedBubble` | Playback speed picker |
| `RecordingDialogsHost` | Orchestrates transcript/rename/move/delete dialogs for recordings |
| `TasksSyncPromptDialog` | Google Tasks connect explainer |
| `PermissionRationaleDialog` | Mic/notification permission rationale |
| `VoiceMindTextFieldColors` | Consistent `OutlinedTextField` color scheme |

---

*This document reflects the app as currently implemented. Update it when architecture changes — but leave field-level details to the code.*
