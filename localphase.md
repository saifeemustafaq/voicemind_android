# Local-First Architecture — Implementation Phases

Each phase is self-contained: once complete, it does not need to be revisited. Phases are ordered by dependency. A developer should complete every checklist item in a phase before moving on.

**Why this migration?** The app currently streams every audio file from Firebase Storage on each play, and reads all metadata (transcripts, summaries, tasks, folders) from Firestore snapshot listeners. This means every interaction requires network, wastes mobile data, and introduces latency. The goal is to make local storage the primary read source and cloud the backup/recovery layer.

**Core principle:** Write locally first, sync to cloud in background. Read from local always. Cloud is for processing (transcription, summaries, task extraction) and cross-device recovery.

---

## Phase 1: Room Database Foundation, Local Audio Manager, Connectivity Observer

**Goal:** Set up all local infrastructure — Room database with entities and DAOs, a local audio file manager, and a network connectivity observer. No existing behavior changes. Everything compiles and the app works exactly as before.

### Dependencies

- [x] Update `android/gradle/libs.versions.toml` — add version entries:
  ```toml
  room = "2.6.1"
  workRuntime = "2.9.1"
  ```

- [x] Update `android/gradle/libs.versions.toml` — add library entries:
  ```toml
  room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
  room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
  room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
  work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
  ```

- [x] Update `android/app/build.gradle.kts` — add dependencies:
  ```kotlin
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)
  implementation(libs.work.runtime.ktx)
  ```

### SyncStatus Enum

- [x] Create `android/app/src/main/java/com/voicemind/data/local/SyncStatus.kt`
  ```kotlin
  package com.voicemind.data.local

  enum class SyncStatus {
      SYNCED,
      PENDING_UPLOAD,
      PENDING_UPDATE,
      PENDING_DELETE,
  }
  ```

### Room Entities

All entities live under `android/app/src/main/java/com/voicemind/data/local/entity/`.

- [x] Create `RecordingEntity.kt`
  ```kotlin
  @Entity(tableName = "recordings")
  data class RecordingEntity(
      @PrimaryKey val id: String,
      val title: String,
      val folderId: String,
      val createdAt: Long?,
      val transcription: String?,
      val summary: String?,
      val audioPath: String,
      val localAudioPath: String?,
      val durationSeconds: Long,
      val isDeleted: Boolean,
      val deletedAt: Long?,
      val processingFailed: Boolean,
      val syncStatus: SyncStatus,
  )
  ```
  - `createdAt` and `deletedAt` are `Long?` (epoch millis). The existing Firestore model uses `Timestamp`; conversion happens in the repository/mapper layer.
  - `audioPath` is the cloud path (`users/{uid}/audio/{id}.m4a`), retained for sync.
  - `localAudioPath` is the absolute path on disk (`/data/.../files/audio/{id}.m4a`), used for playback.
  - `syncStatus` tracks whether this record needs to be pushed to cloud.

- [x] Create `ActionItemEntity.kt`
  ```kotlin
  @Entity(tableName = "action_items")
  data class ActionItemEntity(
      @PrimaryKey val id: String,
      val title: String,
      val completed: Boolean,
      val recordingId: String?,
      val createdAt: Long?,
      val dueDate: Long?,
      val deadline: Long?,
      val notes: String?,
      val googleTaskId: String?,
      val calendarEventId: String?,
      val autoScheduled: Boolean,
      val sharedFromUid: String?,
      val sharedFromName: String?,
      val isDeleted: Boolean,
      val deletedAt: Long?,
      val syncStatus: SyncStatus,
  )
  ```

- [x] Create `FolderEntity.kt`
  ```kotlin
  @Entity(tableName = "folders")
  data class FolderEntity(
      @PrimaryKey val id: String,
      val name: String,
      val createdAt: Long?,
      val isDeleted: Boolean,
      val deletedAt: Long?,
      val syncStatus: SyncStatus,
  )
  ```

- [x] Create `CollectiveSummaryEntity.kt`
  ```kotlin
  @Entity(tableName = "collective_summaries")
  data class CollectiveSummaryEntity(
      @PrimaryKey val id: String,
      val summary: String,
      val recordingIds: String,
      val recordingTitles: String,
      val createdAt: Long?,
      val isDeleted: Boolean,
      val deletedAt: Long?,
      val syncStatus: SyncStatus,
  )
  ```
  - `recordingIds` and `recordingTitles` are stored as comma-separated strings (or JSON arrays). A `TypeConverter` handles `List<String>` ↔ `String` conversion.

### Room DAOs

All DAOs live under `android/app/src/main/java/com/voicemind/data/local/dao/`.

- [x] Create `RecordingDao.kt`
  ```kotlin
  @Dao
  interface RecordingDao {
      @Query("SELECT * FROM recordings WHERE isDeleted = 0 ORDER BY createdAt DESC")
      fun observeAll(): Flow<List<RecordingEntity>>

      @Query("SELECT * FROM recordings WHERE isDeleted = 0 AND folderId = :folderId ORDER BY createdAt DESC")
      fun observeByFolder(folderId: String): Flow<List<RecordingEntity>>

      @Query("SELECT * FROM recordings WHERE id = :id")
      suspend fun getById(id: String): RecordingEntity?

      @Query("SELECT * FROM recordings WHERE id = :id")
      fun observeById(id: String): Flow<RecordingEntity?>

      @Query("SELECT * FROM recordings WHERE syncStatus != 'SYNCED'")
      suspend fun getPendingSync(): List<RecordingEntity>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(entity: RecordingEntity)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsertAll(entities: List<RecordingEntity>)

      @Query("DELETE FROM recordings WHERE id = :id")
      suspend fun hardDelete(id: String)

      @Query("UPDATE recordings SET syncStatus = :status WHERE id = :id")
      suspend fun updateSyncStatus(id: String, status: SyncStatus)

      @Query("UPDATE recordings SET title = :title, syncStatus = :syncStatus WHERE id = :id")
      suspend fun updateTitle(id: String, title: String, syncStatus: SyncStatus)

      @Query("UPDATE recordings SET folderId = :folderId, syncStatus = :syncStatus WHERE id = :id")
      suspend fun updateFolder(id: String, folderId: String, syncStatus: SyncStatus)

      @Query("UPDATE recordings SET transcription = :transcription WHERE id = :id")
      suspend fun updateTranscription(id: String, transcription: String?)

      @Query("UPDATE recordings SET summary = :summary WHERE id = :id")
      suspend fun updateSummary(id: String, summary: String?)

      @Query("UPDATE recordings SET processingFailed = :failed WHERE id = :id")
      suspend fun updateProcessingFailed(id: String, failed: Boolean)

      @Query("DELETE FROM recordings")
      suspend fun deleteAll()
  }
  ```

