---
name: Phase 3 Local-First
overview: Convert ActionItemRepository, FolderRepository, and CollectiveSummaryRepository to local-first (Room primary, Firestore background sync), extend FirestoreSyncService/SyncWorker/InitialSyncManager to cover all entity types, and add necessary DAO query methods.
todos:
  - id: dao-updates
    content: Add missing DAO query methods (updateSyncStatus, field-specific updates) to ActionItemDao, FolderDao, CollectiveSummaryDao
    status: pending
  - id: action-item-repo
    content: "Convert ActionItemRepository to local-first: Room reads, dual-write mutations, Room hard-delete + Firestore soft-delete"
    status: pending
  - id: folder-repo
    content: "Convert FolderRepository to local-first: Room reads, dual-write creates/renames, local-first deletes, seedDefaultsIfEmpty Room-first check"
    status: pending
  - id: summary-repo
    content: "Convert CollectiveSummaryRepository to local-first: Room reads, cloud-function generate with Room insert, local-first deletes"
    status: pending
  - id: sync-service
    content: Extend FirestoreSyncService with listeners for actionItems, folders, collectiveSummaries collections
    status: pending
  - id: sync-worker
    content: Extend SyncWorker to sync pending actionItems, folders, collectiveSummaries to Firestore
    status: pending
  - id: initial-sync
    content: Extend InitialSyncManager to hydrate actionItems, folders, collectiveSummaries from Firestore into Room
    status: pending
isProject: false
---

# Phase 3: Action Items, Folders, Collective Summaries -- Local-First

## Context

Phases 1 and 2 are implemented: Room DB infrastructure exists (entities, DAOs, converters, mappers), and recordings are already local-first with `FirestoreSyncService`, `SyncWorker`, `SyncScheduler`, and `InitialSyncManager` handling recordings only. The three remaining repositories (`ActionItemRepository`, `FolderRepository`, `CollectiveSummaryRepository`) still read/write directly from Firestore snapshot listeners. This phase converts them to the same Room-first + dual-write pattern established in Phase 2.

## Pattern Reference (from RecordingRepository)

The established dual-write pattern in [RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt):

- **Reads**: `recordingDao.observeAll().map { it.map { e -> e.toModel() } }`
- **Writes**: Update Room with `syncStatus = PENDING_UPDATE`, then try Firestore in background; on success mark `SYNCED`; on failure leave pending for SyncWorker
- **Deletes**: Hard-delete from Room + delete local files, best-effort soft-delete in Firestore
- **Cloud functions**: Call cloud, results arrive via `FirestoreSyncService` listener into Room

---

## 1. Add Missing DAO Query Methods

The DAOs from Phase 1 have basic CRUD but lack the per-field update queries needed for the dual-write pattern. Follow the `RecordingDao` pattern (which has `updateTitle`, `updateFolder`, `updateSyncStatus`).

### [ActionItemDao.kt](android/app/src/main/java/com/voicemind/data/local/dao/ActionItemDao.kt)

Add these `@Query` methods:

- `updateSyncStatus(id: String, status: SyncStatus)` -- used after successful Firestore write
- `updateCompleted(id: String, completed: Boolean, syncStatus: SyncStatus)` -- for toggleCompleted
- `updateTitle(id: String, title: String, syncStatus: SyncStatus)` -- for updateTitle
- `updateDueDate(id: String, dueDate: Long?, syncStatus: SyncStatus)` -- for updateDueDate
- `updateDeadline(id: String, deadline: Long?, syncStatus: SyncStatus)` -- for updateDeadline
- `updateNotes(id: String, notes: String?, syncStatus: SyncStatus)` -- for updateNotes
- `getByRecordingId(recordingId: String): List<ActionItemEntity>` -- suspend query for sync

### [FolderDao.kt](android/app/src/main/java/com/voicemind/data/local/dao/FolderDao.kt)

Add:

- `updateSyncStatus(id: String, status: SyncStatus)`
- `updateName(id: String, name: String, syncStatus: SyncStatus)` -- for renameFolder
- `getCount(): Int` -- to check if Room is empty for seedDefaultsIfEmpty

### [CollectiveSummaryDao.kt](android/app/src/main/java/com/voicemind/data/local/dao/CollectiveSummaryDao.kt)

Add:

- `updateSyncStatus(id: String, status: SyncStatus)`

---

## 2. Convert ActionItemRepository to Local-First

File: [ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt)

**Inject** `ActionItemDao` and `SyncScheduler` in constructor (alongside existing `firestore`, `authRepository`, `functions`).

### Reads -- switch to Room


