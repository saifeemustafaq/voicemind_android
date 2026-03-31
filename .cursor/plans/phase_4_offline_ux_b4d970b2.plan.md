---
name: Phase 4 Offline UX
overview: Phase 4 adds offline UX indicators (banner, pending processing badges, needs-internet dialogs), finalizes delete behavior with a PendingDelete table to prevent ghost re-insertion, adds connectivity-triggered sync, and fixes the Phase 3 pushFolderCloud timestamp bug.
todos:
  - id: fix-phase3-bugs
    content: Fix pushFolderCloud timestamp bug + add connectivity-triggered SyncWorker in MainActivity
    status: pending
  - id: offline-banner
    content: Create OfflineBanner composable + wire into both AppNavHost Scaffold modes
    status: pending
  - id: pending-processing
    content: Add syncStatus to Recording model + EntityMappers, add processing status chip to RecordingRow
    status: pending
  - id: needs-internet-dialogs
    content: Add connectivity checks + NeedsInternetReason state to RecordingsViewModel, show dialogs in RecordingsScreen and RecordingDetailScreen
    status: pending
  - id: pending-delete-infra
    content: Create PendingDeleteEntity + PendingDeleteDao, update AppDatabase v1->v2 with migration, provide DAO in AppModule
    status: pending
  - id: update-deletes
    content: Update delete methods in all 4 repositories to use PendingDeleteDao (insert before hard-delete, remove after Firestore succeeds)
    status: pending
  - id: guard-reinsertion
    content: Update FirestoreSyncService to check pending_deletes before upserting in all 4 handlers
    status: pending
  - id: sync-worker-deletes
    content: Update SyncWorker to process pending_deletes table + remove Phase 4 placeholder comments
    status: pending
  - id: settings-sync-status
    content: (Optional) Add sync status row to SettingsScreen showing pending item count
    status: pending
isProject: false
---

# Phase 4: Offline UX, Sync Indicators, and Delete Behavior

## Pre-requisite: Fix Phase 3 Bugs

### A1. Fix `pushFolderCloud` timestamp

In [FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt) line 120, change `FieldValue.serverTimestamp()` to preserve the entity's local timestamp:

```kotlin
// Before:
"createdAt" to FieldValue.serverTimestamp(),

// After:
"createdAt" to (entity.createdAt.toTimestamp() ?: FieldValue.serverTimestamp()),
```

Requires adding `import com.voicemind.data.local.toTimestamp`.

### A2. Connectivity-triggered SyncWorker

Systematic fix for the missing `syncScheduler.enqueueSync()` gap across all repositories. Instead of sprinkling `enqueueSync()` into every catch block, add a single connectivity listener in [MainActivity.kt](android/app/src/main/java/com/voicemind/MainActivity.kt) that enqueues the worker whenever the device goes from offline to online:

```kotlin
LaunchedEffect(uid) {
    var wasOffline = !connectivityObserver.isCurrentlyOnline()
    connectivityObserver.isOnline.collect { online ->
        if (online && wasOffline) syncScheduler.enqueueSync()
        wasOffline = !online
    }
}
```

This ensures ALL pending changes (uploads, updates, deletes) are flushed when connectivity returns, regardless of which repository created them. `ConnectivityObserver` and `SyncScheduler` are both `@Singleton`s injectable into `MainViewModel` or accessed directly in `MainActivity`.

---

## B. Offline Banner

### B1. Create `OfflineBanner.kt`

New file: `android/app/src/main/java/com/voicemind/ui/common/OfflineBanner.kt`

A thin, animated composable that slides in from the top when `ConnectivityObserver.isOnline` is `false`. Dismissable for the current navigation, reappears on route change.

- Background: `MaterialTheme.colorScheme.tertiaryContainer` (amber/warm tone in M3)
- Text: "You're offline. Changes will sync when connected." in `bodySmall`, `onTertiaryContainer` color
- Height: compact (~36dp total with padding)
- Uses `AnimatedVisibility` with `expandVertically`/`shrinkVertically` for smooth show/hide
- A small `IconButton(Close)` to dismiss; dismissal state resets when `currentRoute` changes

### B2. Wire into `AppNavHost.kt`

In [AppNavHost.kt](android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt), inject `ConnectivityObserver` and add the banner inside **both** Scaffold layouts (sidebar mode around line 120 and bottom-bar mode around line 197):

