---
name: Phase 1 Room Foundation
overview: Set up all local-first infrastructure — Room database with entities/DAOs, LocalAudioManager, ConnectivityObserver, and Hilt DI wiring — without changing any existing app behavior.
todos:
  - id: deps
    content: Add Room + WorkManager versions/libraries to libs.versions.toml and dependencies to build.gradle.kts
    status: completed
  - id: sync-status
    content: Create SyncStatus enum at data/local/SyncStatus.kt
    status: completed
  - id: entities
    content: "Create 4 Room entity files: RecordingEntity, ActionItemEntity, FolderEntity, CollectiveSummaryEntity"
    status: completed
  - id: daos
    content: "Create 4 Room DAO interfaces: RecordingDao, ActionItemDao, FolderDao, CollectiveSummaryDao"
    status: pending
  - id: converters
    content: Create Converters.kt with SyncStatus and List<String> type converters
    status: pending
  - id: database
    content: Create AppDatabase.kt Room database class
    status: completed
  - id: mappers
    content: Create EntityMappers.kt with bidirectional entity <-> model conversion functions
    status: completed
  - id: audio-mgr
    content: Create LocalAudioManager.kt for managing local audio files
    status: pending
  - id: connectivity
    content: Create ConnectivityObserver.kt wrapping ConnectivityManager with StateFlow<Boolean>
    status: pending
  - id: di-wiring
    content: Update AppModule.kt to provide AppDatabase and all 4 DAOs
    status: completed
  - id: verify-build
    content: Build the project and verify zero compile errors, existing behavior unchanged
    status: completed
isProject: false
---

# Phase 1: Room Database Foundation, Local Audio Manager, Connectivity Observer

## Scope

This phase is purely additive. No existing behavior changes. The app must compile and work exactly as before after all changes. We are laying the foundation for Phases 2-6.

## 1. Dependencies

**[android/gradle/libs.versions.toml](android/gradle/libs.versions.toml)** — add version + library entries:

```toml
# Under [versions]
room = "2.6.1"
workRuntime = "2.9.1"

# Under [libraries]
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
```

**[android/app/build.gradle.kts](android/app/build.gradle.kts)** — add to `dependencies {}`:

```kotlin
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
implementation(libs.work.runtime.ktx)
```

KSP is already configured (plugin applied, used for Hilt). Gson is available transitively via `converter-gson`.

## 2. New Files — `data/local/` Package

All new files live under `android/app/src/main/java/com/voicemind/data/local/`.

### SyncStatus Enum — `data/local/SyncStatus.kt`

```kotlin
enum class SyncStatus { SYNCED, PENDING_UPLOAD, PENDING_UPDATE, PENDING_DELETE }
```

### Room Entities — `data/local/entity/`

4 entity files, each mirroring its Firestore model with added `syncStatus` and (for recordings) `localAudioPath`:

- `**RecordingEntity.kt**` — mirrors [Recording.kt](android/app/src/main/java/com/voicemind/data/model/Recording.kt) + `localAudioPath: String?`, `syncStatus: SyncStatus`. Uses `Long?` for timestamps (epoch millis).
- `**ActionItemEntity.kt**` — mirrors [ActionItem.kt](android/app/src/main/java/com/voicemind/data/model/ActionItem.kt) + `syncStatus`.
- `**FolderEntity.kt**` — mirrors [Folder.kt](android/app/src/main/java/com/voicemind/data/model/Folder.kt) + `syncStatus`.
- `**CollectiveSummaryEntity.kt**` — mirrors [CollectiveSummary.kt](android/app/src/main/java/com/voicemind/data/model/CollectiveSummary.kt) + `syncStatus`. `recordingIds`/`recordingTitles` stored as JSON strings with TypeConverter.

### Room DAOs — `data/local/dao/`

4 DAO interfaces with full CRUD + sync support:

- `**RecordingDao.kt**` — `observeAll()`, `observeByFolder()`, `getById()`, `observeById()`, `getPendingSync()`, `upsert()`, `upsertAll()`, `hardDelete()`, `updateSyncStatus()`, `updateTitle()`, `updateFolder()`, `updateTranscription()`, `updateSummary()`, `updateProcessingFailed()`, `deleteAll()`
- `**ActionItemDao.kt**` — `observeAll()`, `observeByRecordingId()`, `observeById()`, `getById()`, `getPendingSync()`, `observeSharedTasks()`, `upsert()`, `upsertAll()`, `hardDelete()`, `deleteAll()`
- `**FolderDao.kt**` — `observeAll()`, `getById()`, `getPendingSync()`, `upsert()`, `upsertAll()`, `hardDelete()`, `deleteAll()`
- `**CollectiveSummaryDao.kt**` — `observeAll()`, `getById()`, `getPendingSync()`, `upsert()`, `upsertAll()`, `hardDelete()`, `deleteAll()`