- [x] Create `ActionItemDao.kt`
  ```kotlin
  @Dao
  interface ActionItemDao {
      @Query("SELECT * FROM action_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
      fun observeAll(): Flow<List<ActionItemEntity>>

      @Query("SELECT * FROM action_items WHERE isDeleted = 0 AND recordingId = :recordingId ORDER BY createdAt ASC")
      fun observeByRecordingId(recordingId: String): Flow<List<ActionItemEntity>>

      @Query("SELECT * FROM action_items WHERE id = :id")
      fun observeById(id: String): Flow<ActionItemEntity?>

      @Query("SELECT * FROM action_items WHERE id = :id")
      suspend fun getById(id: String): ActionItemEntity?

      @Query("SELECT * FROM action_items WHERE syncStatus != 'SYNCED'")
      suspend fun getPendingSync(): List<ActionItemEntity>

      @Query("SELECT * FROM action_items WHERE isDeleted = 0 AND sharedFromUid IS NOT NULL ORDER BY createdAt DESC")
      fun observeSharedTasks(): Flow<List<ActionItemEntity>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(entity: ActionItemEntity)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsertAll(entities: List<ActionItemEntity>)

      @Query("DELETE FROM action_items WHERE id = :id")
      suspend fun hardDelete(id: String)

      @Query("DELETE FROM action_items")
      suspend fun deleteAll()
  }
  ```

- [x] Create `FolderDao.kt`
  ```kotlin
  @Dao
  interface FolderDao {
      @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY createdAt ASC")
      fun observeAll(): Flow<List<FolderEntity>>

      @Query("SELECT * FROM folders WHERE id = :id")
      suspend fun getById(id: String): FolderEntity?

      @Query("SELECT * FROM folders WHERE syncStatus != 'SYNCED'")
      suspend fun getPendingSync(): List<FolderEntity>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(entity: FolderEntity)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsertAll(entities: List<FolderEntity>)

      @Query("DELETE FROM folders WHERE id = :id")
      suspend fun hardDelete(id: String)

      @Query("DELETE FROM folders")
      suspend fun deleteAll()
  }
  ```

- [x] Create `CollectiveSummaryDao.kt`
  ```kotlin
  @Dao
  interface CollectiveSummaryDao {
      @Query("SELECT * FROM collective_summaries WHERE isDeleted = 0 ORDER BY createdAt DESC")
      fun observeAll(): Flow<List<CollectiveSummaryEntity>>

      @Query("SELECT * FROM collective_summaries WHERE id = :id")
      suspend fun getById(id: String): CollectiveSummaryEntity?

      @Query("SELECT * FROM collective_summaries WHERE syncStatus != 'SYNCED'")
      suspend fun getPendingSync(): List<CollectiveSummaryEntity>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(entity: CollectiveSummaryEntity)

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsertAll(entities: List<CollectiveSummaryEntity>)

      @Query("DELETE FROM collective_summaries WHERE id = :id")
      suspend fun hardDelete(id: String)

      @Query("DELETE FROM collective_summaries")
      suspend fun deleteAll()
  }
  ```

### Type Converters

- [x] Create `android/app/src/main/java/com/voicemind/data/local/Converters.kt`
  ```kotlin
  package com.voicemind.data.local

  import androidx.room.TypeConverter
  import com.google.gson.Gson
  import com.google.gson.reflect.TypeToken

  class Converters {
      @TypeConverter
      fun fromSyncStatus(value: SyncStatus): String = value.name

      @TypeConverter
      fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

      @TypeConverter
      fun fromStringList(value: List<String>): String = Gson().toJson(value)

      @TypeConverter
      fun toStringList(value: String): List<String> {
          val type = object : TypeToken<List<String>>() {}.type
          return Gson().fromJson(value, type)
      }
  }
  ```

### Room Database

- [x] Create `android/app/src/main/java/com/voicemind/data/local/AppDatabase.kt`
  ```kotlin
  @Database(
      entities = [
          RecordingEntity::class,
          ActionItemEntity::class,
          FolderEntity::class,
          CollectiveSummaryEntity::class,
      ],
      version = 1,
      exportSchema = false,
  )
  @TypeConverters(Converters::class)
  abstract class AppDatabase : RoomDatabase() {
      abstract fun recordingDao(): RecordingDao
      abstract fun actionItemDao(): ActionItemDao
      abstract fun folderDao(): FolderDao
      abstract fun collectiveSummaryDao(): CollectiveSummaryDao
  }
  ```

### Entity Mappers

- [x] Create `android/app/src/main/java/com/voicemind/data/local/EntityMappers.kt`
  - Functions to convert between Room entities and existing Firestore data models:
    - `RecordingEntity.toModel(): Recording`
    - `Recording.toEntity(localAudioPath: String?, syncStatus: SyncStatus): RecordingEntity`
    - `ActionItemEntity.toModel(): ActionItem`
    - `ActionItem.toEntity(syncStatus: SyncStatus): ActionItemEntity`
    - `FolderEntity.toModel(): Folder`
    - `Folder.toEntity(syncStatus: SyncStatus): FolderEntity`
    - `CollectiveSummaryEntity.toModel(): CollectiveSummary`
    - `CollectiveSummary.toEntity(syncStatus: SyncStatus): CollectiveSummaryEntity`
  - `Timestamp` → `Long` conversion: `timestamp?.seconds?.times(1000)?.plus(timestamp.nanoseconds / 1_000_000)`
  - `Long` → `Timestamp` conversion: `Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())`

### Local Audio Manager

- [x] Create `android/app/src/main/java/com/voicemind/data/local/LocalAudioManager.kt`
  ```kotlin
  @Singleton
  class LocalAudioManager @Inject constructor(
      @ApplicationContext private val context: Context,
  ) {
      private val audioDir: File get() = File(context.filesDir, "audio").also { it.mkdirs() }
      private val sharedAudioDir: File get() = File(context.filesDir, "shared_audio").also { it.mkdirs() }

      fun getAudioFile(recordingId: String): File = File(audioDir, "$recordingId.m4a")

      fun getSharedAudioFile(shareId: String): File = File(sharedAudioDir, "$shareId.m4a")

      fun saveAudio(recordingId: String, sourceFile: File): File {
          val dest = getAudioFile(recordingId)
          sourceFile.copyTo(dest, overwrite = true)
          return dest
      }

      fun audioExists(recordingId: String): Boolean = getAudioFile(recordingId).exists()

      fun sharedAudioExists(shareId: String): Boolean = getSharedAudioFile(shareId).exists()

      fun deleteAudio(recordingId: String): Boolean = getAudioFile(recordingId).delete()

      fun deleteSharedAudio(shareId: String): Boolean = getSharedAudioFile(shareId).delete()

      fun getOwnAudioSizeBytes(): Long = audioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

      fun getSharedAudioSizeBytes(): Long = sharedAudioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

      fun getTotalSizeBytes(): Long = getOwnAudioSizeBytes() + getSharedAudioSizeBytes()

      fun clearSharedAudio() { sharedAudioDir.listFiles()?.forEach { it.delete() } }

      fun clearAllAudio() {
          audioDir.listFiles()?.forEach { it.delete() }
          sharedAudioDir.listFiles()?.forEach { it.delete() }
      }
  }
  ```
  - Uses `context.filesDir` (app-internal storage). No runtime permissions needed on API 28+.
  - `audio/` holds the user's own recordings.
  - `shared_audio/` holds downloaded shared recordings.