```kotlin
Scaffold { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
        OfflineBanner(isOnline = isOnline, currentRoute = currentRoute)
        NavHost(
            navController = navController,
            startDestination = ...,
            modifier = Modifier.weight(1f),
        ) { ... }
    }
}
```

The `ConnectivityObserver` can be provided via `hiltViewModel()` on a lightweight ViewModel, or passed through `AppNavHost` parameters from `MainActivity` (which already holds the DI graph). Passing as a parameter from `MainActivity` is simpler since `AppNavHost` is not a `@Composable` with its own ViewModel.

---

## C. Recording Pending Processing State

### C1. Add `syncStatus` to `Recording` model

In [Recording.kt](android/app/src/main/java/com/voicemind/data/model/Recording.kt), add:

```kotlin
import com.google.firebase.firestore.Exclude
import com.voicemind.data.local.SyncStatus

data class Recording(
    // ... existing fields ...
    @get:Exclude val syncStatus: SyncStatus = SyncStatus.SYNCED,
)
```

`@get:Exclude` tells Firestore to ignore this field during serialization/deserialization. The default value `SYNCED` is used when Firestore creates the object, which is correct for cloud-sourced recordings.

### C2. Update EntityMappers

In [EntityMappers.kt](android/app/src/main/java/com/voicemind/data/local/EntityMappers.kt), update `RecordingEntity.toModel()` to include `syncStatus`:

```kotlin
fun RecordingEntity.toModel() = Recording(
    // ... existing fields ...
    syncStatus = syncStatus,
)
```

### C3. Add processing status to `RecordingRow`

In [RecordingsScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsScreen.kt), add a status chip below the date/duration line in `RecordingRow` (around line 930, after the existing `Text` for date):

Three states to show:


| Condition                                                                    | Chip text            | Color                                         |
| ---------------------------------------------------------------------------- | -------------------- | --------------------------------------------- |
| `transcription == null && !processingFailed && syncStatus == PENDING_UPLOAD` | "Waiting for upload" | `tertiaryContainer` / `onTertiaryContainer`   |
| `transcription == null && !processingFailed && syncStatus == SYNCED`         | "Processing..."      | `secondaryContainer` / `onSecondaryContainer` |
| `processingFailed == true`                                                   | "Processing failed"  | `errorContainer` / `onErrorContainer`         |


Use a small `Surface` with `shape = MaterialTheme.shapes.small`, compact padding (horizontal 8dp, vertical 2dp), and `bodySmall` typography. The existing "processingFailed" styling in `TranscriptContent` (`RecordingDialogs.kt`) stays as-is for the transcript sheet; this adds inline visibility on the list row.

---

## D. "Needs Internet" Dialogs

### D1. Add state + checks to `RecordingsViewModel`

In [RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt):

- Inject `ConnectivityObserver`
- Add to `RecordingsListState`:

```kotlin
  val needsInternetDialog: NeedsInternetReason? = null,
  

```

  Where `NeedsInternetReason` is a sealed class: `GenerateSummary`, `GenerateTasks`, `ShareWithUser`.

- In `generateSummary(recording)`: before proceeding, check:
  - If `recording.transcription == null && !connectivityObserver.isCurrentlyOnline()` -> set `needsInternetDialog = NeedsInternetReason.GenerateSummary`
  - If `recording.transcription == null && recording.syncStatus != SyncStatus.SYNCED` -> set dialog with "still being processed" message
  - Otherwise: proceed as normal
- In `generateTasks(recording)`: same check pattern
- In share flows (`shareAudio`, `shareWithUser`): check if offline -> set `needsInternetDialog = NeedsInternetReason.ShareWithUser`
- Add `fun dismissNeedsInternetDialog()` that sets it to null

### D2. Show dialogs in UI

In [RecordingsScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsScreen.kt) and [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt), observe `needsInternetDialog` and show an `AlertDialog` following the existing pattern from [TasksSyncPromptDialog.kt](android/app/src/main/java/com/voicemind/ui/components/TasksSyncPromptDialog.kt):

- Title: "Internet Required" or "Still Processing"
- Body: varies by reason (as specified in the `localphase.md` spec)
- Single "OK" TextButton to dismiss
- Follow existing dialog pattern: `AlertDialog`, `TextButton`, M3 colors