| Method                   | Before                 | After                                                              |
| ------------------------ | ---------------------- | ------------------------------------------------------------------ |
| `observeActionItems()`   | Firestore callbackFlow | `actionItemDao.observeAll().map { it.map { e -> e.toModel() } }`   |
| `observeByRecordingId()` | Firestore callbackFlow | `actionItemDao.observeByRecordingId(recordingId).map { ... }`      |
| `observeActionItem()`    | Firestore callbackFlow | `actionItemDao.observeById(itemId).map { it?.toModel() }`          |
| `observeSharedTasks()`   | Firestore callbackFlow | `actionItemDao.observeSharedTasks().map { ... }`                   |
| `getActionItem()`        | Firestore get          | `actionItemDao.getById(itemId)?.toModel()`                         |
| `getByRecordingId()`     | Firestore query        | `actionItemDao.getByRecordingId(recordingId).map { it.toModel() }` |


### Writes -- dual-write (Room first, Firestore background)

- `**createItem(title)`**: Generate doc ID via `collection().document().id`, create `ActionItemEntity` with `syncStatus = PENDING_UPLOAD`, upsert to Room, then try Firestore set; on success mark `SYNCED`; on failure enqueue SyncWorker
- `**toggleCompleted(itemId, completed)**`: `actionItemDao.updateCompleted(id, completed, PENDING_UPDATE)`, then try Firestore update; on success `SYNCED`
- `**updateTitle(itemId, title)**`: Same pattern with `actionItemDao.updateTitle`
- `**updateDueDate(itemId, dueDate)**`: Same pattern with `actionItemDao.updateDueDate` (convert `Timestamp?` to `Long?` via `toEpochMillis()`)
- `**updateDeadline(itemId, deadline)**`: Same pattern
- `**updateNotes(itemId, notes)**`: Same pattern

### Deletes -- Room hard-delete + Firestore soft-delete

- `**deleteItem(itemId)**`: `actionItemDao.hardDelete(itemId)`, then try Firestore soft-delete; on failure the item is already gone locally
- `**deleteItems(itemIds)**`: Loop through `hardDelete` + batch Firestore soft-delete
- `**markCompleted(itemIds, completed)**`: Loop Room updates + batch Firestore updates

### Keep unchanged (cross-user / cloud-function calls)

- `retryExtractActionItems()` -- cloud function, new items arrive via FirestoreSyncService
- `observeActionItemsForRecording(ownerUid, recordingId)` -- cross-user read, stays Firestore
- `addSharedTask()` -- writes to Firestore, picked up by listener into Room
- `isSharedTaskAdded()` -- cross-user check, stays Firestore
- `hasGeneratedTasksForSharedRecording()` -- cross-user check, stays Firestore

### New: SyncWorker support method

- `pushActionItemCloud(entity: ActionItemEntity)` -- Firestore create for PENDING_UPLOAD items (used by SyncWorker)
- `pushActionItemUpdate(entity: ActionItemEntity)` -- push changed fields for PENDING_UPDATE items

---

## 3. Convert FolderRepository to Local-First

File: [FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt)

**Inject** `FolderDao` and `SyncScheduler` in constructor.

### Reads

- `observeFolders()`: `folderDao.observeAll().map { it.map { e -> e.toModel() } }`

### Writes

- `**createFolder(name)`**: Generate doc ID, create `FolderEntity` with `PENDING_UPLOAD`, upsert to Room, try Firestore; return ID
- `**renameFolder(folderId, newName)**`: Guard `UNFILED_ID`, update Room via `folderDao.updateName(id, name, PENDING_UPDATE)`, try Firestore
- `**deleteFolder(folderId)**`: Guard `UNFILED_ID`, hard-delete from Room, try Firestore soft-delete

### seedDefaultsIfEmpty

- Check Room first via `folderDao.getCount()`. If Room has folders, return early (normal case after initial sync).
- If Room is empty, check Firestore. If Firestore is also empty, seed defaults into both Room and Firestore.
- If Room is empty but Firestore has folders, `InitialSyncManager` handles hydration.

### New: SyncWorker support

- `pushFolderCloud(entity: FolderEntity)` -- Firestore create
- `pushFolderUpdate(entity: FolderEntity)` -- push name change

---

## 4. Convert CollectiveSummaryRepository to Local-First

File: [CollectiveSummaryRepository.kt](android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt)

**Inject** `CollectiveSummaryDao` in constructor.

### Reads

- `observeSummaries()`: `collectiveSummaryDao.observeAll().map { ... }`
- `getSummary(summaryId)`: `collectiveSummaryDao.getById(summaryId)?.toModel()`

### Cloud function

- `generateCollectiveSummary(recordingIds)`: Still calls cloud function. After the function returns a `summaryId`, poll Room briefly for the summary (since `FirestoreSyncService` will insert it). If not in Room yet, fall back to Firestore get and insert into Room manually.