### Connectivity Observer

- [x] Create `android/app/src/main/java/com/voicemind/util/ConnectivityObserver.kt`
  ```kotlin
  @Singleton
  class ConnectivityObserver @Inject constructor(
      @ApplicationContext private val context: Context,
  ) {
      private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

      val isOnline: StateFlow<Boolean> = callbackFlow {
          val callback = object : ConnectivityManager.NetworkCallback() {
              override fun onAvailable(network: Network) { trySend(true) }
              override fun onLost(network: Network) { trySend(false) }
              override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                  trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
              }
          }
          val request = NetworkRequest.Builder()
              .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
              .build()
          connectivityManager.registerNetworkCallback(request, callback)

          // Emit initial state
          val active = connectivityManager.activeNetwork
          val caps = connectivityManager.getNetworkCapabilities(active)
          trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

          awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
      }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, false)
  }
  ```

### Hilt DI Module

- [x] Update `android/app/src/main/java/com/voicemind/di/AppModule.kt` — add providers:
  ```kotlin
  @Provides
  @Singleton
  fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
      Room.databaseBuilder(context, AppDatabase::class.java, "voicemind.db")
          .fallbackToDestructiveMigration()
          .build()

  @Provides fun provideRecordingDao(db: AppDatabase): RecordingDao = db.recordingDao()

  @Provides fun provideActionItemDao(db: AppDatabase): ActionItemDao = db.actionItemDao()

  @Provides fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

  @Provides fun provideCollectiveSummaryDao(db: AppDatabase): CollectiveSummaryDao = db.collectiveSummaryDao()
  ```
  - `LocalAudioManager` and `ConnectivityObserver` are `@Singleton class ... @Inject constructor(...)`, so Hilt constructs them automatically — no explicit provider needed.
  - Use `fallbackToDestructiveMigration()` during development. Before production release, replace with proper `Migration` objects.

### Verification

- [x] Project compiles with no errors after adding all new files
- [x] Room database can be instantiated (add a quick unit test or log in `Application.onCreate`)
- [x] `LocalAudioManager` can create and delete files in `filesDir/audio/`
- [x] `ConnectivityObserver.isOnline` emits correct state when toggling airplane mode
- [x] Existing app behavior is completely unchanged — all reads still come from Firestore

---

## Phase 2: Recordings — Local-First with Offline Support

**Goal:** Recordings are stored and read locally. Audio plays from local file. When the device is online, data syncs to/from Firestore. When offline, recordings are saved locally and queued for upload. Cloud-processed fields (transcription, summary) are synced back into Room when they arrive.

### RecordingService — Keep Audio Locally

- [x] Update `android/app/src/main/java/com/voicemind/service/RecordingService.kt`:
  - Inject `LocalAudioManager`, `ConnectivityObserver`, and `RecordingDao`
  - In `handleStopSave`, replace the current flow:
    ```
    BEFORE:
      1. Upload audio to Storage
      2. Create Firestore doc
      3. Call processRecording
      4. Delete local file

    AFTER:
      1. Move audio from cacheDir to LocalAudioManager.saveAudio(recordingId, file)
      2. Insert into Room via RecordingDao with:
         - localAudioPath = saved file's absolute path
         - syncStatus = PENDING_UPLOAD
         - audioPath = "users/{uid}/audio/{recordingId}.m4a" (pre-computed cloud path)
      3. If ConnectivityObserver.isOnline.value == true:
         a. Upload audio to Storage via StorageRepository
         b. Create Firestore doc via RecordingRepository (cloud-only method, see below)
         c. Call processRecording cloud function
         d. Update Room syncStatus = SYNCED
      4. If offline:
         a. Do nothing more — recording is safely stored locally
         b. Enqueue SyncWorker via WorkManager (one-time, constrained to CONNECTED)
      5. Do NOT delete the local file (remove the file.delete() call)
    ```
  - The `catch` block should still set `processingFailed = true` on failure, updating both Room and Firestore (if online)