### Type Converters — `data/local/Converters.kt`

- `SyncStatus` <-> `String` (name-based)
- `List<String>` <-> `String` (JSON via Gson, available transitively from `converter-gson`)

### Room Database — `data/local/AppDatabase.kt`

- Version 1, `exportSchema = false`, `fallbackToDestructiveMigration()`
- Entities: `RecordingEntity`, `ActionItemEntity`, `FolderEntity`, `CollectiveSummaryEntity`
- TypeConverters: `Converters`
- Abstract DAO accessors for all 4 DAOs

### Entity Mappers — `data/local/EntityMappers.kt`

Extension functions converting between Room entities and Firestore models:

- `RecordingEntity.toModel(): Recording` and `Recording.toEntity(localAudioPath, syncStatus): RecordingEntity`
- Same for `ActionItem`, `Folder`, `CollectiveSummary`
- Timestamp conversion: `Timestamp` -> `Long` (epoch millis) and `Long` -> `Timestamp`

### Local Audio Manager — `data/local/LocalAudioManager.kt`

- `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`
- Manages `filesDir/audio/` (own recordings) and `filesDir/shared_audio/` (shared recordings)
- Methods: `getAudioFile()`, `getSharedAudioFile()`, `saveAudio()`, `audioExists()`, `sharedAudioExists()`, `deleteAudio()`, `deleteSharedAudio()`, `getOwnAudioSizeBytes()`, `getSharedAudioSizeBytes()`, `getTotalSizeBytes()`, `clearSharedAudio()`, `clearAllAudio()`

## 3. New File — `util/ConnectivityObserver.kt`

- `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`
- Wraps `ConnectivityManager.NetworkCallback`
- Exposes `isOnline: StateFlow<Boolean>` via `callbackFlow` + `stateIn(Eagerly)`
- Initial state emitted from `activeNetwork` capabilities check

Goes in the existing [util/](android/app/src/main/java/com/voicemind/util/) package alongside `DateFormatting.kt`, etc.

## 4. DI Wiring — Update `AppModule.kt`

**[AppModule.kt](android/app/src/main/java/com/voicemind/di/AppModule.kt)** — add providers:

```kotlin
@Provides @Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = ...

@Provides fun provideRecordingDao(db: AppDatabase): RecordingDao = db.recordingDao()
@Provides fun provideActionItemDao(db: AppDatabase): ActionItemDao = db.actionItemDao()
@Provides fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()
@Provides fun provideCollectiveSummaryDao(db: AppDatabase): CollectiveSummaryDao = db.collectiveSummaryDao()
```

`LocalAudioManager` and `ConnectivityObserver` are constructor-injected `@Singleton` classes — no explicit provider needed.

## Files Summary

**New files (14):**

- `data/local/SyncStatus.kt`
- `data/local/entity/RecordingEntity.kt`
- `data/local/entity/ActionItemEntity.kt`
- `data/local/entity/FolderEntity.kt`
- `data/local/entity/CollectiveSummaryEntity.kt`
- `data/local/dao/RecordingDao.kt`
- `data/local/dao/ActionItemDao.kt`
- `data/local/dao/FolderDao.kt`
- `data/local/dao/CollectiveSummaryDao.kt`
- `data/local/Converters.kt`
- `data/local/AppDatabase.kt`
- `data/local/EntityMappers.kt`
- `data/local/LocalAudioManager.kt`
- `util/ConnectivityObserver.kt`

**Modified files (3):**

- `android/gradle/libs.versions.toml` — add Room + WorkManager versions/libraries
- `android/app/build.gradle.kts` — add Room + WorkManager dependencies
- `android/app/src/main/java/com/voicemind/di/AppModule.kt` — add DB + DAO providers

## Verification

After implementation, the project must:

- Compile with zero errors
- Run with identical behavior to before (all reads still from Firestore)
- Room database instantiable at runtime (verified by Hilt providing it)
- No runtime permission dialogs (using `context.filesDir`)