### Deletes

- `deleteSummary(summaryId)`: Hard-delete from Room, try Firestore soft-delete

### Keep unchanged

- `getSharedSummary()`, `observeSharedSummary()`, `duplicateSharedSummary()` -- cross-user reads, stay Firestore

---

## 5. Extend FirestoreSyncService for All Collections

File: [FirestoreSyncService.kt](android/app/src/main/java/com/voicemind/data/sync/FirestoreSyncService.kt)

**Inject** `ActionItemDao`, `FolderDao`, `CollectiveSummaryDao` in constructor (alongside existing `RecordingDao`, `LocalAudioManager`).

Add three new `ListenerRegistration` fields: `actionItemsListener`, `foldersListener`, `summariesListener`.

### startListening(uid) -- add three listeners

Each listener follows the same pattern as the recordings listener:

**actionItems listener** (`users/{uid}/actionItems`):

- ADDED/MODIFIED: Deserialize to `ActionItem`, check `isDeleted` (if true, hard-delete from Room), otherwise merge with existing entity. Cloud-owned fields (`googleTaskId`, `calendarEventId`, `autoScheduled`) always overwrite. User-owned fields (`title`, `completed`, `notes`, `dueDate`, `deadline`) only overwrite if local `syncStatus == SYNCED`.
- REMOVED: Hard-delete from Room.

**folders listener** (`users/{uid}/folders`):

- ADDED/MODIFIED: Deserialize to `Folder`, check `isDeleted`, upsert into Room. User-owned field (`name`) only overwrite if local `syncStatus == SYNCED`.
- REMOVED: Hard-delete from Room.

**summaries listener** (`users/{uid}/collectiveSummaries`):

- ADDED/MODIFIED: Deserialize to `CollectiveSummary`, check `isDeleted`, upsert into Room. All fields are cloud-owned (summaries are generated by cloud function), so always overwrite.
- REMOVED: Hard-delete from Room.

### stopListening() -- remove all four listeners

---

## 6. Extend SyncWorker for All Entity Types

File: [SyncWorker.kt](android/app/src/main/java/com/voicemind/data/sync/SyncWorker.kt)

**Inject** `ActionItemDao`, `FolderDao`, `CollectiveSummaryDao`, `ActionItemRepository`, `FolderRepository`, `CollectiveSummaryRepository`.

Add `syncPendingActionItems()`, `syncPendingFolders()`, `syncPendingSummaries()` -- same pattern as `syncPendingRecordings()`:

```
doWork():
  syncPendingRecordings()
  syncPendingActionItems()
  syncPendingFolders()
  syncPendingSummaries()
```

For each pending entity:

- `PENDING_UPLOAD`: Push to Firestore via repository cloud-create method, mark `SYNCED`
- `PENDING_UPDATE`: Push changed fields to Firestore via repository push-update method, mark `SYNCED`
- `PENDING_DELETE`: `/* Phase 4 */` (same as recordings)

---

## 7. Extend InitialSyncManager for All Entity Types

File: [InitialSyncManager.kt](android/app/src/main/java/com/voicemind/data/sync/InitialSyncManager.kt)

**Inject** `ActionItemDao`, `FolderDao`, `CollectiveSummaryDao`.

In `runIfNeeded()`, after hydrating recordings, also hydrate:

- `actionItems` collection -> `ActionItemEntity` list -> `actionItemDao.upsertAll()`
- `folders` collection -> `FolderEntity` list -> `folderDao.upsertAll()`
- `collectiveSummaries` collection -> `CollectiveSummaryEntity` list -> `collectiveSummaryDao.upsertAll()`

Use the existing `toEntity(SyncStatus.SYNCED)` mapper functions from `EntityMappers.kt`.

---

## Files Changed Summary

**Modified (7 files):**

- `data/local/dao/ActionItemDao.kt` -- add update queries
- `data/local/dao/FolderDao.kt` -- add update queries
- `data/local/dao/CollectiveSummaryDao.kt` -- add updateSyncStatus
- `data/repository/ActionItemRepository.kt` -- Room-first reads + dual-write
- `data/repository/FolderRepository.kt` -- Room-first reads + dual-write
- `data/repository/CollectiveSummaryRepository.kt` -- Room-first reads + dual-write
- `data/sync/FirestoreSyncService.kt` -- add 3 collection listeners
- `data/sync/SyncWorker.kt` -- add sync for 3 entity types
- `data/sync/InitialSyncManager.kt` -- hydrate 3 additional collections

**No new files created.** All changes are modifications to existing files.

**No ViewModel changes required.** The repositories maintain the same public API signatures (`Flow<List<T>>`, `suspend fun`, etc.), so ViewModels continue to work without modification.