### RecordingRepository — Dual Write, Local Read

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt`:
  - Inject `RecordingDao` and `LocalAudioManager` in constructor
  - **`observeRecordings()`**: Change from Firestore snapshot to Room DAO:
    ```kotlin
    fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll().map { entities -> entities.map { it.toModel() } }
    ```
  - **`observeByFolder(folderId)`**: Same pattern, use `recordingDao.observeByFolder(folderId)`
  - **`getRecording(recordingId)`**: Read from Room first, fall back to Firestore only if not in Room:
    ```kotlin
    suspend fun getRecording(recordingId: String): Recording? =
        recordingDao.getById(recordingId)?.toModel()
    ```
  - **`createRecording(recording)`**: Write to Room (handled by RecordingService now), keep Firestore write as `createRecordingCloud()` for use by sync:
    ```kotlin
    suspend fun createRecordingCloud(recording: Recording): String {
        val docRef = collection().document(recording.id)
        docRef.set(mapOf(
            "title" to recording.title,
            "folderId" to recording.folderId,
            "audioPath" to recording.audioPath,
            "transcription" to recording.transcription,
            "isDeleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "durationSeconds" to recording.durationSeconds,
        )).await()
        return recording.id
    }
    ```
  - **`updateTitle(recordingId, title)`**: Write to Room first with `syncStatus = PENDING_UPDATE`, then Firestore in background. If Firestore write fails (offline), the SyncWorker will retry:
    ```kotlin
    suspend fun updateTitle(recordingId: String, title: String) {
        recordingDao.updateTitle(recordingId, title.trim().take(25), SyncStatus.PENDING_UPDATE)
        try { collection().document(recordingId).update("title", title.trim().take(25)).await() }
        catch (_: Exception) { /* SyncWorker will retry */ }
    }
    ```
  - **`moveToFolder(recordingId, folderId)`**: Same dual-write pattern
  - **`deleteRecording(recording)`**: Hard delete from Room + delete local audio file + soft delete in Firestore:
    ```kotlin
    suspend fun deleteRecording(recording: Recording) {
        recordingDao.hardDelete(recording.id)
        localAudioManager.deleteAudio(recording.id)
        try {
            collection().document(recording.id).update(
                mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            ).await()
        } catch (_: Exception) {
            // If offline, we've already deleted locally.
            // The cloud still has the data for recovery if needed.
        }
    }
    ```
  - **`generateSummary(recordingId)`**: Still calls cloud function. The Firestore listener (see FirestoreSyncService) will pick up the summary and write it to Room.
  - **Keep** `observeSharedRecording()` and `getSharedRecording()` unchanged (these are cross-user reads that must go through Firestore).

### FirestoreSyncService — Cloud-to-Local Sync

- [x] Create `android/app/src/main/java/com/voicemind/data/sync/FirestoreSyncService.kt`
  ```kotlin
  @Singleton
  class FirestoreSyncService @Inject constructor(
      private val firestore: FirebaseFirestore,
      private val authRepository: AuthRepository,
      private val recordingDao: RecordingDao,
      private val actionItemDao: ActionItemDao,
      private val folderDao: FolderDao,
      private val collectiveSummaryDao: CollectiveSummaryDao,
  ) {
      private var recordingsListener: ListenerRegistration? = null
      private var actionItemsListener: ListenerRegistration? = null
      private var foldersListener: ListenerRegistration? = null
      private var summariesListener: ListenerRegistration? = null
      private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  ```
  - **`startListening()`**: Called after user sign-in. Registers Firestore snapshot listeners for all four collections (`recordings`, `actionItems`, `folders`, `collectiveSummaries`).
  - **`stopListening()`**: Called on sign-out. Removes all listeners.
  - **Recordings listener behavior**:
    - On each snapshot, iterate through `documentChanges`
    - For `ADDED` and `MODIFIED`: read the Firestore document, convert to `RecordingEntity`
    - **Conflict rule**: Cloud-owned fields (`transcription`, `summary`, `processingFailed`, `title` when set by auto-title) always overwrite local. For user-edited fields (`title` edited by user, `folderId`), only overwrite if local `syncStatus == SYNCED` (meaning no pending local changes).
    - Upsert into Room via DAO
    - For `REMOVED`: this means cloud-side deletion (by another device or admin). Hard delete from Room + delete local audio.
  - Same pattern for the other three collections.
  - **Important**: Firestore callbacks run on the main thread. All Room writes must be dispatched to `Dispatchers.IO` via the coroutine scope.

### SyncWorker — Local-to-Cloud Sync

- [x] Create `android/app/src/main/java/com/voicemind/data/sync/SyncWorker.kt`
  ```kotlin
  @HiltWorker
  class SyncWorker @AssistedInject constructor(
      @Assisted context: Context,
      @Assisted params: WorkerParameters,
      private val recordingDao: RecordingDao,
      private val actionItemDao: ActionItemDao,
      private val folderDao: FolderDao,
      private val collectiveSummaryDao: CollectiveSummaryDao,
      private val storageRepository: StorageRepository,
      private val recordingRepository: RecordingRepository,
      private val localAudioManager: LocalAudioManager,
      private val functions: FirebaseFunctions,
      private val navPreferenceRepository: NavPreferenceRepository,
  ) : CoroutineWorker(context, params) {
  ```
  - **`doWork()`**:
    1. Query `recordingDao.getPendingSync()` for recordings with `syncStatus != SYNCED`
    2. For each `PENDING_UPLOAD`:
       - Upload audio file from `localAudioPath` to Storage via `storageRepository.uploadAudio()`
       - Create Firestore document via `recordingRepository.createRecordingCloud()`
       - Call `processRecording` cloud function
       - Update `syncStatus = SYNCED` in Room
    3. For each `PENDING_UPDATE`:
       - Push updated fields to Firestore
       - Update `syncStatus = SYNCED`
    4. For each `PENDING_DELETE`:
       - Soft delete in Firestore
       - Hard delete from Room
    5. Repeat for action items, folders, collective summaries
    6. Return `Result.success()`
  - On failure for any item, leave its `syncStatus` unchanged and return `Result.retry()`

- [x] Create `android/app/src/main/java/com/voicemind/data/sync/SyncScheduler.kt`
  - Utility class to enqueue `SyncWorker`:
    ```kotlin
    @Singleton
    class SyncScheduler @Inject constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun enqueueSync() {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "voicemind_sync",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
    ```

### Playback — Local File

- [x] Update `android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt`:
  - Remove `StorageRepository` dependency for playback
  - Change `playAudio(recording)`:
    ```
    BEFORE:
      val url = storageRepository.getDownloadUrl(recording.audioPath)
      mediaPlayer.setDataSource(url.toString())

    AFTER:
      val entity = recordingDao.getById(recording.id)
      val localPath = entity?.localAudioPath
      if (localPath != null && File(localPath).exists()) {
          mediaPlayer.setDataSource(localPath)
      } else {
          // Fallback for old recordings not yet in local storage
          val url = storageRepository.getDownloadUrl(recording.audioPath)
          mediaPlayer.setDataSource(url.toString())
      }
    ```
  - `shareAudio()` can also use local file instead of downloading from Storage:
    ```
    BEFORE: downloads from getDownloadUrl to cacheDir, then shares
    AFTER: copies from localAudioPath to cacheDir temp file, then shares
    ```

- [x] Update `android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailViewModel.kt`:
  - Change `loadWaveform()` to use local file path instead of `getDownloadUrl()`:
    ```kotlin
    val recording = recordingDao.getById(recordingId)
    val path = recording?.localAudioPath
    if (path != null && File(path).exists()) {
        val bars = WaveformExtractor.extract(path)
        _state.update { it.copy(waveformBars = bars, audioUrl = path) }
    } else {
        // Fallback to cloud URL
        val rec = recordingRepository.getRecording(recordingId) ?: return@launch
        val url = storageRepository.getDownloadUrl(rec.audioPath).toString()
        val bars = WaveformExtractor.extract(url)
        _state.update { it.copy(waveformBars = bars, audioUrl = url) }
    }
    ```

### StorageRepository — Add Download

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/StorageRepository.kt`:
  - Add a method to download audio to local storage (used by SyncWorker for on-demand download, and by Phase 5 for shared audio):
    ```kotlin
    suspend fun downloadAudio(audioPath: String, destinationFile: File) {
        val ref = storage.reference.child(audioPath)
        ref.getFile(destinationFile).await()
        Timber.d("Audio downloaded to ${destinationFile.absolutePath}")
    }
    ```

### Initial Data Hydration

- [x] Create `android/app/src/main/java/com/voicemind/data/sync/InitialSyncManager.kt`
  - On first app launch after this migration (detected by checking if Room is empty and user is signed in):
    - Fetch all recordings from Firestore, insert into Room with `syncStatus = SYNCED`
    - Fetch all action items, folders, collective summaries similarly
    - Audio files are NOT downloaded yet (that happens on-demand when played, or bulk-downloaded if user chooses in Phase 5)
    - Mark initial sync complete in DataStore
  - This runs once. After this, `FirestoreSyncService` keeps Room up to date.
  - `localAudioPath` is `null` for migrated recordings. Playback falls back to cloud URL. Once a recording is played, the audio can be cached locally (optional optimization).

### FirestoreSyncService Lifecycle

- [x] Wire `FirestoreSyncService.startListening()` into the app lifecycle:
  - In `MainActivity` or a dedicated `Application` subclass, call `startListening()` after successful authentication
  - Call `stopListening()` on sign-out (already have sign-out logic in `AuthRepository`)
  - Ensure `InitialSyncManager` runs before `FirestoreSyncService` starts listening (to avoid duplicating initial load)

### Verification

- [x] Record a new memo while online — audio file persists in `filesDir/audio/`, recording appears in Room, Firestore doc is created, transcription arrives via FirestoreSyncService and appears in Room
- [x] Play a recording — audio plays from local file with no network call (verify via Logcat/network inspector)
- [x] Record a memo while offline (airplane mode) — recording saves locally, appears in UI immediately with "pending" indicator, does NOT crash or hang
- [x] Turn network back on — SyncWorker uploads audio + creates Firestore doc + calls processRecording, transcription arrives and updates Room
- [x] Rename a recording — Room updates instantly, Firestore updates in background
- [x] Delete a recording — disappears from UI immediately (Room hard delete), audio file deleted from disk, Firestore gets soft delete
- [x] Waveform renders from local file (no download URL call)
- [x] Share audio uses local file instead of downloading from cloud
- [x] Existing recordings from before migration still work (fallback to cloud URL for playback)

---

## Phase 3: Action Items, Folders, Collective Summaries — Local-First

**Goal:** All remaining data types read from Room as primary source. Same dual-write + sync pattern as Phase 2. After this phase, the UI never reads directly from Firestore snapshot listeners for its own data.

### ActionItemRepository

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt`:
  - Inject `ActionItemDao`
  - **`observeActionItems()`**: Read from Room:
    ```kotlin
    fun observeActionItems(): Flow<List<ActionItem>> =
        actionItemDao.observeAll().map { entities -> entities.map { it.toModel() } }
    ```
  - **`observeByRecordingId(recordingId)`**: Read from Room:
    ```kotlin
    fun observeByRecordingId(recordingId: String): Flow<List<ActionItem>> =
        actionItemDao.observeByRecordingId(recordingId).map { it.map { e -> e.toModel() } }
    ```
  - **`observeActionItem(itemId)`**: Read from Room:
    ```kotlin
    fun observeActionItem(itemId: String): Flow<ActionItem?> =
        actionItemDao.observeById(itemId).map { it?.toModel() }
    ```
  - **`observeSharedTasks()`**: Read from Room:
    ```kotlin
    fun observeSharedTasks(): Flow<List<ActionItem>> =
        actionItemDao.observeSharedTasks().map { it.map { e -> e.toModel() } }
    ```
  - **`createItem(title)`**: Write to Room first with `syncStatus = PENDING_UPLOAD`, then Firestore:
    ```kotlin
    suspend fun createItem(title: String) {
        val id = collection().document().id
        val entity = ActionItemEntity(
            id = id, title = title, completed = false, recordingId = null,
            createdAt = System.currentTimeMillis(), /* ... other fields null/default ... */
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )
        actionItemDao.upsert(entity)
        try {
            collection().document(id).set(mapOf(
                "title" to title, "completed" to false, "isDeleted" to false,
                "createdAt" to Timestamp.now(),
            )).await()
            actionItemDao.updateSyncStatus(id, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker retries */ }
    }
    ```
  - **`toggleCompleted(itemId, completed)`**: Update Room, then Firestore (dual write)
  - **`updateTitle(itemId, title)`**: Update Room, then Firestore (dual write)
  - **`updateDueDate(itemId, dueDate)`**: Update Room, then Firestore (dual write)
  - **`updateDeadline(itemId, deadline)`**: Update Room, then Firestore (dual write)
  - **`updateNotes(itemId, notes)`**: Update Room, then Firestore (dual write)
  - **`deleteItem(itemId)`**: Hard delete from Room, soft delete in Firestore
  - **`deleteItems(itemIds)`**: Batch hard delete from Room, batch soft delete in Firestore
  - **`markCompleted(itemIds, completed)`**: Batch update Room, batch update Firestore
  - **`getActionItem(itemId)`**: Read from Room
  - **`getByRecordingId(recordingId)`**: Read from Room
  - **Keep unchanged**: `retryExtractActionItems()` (calls cloud function; new items arrive via FirestoreSyncService), `observeActionItemsForRecording()` (cross-user shared read, stays Firestore), `addSharedTask()` (writes to Firestore, picked up by listener → Room), `isSharedTaskAdded()`, `hasGeneratedTasksForSharedRecording()`

### FolderRepository

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt`:
  - Inject `FolderDao`
  - **`observeFolders()`**: Read from Room:
    ```kotlin
    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { entities -> entities.map { it.toModel() } }
    ```
  - **`createFolder(name)`**: Write to Room with `syncStatus = PENDING_UPLOAD`, then Firestore
  - **`renameFolder(folderId, newName)`**: Update Room, then Firestore
  - **`deleteFolder(folderId)`**: Hard delete from Room, soft delete in Firestore
  - **`seedDefaultsIfEmpty()`**: Check Room first. If Room has no folders and Firestore has none, seed defaults into both Room and Firestore. If Room is empty but Firestore has folders (migration scenario), the InitialSyncManager from Phase 2 handles population.
  - **`reassignFolder()`** (from RecordingRepository): Update folderId in Room for affected recordings, then Firestore batch

### CollectiveSummaryRepository

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt`:
  - Inject `CollectiveSummaryDao`
  - **`observeSummaries()`**: Read from Room
  - **`getSummary(summaryId)`**: Read from Room
  - **`generateCollectiveSummary(recordingIds)`**: Still calls cloud function. The cloud function creates the Firestore doc. FirestoreSyncService picks it up and inserts into Room. Return value should come from the listener update (or poll Room after calling the function).
  - **`deleteSummary(summaryId)`**: Hard delete from Room, soft delete in Firestore
  - **Keep unchanged**: `getSharedSummary()`, `observeSharedSummary()`, `duplicateSharedSummary()` (cross-user reads, stay Firestore)

### Update FirestoreSyncService

- [x] Update `FirestoreSyncService` (created in Phase 2) to include listeners for action items, folders, and collective summaries — following the same pattern as recordings:
  - `actionItems` listener: upsert into `ActionItemDao`, handle `ADDED`/`MODIFIED`/`REMOVED`
  - `folders` listener: upsert into `FolderDao`
  - `collectiveSummaries` listener: upsert into `CollectiveSummaryDao`

### Update SyncWorker

- [x] Update `SyncWorker` (created in Phase 2) to handle pending sync for action items, folders, and collective summaries — same pattern as recordings (check `getPendingSync()`, push to Firestore, update status)

### Verification

- [x] Action items list loads from Room (no Firestore call in RecordingsViewModel/TasksViewModel)
- [x] Creating a task saves to Room immediately and syncs to Firestore in background
- [x] Completing/editing a task updates Room first, then Firestore
- [x] Deleting a task removes from Room immediately, soft-deletes in Firestore
- [x] Folders list loads from Room
- [x] Creating/renaming/deleting folders works local-first
- [x] Collective summaries load from Room
- [x] Generating a collective summary (cloud function) results in the summary appearing in Room via FirestoreSyncService
- [x] All operations work offline — changes queue and sync when connectivity returns
- [x] Google Tasks sync results (from cloud triggers) arrive into Room via FirestoreSyncService

---

## Phase 4: Offline UX, Sync Indicators, and Delete Behavior

**Goal:** Users see clear visual indicators when offline, when recordings are pending processing, and when sync is in progress. Delete behavior is finalized: hard delete local, soft delete cloud. Users are told when an action requires internet.

### Connectivity State in UI

- [x] Create `android/app/src/main/java/com/voicemind/ui/common/OfflineBanner.kt`
  - A composable that shows a subtle top banner when `ConnectivityObserver.isOnline` is `false`
  - Text: "You're offline. Changes will sync when connected."
  - Amber/warning color, dismissable but reappears on navigation
  - Thin bar at the top of the screen, does not push content down significantly

- [x] Add `OfflineBanner` to the main scaffold in `MainActivity.kt` or the root navigation composable
  - It should appear on every screen when offline

### Recording Pending Processing State

- [x] Update recording list item UI in `RecordingsScreen.kt`:
  - When a recording has `transcription == null` AND `processingFailed == false` AND `syncStatus == PENDING_UPLOAD`:
    - Show a "Waiting for internet" chip/badge on the recording card
  - When a recording has `transcription == null` AND `processingFailed == false` AND `syncStatus == SYNCED`:
    - Show a "Processing..." chip/badge (upload done, waiting for cloud function to complete)
  - When `processingFailed == true`:
    - Show "Processing failed" with retry button (existing behavior)
  - The `syncStatus` field needs to be exposed in the UI model. Add it to `Recording` data class or create a `RecordingUiModel` wrapper.

### "Needs Internet" Dialogs

- [x] When the user taps "Generate Summary" on a recording that has no transcription:
  - If offline: Show dialog — "This recording hasn't been processed yet. Please connect to the internet so VoiceMind can transcribe the audio and generate a summary."
  - If online but transcription is still pending: Show dialog — "This recording is still being processed. Please wait a moment and try again."

- [x] When the user taps "Generate Tasks" on a recording with no transcription:
  - Same dialog pattern as above

- [x] When the user tries to share a recording while offline:
  - Show dialog — "Sharing requires an internet connection. Please connect and try again."

### Delete Behavior — Final Implementation

- [x] Ensure all delete paths follow this pattern:
  | Data Type | Local (Room) | Local (File) | Cloud (Firestore) |
  |-----------|-------------|--------------|-------------------|
  | Recording | Hard delete from Room | Delete audio from disk | Soft delete (`isDeleted = true`) |
  | Action Item | Hard delete from Room | N/A | Soft delete |
  | Folder | Hard delete from Room | N/A | Soft delete |
  | Collective Summary | Hard delete from Room | N/A | Soft delete |

- [x] For offline deletes:
  1. Hard delete from Room immediately (user sees it disappear)
  2. Delete local audio file if applicable
  3. If online: soft delete in Firestore immediately
  4. If offline: The item is already gone from local. When the user comes back online, we need to push the soft delete. **Strategy**: Before hard-deleting from Room, if offline, insert a record into a new `pending_deletes` Room table with `(entityType, entityId, deletedAt)`. SyncWorker processes this table when online.

- [x] Create `android/app/src/main/java/com/voicemind/data/local/entity/PendingDeleteEntity.kt`:
  ```kotlin
  @Entity(tableName = "pending_deletes")
  data class PendingDeleteEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val entityType: String,
      val entityId: String,
      val createdAt: Long = System.currentTimeMillis(),
  )
  ```

- [x] Create `PendingDeleteDao.kt` with `getAll()`, `insert()`, `delete(id)`, `deleteAll()`

- [x] Update `AppDatabase` to include `PendingDeleteEntity` and `PendingDeleteDao` (Room migration required — version 1 → 2)

- [x] Update `SyncWorker` to process pending deletes: read from `PendingDeleteDao`, soft-delete in Firestore, then remove from pending deletes table

### Sync Status in Settings (Optional)

- [x] Add a "Sync Status" row in `SettingsScreen.kt`:
  - Shows "All synced" when no pending items
  - Shows "X items pending sync" when there are pending items
  - Tapping shows a brief breakdown

### Verification

- [x] Offline banner appears when airplane mode is on, disappears when turned off
- [x] Recording card shows "Waiting for internet" when recorded offline
- [x] Recording card shows "Processing..." when uploaded but transcript not yet received
- [x] "Generate Summary" on an unprocessed recording while offline shows the correct dialog
- [x] "Generate Tasks" on an unprocessed recording while offline shows the correct dialog
- [x] Deleting a recording while offline removes it from UI immediately, does NOT crash
- [x] Coming back online after offline delete correctly soft-deletes in Firestore
- [x] Deleting a recording while online removes from Room + audio file + soft-deletes Firestore

---

## Phase 5: Shared Content Local Storage and New Device Setup

**Goal:** Shared recordings are downloaded locally for offline playback. On a new device or reinstall, the user chooses their sync strategy (download everything, on-demand, or metadata-only).

### Shared Audio — Download and Cache Locally

- [x] Update `android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt`:
  - When loading shared recording audio:
    ```
    BEFORE:
      1. Call getSharedAudioUrl() every time
      2. Pass URL to MediaPlayer

    AFTER:
      1. Check if LocalAudioManager.sharedAudioExists(shareId)
      2. If yes: play from local file
      3. If no:
         a. Call getSharedAudioUrl() to get signed URL
         b. Download to LocalAudioManager.getSharedAudioFile(shareId) via StorageRepository
         c. Play from local file
         d. (Show download progress indicator in UI)
    ```

- [x] Update `SharingRepository.dismissSharedItem()`:
  - After dismissing, also call `localAudioManager.deleteSharedAudio(shareId)` to clean up disk space

### Shared Items in Room (Optional Extension)

- [x] Create `SharedItemEntity.kt` in `data/local/entity/`:
  ```kotlin
  @Entity(tableName = "shared_items")
  data class SharedItemEntity(
      @PrimaryKey val id: String,
      val ownerUid: String,
      val ownerName: String,
      val ownerEmail: String,
      val itemType: String,
      val itemId: String,
      val sharedAt: Long?,
      val isRead: Boolean,
      val ownerItemDeleted: Boolean,
      val localAudioPath: String?,
  )
  ```
  - This allows viewing shared items offline
  - Requires `SharedItemDao` and Room migration

- [x] Update `FirestoreSyncService` to add a listener for `sharedWithMe` collection → Room

### New Device / Reinstall Setup

- [x] Create `android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupScreen.kt`:
  - Full-screen dialog shown on first launch when:
    - User is authenticated (signed in)
    - Room database is empty (no recordings)
    - DataStore does not have `device_setup_complete = true`
  - Title: "Set Up Local Storage"
  - Explanation text: "VoiceMind stores your recordings locally for faster access and offline use. How would you like to set up this device?"
  - Three options (radio buttons):
    1. **"Download Everything"** — Sync all recordings, audio files, and data from the cloud. Best for your primary device. (Warning: may use significant storage)
    2. **"Download on Demand"** — Sync metadata (titles, transcripts, tasks) now. Audio files download when you first play them. Balanced approach.
    3. **"Metadata Only"** — Sync metadata only. Audio streams from cloud when played. Minimum storage use.
  - Confirm button: "Set Up"
  - Note at bottom: "This choice cannot be changed without reinstalling the app."

- [x] Create `android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupViewModel.kt`:
  - Holds selected option state
  - On confirm:
    - Saves choice to DataStore via `NavPreferenceRepository`:
      - `deviceSyncStrategy`: `"full"` | `"on_demand"` | `"metadata_only"`
      - `deviceSetupComplete`: `true`
    - Triggers `InitialSyncManager` with the chosen strategy

- [x] Update `android/app/src/main/java/com/voicemind/data/repository/NavPreferenceRepository.kt`:
  - Add keys and methods:
    ```kotlin
    private val deviceSyncStrategyKey = stringPreferencesKey("device_sync_strategy")
    private val deviceSetupCompleteKey = booleanPreferencesKey("device_setup_complete")

    val deviceSyncStrategy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[deviceSyncStrategyKey] ?: "on_demand"
    }

    val isDeviceSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[deviceSetupCompleteKey] ?: false
    }

    suspend fun setDeviceSyncStrategy(strategy: String) { ... }
    suspend fun setDeviceSetupComplete(complete: Boolean) { ... }
    ```

- [x] Update `InitialSyncManager` (from Phase 2):
  - Accept sync strategy parameter
  - **"full"**: Fetch all metadata from Firestore → Room, then enqueue a `BulkDownloadWorker` that downloads every audio file from Storage to `LocalAudioManager`
  - **"on_demand"**: Fetch all metadata from Firestore → Room, set `localAudioPath = null`. Audio downloads on first play (the playback fallback in Phase 2 handles this, but now also saves the downloaded file locally for future plays)
  - **"metadata_only"**: Fetch all metadata from Firestore → Room, set `localAudioPath = null`. Audio always streams from cloud (no local caching)

- [x] Create `android/app/src/main/java/com/voicemind/data/sync/BulkDownloadWorker.kt`:
  - WorkManager worker that downloads all audio files for recordings where `localAudioPath == null`
  - Shows a notification with progress (X of Y files downloaded)
  - Runs with network constraint
  - Updates Room `localAudioPath` as each file completes

- [x] Wire `DeviceSetupScreen` into navigation:
  - In `AppNavHost.kt`, check `isDeviceSetupComplete` on launch
  - If false and user is signed in, show `DeviceSetupScreen` before proceeding to main app
  - After setup completes, navigate to main app

### On-Demand Download During Playback

- [x] Update playback fallback logic in `RecordingsViewModel.playAudio()` (Phase 2 code):
  - When `localAudioPath == null` and `deviceSyncStrategy == "on_demand"`:
    1. Download audio from Storage to `LocalAudioManager` in background
    2. Update Room `localAudioPath`
    3. Play from local file
  - When `deviceSyncStrategy == "metadata_only"`:
    1. Stream from cloud URL (existing behavior)
    2. Do NOT save locally

### Verification

- [x] Opening a shared recording downloads audio locally; second play uses local file (no network)
- [x] Dismissing a shared item deletes the local audio file
- [x] Fresh install with existing account shows DeviceSetupScreen
- [x] Choosing "Download Everything" downloads all audio files with progress notification
- [x] Choosing "Download on Demand" syncs metadata only; audio downloads on first play
- [x] Choosing "Metadata Only" syncs metadata only; audio streams from cloud
- [x] Choice is persisted and not shown again on subsequent launches
- [x] Device setup cannot be changed without reinstalling

---

## Phase 6: Storage Management, User Consent, and Cleanup

**Goal:** Users have visibility into local storage usage, can manage their storage, and are presented with a consent dialog when the app first adopts local storage on upgrade.

### User Consent on Upgrade

- [x] Create `android/app/src/main/java/com/voicemind/ui/setup/LocalStorageConsentDialog.kt`:
  - Shown on first app launch after upgrading to the local-first version (detected: user is signed in, has Firestore data, but `deviceSetupComplete == false`)
  - Title: "New: Local Storage"
  - Body: "VoiceMind now stores your recordings locally on your device for faster access, offline playback, and reduced data usage. Your data is still backed up to the cloud."
  - Storage estimate: "Estimated storage needed: ~X MB" (calculated from Firestore recording count × average file size)
  - Button: "Set Up Now" → navigates to `DeviceSetupScreen`
  - Button: "Later" → dismisses, shown again on next launch until setup is complete

### Storage Usage Screen

- [x] Create `android/app/src/main/java/com/voicemind/ui/settings/StorageScreen.kt`:
  - Accessible from Settings screen (add "Storage" row under a new "DATA" section)
  - Displays:
    - **Total local storage used**: `LocalAudioManager.getTotalSizeBytes()` + Room database file size
    - **Breakdown**:
      - Your recordings: `LocalAudioManager.getOwnAudioSizeBytes()` — formatted as "X.X MB" or "X.X GB"
      - Shared recordings: `LocalAudioManager.getSharedAudioSizeBytes()`
      - Database: `context.getDatabasePath("voicemind.db")?.length()`
    - **Device sync strategy**: Shows current choice (e.g., "Download on Demand")
  - Actions:
    - **"Clear Shared Audio Cache"** button: Calls `localAudioManager.clearSharedAudio()`, shows confirmation dialog first
    - **"Clear All Local Data"** button: Calls `localAudioManager.clearAllAudio()` + Room `deleteAll()` on all DAOs + resets `deviceSetupComplete = false`. Shows warning dialog: "This will remove all local data. Your recordings are still safely stored in the cloud. You'll need to set up local storage again."

- [x] Create `android/app/src/main/java/com/voicemind/ui/settings/StorageViewModel.kt`:
  - Exposes storage size stats
  - Handles clear actions with confirmation state

### Settings Integration

- [x] Update `SettingsScreen.kt`:
  - Add "DATA" section with:
    - "Storage" row → navigates to `StorageScreen`
    - Shows total storage used as subtitle (e.g., "Using 245 MB")
  - Add route in `Routes.kt` and `AppNavHost.kt`

### Storage Permission Notes

- [x] Confirm that `context.filesDir` (app-internal storage) does NOT require any runtime permissions on API 28+. This is the approach used by `LocalAudioManager`. No permission dialogs are needed.
- [x] If in the future external storage is desired (e.g., to survive app data clear), add `MANAGE_EXTERNAL_STORAGE` for API 30+ or use `MediaStore`. But for now, `filesDir` is sufficient and permission-free.

### Verification

- [x] Consent dialog appears on first launch after upgrade
- [x] Storage screen shows accurate breakdown of storage usage
- [x] "Clear Shared Audio Cache" removes shared audio files and frees space
- [x] "Clear All Local Data" wipes Room + audio files and triggers device setup flow on next launch
- [x] Storage row in Settings shows correct total size
- [x] No runtime permission dialogs appear for local storage

---

## Appendix: Phase Dependencies

```
Phase 1  ──→  Phase 2  ──→  Phase 3  ──→  Phase 4
                  │                            │
                  └──────────────┬──────────────┘
                                 ▼
                             Phase 5  ──→  Phase 6
```

- Phases 1–4 are strictly sequential (each depends on the previous)
- Phase 5 depends on Phase 2 (local audio manager, sync infrastructure) and Phase 4 (offline UX patterns)
- Phase 6 depends on Phase 5 (device setup, storage management builds on the sync strategy choice)

---

## Appendix: New Files Created Across All Phases

| Phase | File | Type |
|-------|------|------|
| 1 | `android/.../data/local/SyncStatus.kt` | Enum |
| 1 | `android/.../data/local/entity/RecordingEntity.kt` | Room Entity |
| 1 | `android/.../data/local/entity/ActionItemEntity.kt` | Room Entity |
| 1 | `android/.../data/local/entity/FolderEntity.kt` | Room Entity |
| 1 | `android/.../data/local/entity/CollectiveSummaryEntity.kt` | Room Entity |
| 1 | `android/.../data/local/dao/RecordingDao.kt` | Room DAO |
| 1 | `android/.../data/local/dao/ActionItemDao.kt` | Room DAO |
| 1 | `android/.../data/local/dao/FolderDao.kt` | Room DAO |
| 1 | `android/.../data/local/dao/CollectiveSummaryDao.kt` | Room DAO |
| 1 | `android/.../data/local/AppDatabase.kt` | Room Database |
| 1 | `android/.../data/local/Converters.kt` | Type Converters |
| 1 | `android/.../data/local/EntityMappers.kt` | Mapper Functions |
| 1 | `android/.../data/local/LocalAudioManager.kt` | Utility |
| 1 | `android/.../util/ConnectivityObserver.kt` | Utility |
| 2 | `android/.../data/sync/FirestoreSyncService.kt` | Sync Service |
| 2 | `android/.../data/sync/SyncWorker.kt` | WorkManager Worker |
| 2 | `android/.../data/sync/SyncScheduler.kt` | Utility |
| 2 | `android/.../data/sync/InitialSyncManager.kt` | Migration Utility |
| 4 | `android/.../ui/common/OfflineBanner.kt` | UI Component |
| 4 | `android/.../data/local/entity/PendingDeleteEntity.kt` | Room Entity |
| 5 | `android/.../data/local/entity/SharedItemEntity.kt` | Room Entity |
| 5 | `android/.../ui/setup/DeviceSetupScreen.kt` | Screen |
| 5 | `android/.../ui/setup/DeviceSetupViewModel.kt` | ViewModel |
| 5 | `android/.../data/sync/BulkDownloadWorker.kt` | WorkManager Worker |
| 6 | `android/.../ui/setup/LocalStorageConsentDialog.kt` | Dialog |
| 6 | `android/.../ui/settings/StorageScreen.kt` | Screen |
| 6 | `android/.../ui/settings/StorageViewModel.kt` | ViewModel |

## Appendix: Modified Files Across All Phases

| Phase | File | Change Summary |
|-------|------|----------------|
| 1 | `android/gradle/libs.versions.toml` | Add Room and WorkManager versions + libraries |
| 1 | `android/app/build.gradle.kts` | Add Room, WorkManager dependencies |
| 1 | `android/.../di/AppModule.kt` | Provide AppDatabase, DAOs |
| 2 | `android/.../service/RecordingService.kt` | Keep audio locally, insert Room, conditional cloud upload |
| 2 | `android/.../data/repository/RecordingRepository.kt` | Read from Room, dual-write, local-first delete |
| 2 | `android/.../data/repository/StorageRepository.kt` | Add downloadAudio() method |
| 2 | `android/.../ui/recording/RecordingsViewModel.kt` | Play from local file, remove getDownloadUrl for playback |
| 2 | `android/.../ui/recording/RecordingDetailViewModel.kt` | Waveform from local file |
| 3 | `android/.../data/repository/ActionItemRepository.kt` | Read from Room, dual-write, local-first delete |
| 3 | `android/.../data/repository/FolderRepository.kt` | Read from Room, dual-write, local-first delete |
| 3 | `android/.../data/repository/CollectiveSummaryRepository.kt` | Read from Room, dual-write, local-first delete |
| 3 | `android/.../data/sync/FirestoreSyncService.kt` | Add listeners for actionItems, folders, summaries |
| 3 | `android/.../data/sync/SyncWorker.kt` | Handle pending sync for all entity types |
| 4 | `android/.../ui/recording/RecordingsScreen.kt` | Pending processing badge, offline-aware dialogs |
| 4 | `android/.../data/local/AppDatabase.kt` | Add PendingDeleteEntity, migration v1→v2 |
| 4 | `android/.../data/sync/SyncWorker.kt` | Process pending deletes table |
| 4 | `android/.../ui/settings/SettingsScreen.kt` | Add sync status row |
| 5 | `android/.../ui/sharing/SharedRecordingDetailViewModel.kt` | Download shared audio locally |
| 5 | `android/.../data/repository/SharingRepository.kt` | Clean up local shared audio on dismiss |
| 5 | `android/.../data/repository/NavPreferenceRepository.kt` | Add deviceSyncStrategy, deviceSetupComplete prefs |
| 5 | `android/.../data/sync/InitialSyncManager.kt` | Accept sync strategy, implement bulk vs on-demand |
| 5 | `android/.../ui/navigation/Routes.kt` | Add device setup route |
| 5 | `android/.../ui/navigation/AppNavHost.kt` | Wire DeviceSetupScreen, check setup state |
| 6 | `android/.../ui/settings/SettingsScreen.kt` | Add Storage row under DATA section |
| 6 | `android/.../ui/navigation/Routes.kt` | Add storage route |
| 6 | `android/.../ui/navigation/AppNavHost.kt` | Wire StorageScreen |

## Appendix: Conflict Resolution Rules

| Field | Owner | Rule |
|-------|-------|------|
| `transcription` | Cloud (processRecording function) | Cloud always wins |
| `summary` | Cloud (generateSummary function) | Cloud always wins |
| `processingFailed` | Cloud | Cloud always wins |
| `title` (auto-generated) | Cloud (processRecording function) | Cloud wins only if local `syncStatus == SYNCED` |
| `title` (user-edited) | Local (user action) | Local wins; pushes to cloud |
| `folderId` | Local (user action) | Local wins; pushes to cloud |
| `completed` (action item) | Local (user action) | Local wins; pushes to cloud |
| `notes` | Local (user action) | Local wins; pushes to cloud |
| `dueDate`, `deadline` | Local (user action) | Local wins; pushes to cloud |
| `googleTaskId`, `calendarEventId` | Cloud (Google Tasks sync trigger) | Cloud always wins |
| `autoScheduled` | Cloud (NTS trigger) | Cloud always wins |

## Appendix: Room Database Migrations

| From → To | Trigger Phase | Changes |
|-----------|--------------|---------|
| — → v1 | Phase 1 | Initial schema: recordings, action_items, folders, collective_summaries |
| v1 → v2 | Phase 4 | Add pending_deletes table |
| v2 → v3 | Phase 5 | Add shared_items table |

Use `fallbackToDestructiveMigration()` during development. Before any production release, write proper `Migration` objects that preserve user data.