---

## E. Offline Delete with PendingDelete Table

### E1. Create `PendingDeleteEntity`

New file: `android/app/src/main/java/com/voicemind/data/local/entity/PendingDeleteEntity.kt`

```kotlin
@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,  // "recording", "actionItem", "folder", "collectiveSummary"
    val entityId: String,
    val createdAt: Long = System.currentTimeMillis(),
)
```

### E2. Create `PendingDeleteDao`

New file: `android/app/src/main/java/com/voicemind/data/local/dao/PendingDeleteDao.kt`

```kotlin
@Dao
interface PendingDeleteDao {
    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Query("SELECT entityId FROM pending_deletes WHERE entityType = :type")
    suspend fun getDeletedIds(type: String): List<String>

    @Insert
    suspend fun insert(entity: PendingDeleteEntity)

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_deletes")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM pending_deletes WHERE entityType = :type AND entityId = :entityId LIMIT 1)")
    suspend fun exists(type: String, entityId: String): Boolean
}
```

The `exists()` query is critical -- `FirestoreSyncService` calls it before re-inserting.

### E3. Update `AppDatabase` (v1 -> v2)

In [AppDatabase.kt](android/app/src/main/java/com/voicemind/data/local/AppDatabase.kt):

- Bump `version = 2`
- Add `PendingDeleteEntity::class` to entities array
- Add `abstract fun pendingDeleteDao(): PendingDeleteDao`
- Add a manual `Migration(1, 2)` that creates the new table (avoids destructive migration):

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_deletes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
    }
}
```

In [AppModule.kt](android/app/src/main/java/com/voicemind/di/AppModule.kt), add the migration and provide the DAO:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "voicemind.db")
    .addMigrations(AppDatabase.MIGRATION_1_2)
    .fallbackToDestructiveMigration()
    .build()

@Provides
fun providePendingDeleteDao(db: AppDatabase): PendingDeleteDao = db.pendingDeleteDao()
```

### E4. Update delete methods in all repositories

Inject `PendingDeleteDao` and `ConnectivityObserver` into all four repositories. Change delete flow to:

1. Insert `PendingDeleteEntity(entityType, entityId)` into pending_deletes
2. Hard-delete from Room entity table
3. Delete local audio file if recording
4. Try Firestore soft-delete
5. If succeeds: remove from pending_deletes
6. If fails: leave in pending_deletes (SyncWorker will handle)

Example for [RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt):

```kotlin
suspend fun deleteRecording(recording: Recording) {
    pendingDeleteDao.insert(PendingDeleteEntity(entityType = "recording", entityId = recording.id))
    recordingDao.hardDelete(recording.id)
    localAudioManager.deleteAudio(recording.id)
    try {
        collection().document(recording.id).update(
            mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
        ).await()
        pendingDeleteDao.delete(/* find by entityType + entityId */)
    } catch (_: Exception) { /* SyncWorker will process pending_deletes */ }
}
```

**Note:** `PendingDeleteDao.delete()` takes the auto-generated `id`, so we need a way to find it. Add a query: `@Query("DELETE FROM pending_deletes WHERE entityType = :type AND entityId = :entityId") suspend fun deleteByEntity(type: String, entityId: String)`.

Same pattern for `ActionItemRepository.deleteItem/deleteItems`, `FolderRepository.deleteFolder`, `CollectiveSummaryRepository.deleteSummary`.

### E5. Update `FirestoreSyncService` -- guard against ghost re-insertion

In [FirestoreSyncService.kt](android/app/src/main/java/com/voicemind/data/sync/FirestoreSyncService.kt), inject `PendingDeleteDao`. In each `handle*Change` method, before upserting a new entity (when `existing == null` and `!cloud.isDeleted`), check:

```kotlin
if (pendingDeleteDao.exists("recording", cloud.id)) return
```

This prevents the ghost from being re-inserted into Room after a local delete that hasn't synced to Firestore yet.

Add the check in all four handlers: `handleRecordingChange`, `handleActionItemChange`, `handleFolderChange`, `handleSummaryChange`.

### E6. Update `SyncWorker` -- process pending_deletes

In [SyncWorker.kt](android/app/src/main/java/com/voicemind/data/sync/SyncWorker.kt), inject `PendingDeleteDao`. Add `syncPendingDeletes()` to `doWork()`:

```kotlin
override suspend fun doWork(): Result {
    return try {
        syncPendingRecordings()
        syncPendingActionItems()
        syncPendingFolders()
        syncPendingSummaries()
        syncPendingDeletes()
        Result.success()
    } catch (e: Exception) { ... }
}

private suspend fun syncPendingDeletes() {
    pendingDeleteDao.getAll().forEach { pending ->
        val collectionPath = when (pending.entityType) {
            "recording" -> "recordings"
            "actionItem" -> "actionItems"
            "folder" -> "folders"
            "collectiveSummary" -> "collectiveSummaries"
            else -> return@forEach
        }
        try {
            firestore.document("users/$uid/$collectionPath/${pending.entityId}")
                .update(mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp()))
                .await()
            pendingDeleteDao.delete(pending.id)
        } catch (e: Exception) {
            Timber.w(e, "SyncWorker: pending delete failed for %s/%s", pending.entityType, pending.entityId)
        }
    }
}
```

The worker needs access to the current UID. Inject `AuthRepository` (already available) to get `currentUser?.uid`.

Also remove the `/* Phase 4 */` placeholder comments from the `PENDING_DELETE` branches (these are now handled via the pending_deletes table, not via entity-level `syncStatus`).

---

## F. Sync Status in Settings (Optional)

In [SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt), add a "SYNC" section between INTEGRATIONS and the footer. Show:

- "All synced" with a checkmark when all counts are zero
- "X items pending sync" with a refresh icon when there are pending items

Query counts from the DAOs (`getPendingSync().size` for each + `pendingDeleteDao.getAll().size`). Expose via `SettingsViewModel` or a dedicated flow. Tapping could show a brief `AlertDialog` breakdown. This is low priority and can be deferred.

---

## New Files Summary


| File                                       | Purpose                             |
| ------------------------------------------ | ----------------------------------- |
| `ui/common/OfflineBanner.kt`               | Composable banner for offline state |
| `data/local/entity/PendingDeleteEntity.kt` | Room entity for pending deletes     |
| `data/local/dao/PendingDeleteDao.kt`       | DAO for pending deletes             |


## Modified Files Summary


| File                                             | Changes                                                                                               |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| `data/repository/FolderRepository.kt`            | Fix `pushFolderCloud` timestamp, inject `PendingDeleteDao`, update `deleteFolder`                     |
| `data/model/Recording.kt`                        | Add `syncStatus` field with `@get:Exclude`                                                            |
| `data/local/EntityMappers.kt`                    | Include `syncStatus` in `RecordingEntity.toModel()`                                                   |
| `data/local/AppDatabase.kt`                      | Bump to v2, add `PendingDeleteEntity`, add `pendingDeleteDao()`, add migration                        |
| `di/AppModule.kt`                                | Add migration to database builder, provide `PendingDeleteDao`                                         |
| `data/repository/RecordingRepository.kt`         | Inject `PendingDeleteDao`, update `deleteRecording`                                                   |
| `data/repository/ActionItemRepository.kt`        | Inject `PendingDeleteDao`, update `deleteItem`/`deleteItems`                                          |
| `data/repository/CollectiveSummaryRepository.kt` | Inject `PendingDeleteDao`, update `deleteSummary`                                                     |
| `data/sync/FirestoreSyncService.kt`              | Inject `PendingDeleteDao`, add re-insertion guard in all handlers                                     |
| `data/sync/SyncWorker.kt`                        | Inject `PendingDeleteDao` + `AuthRepository`, add `syncPendingDeletes()`, remove Phase 4 placeholders |
| `ui/navigation/AppNavHost.kt`                    | Add `OfflineBanner` in both Scaffold modes                                                            |
| `ui/recording/RecordingsScreen.kt`               | Add processing status chip to `RecordingRow`, add needs-internet dialog                               |
| `ui/recording/RecordingsViewModel.kt`            | Inject `ConnectivityObserver`, add `NeedsInternetReason`, guard summary/tasks/share                   |
| `ui/recording/RecordingDetailScreen.kt`          | Add needs-internet dialog                                                                             |
| `MainActivity.kt`                                | Add connectivity-triggered sync                                                                       |
| `ui/settings/SettingsScreen.kt`                  | (Optional) Add sync status row                                                                        |


