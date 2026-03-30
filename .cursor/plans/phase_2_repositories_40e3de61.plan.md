---
name: Phase 2 Repositories
overview: Update all 5 Android repository files to replace hard deletes with soft deletes and add `.whereEqualTo("isDeleted", false)` filters to all collection queries. No UI changes — only the repository layer changes.
todos:
  - id: recording-repo
    content: "RecordingRepository: 3 query filters + soft delete (remove audio deletion)"
    status: pending
  - id: action-repo
    content: "ActionItemRepository: 5 query filters + 2 soft delete methods"
    status: pending
  - id: summary-repo
    content: "CollectiveSummaryRepository: 1 query filter + 1 soft delete method"
    status: pending
  - id: folder-repo
    content: "FolderRepository: 2 query filters + 1 soft delete method"
    status: pending
  - id: sharing-repo
    content: "SharingRepository: 4 query filters (isDeleted + ownerItemDeleted)"
    status: pending
isProject: false
---

# Phase 2: Android Repository Layer — Soft Delete + Query Filters

All changes follow the same two patterns:

- **Delete methods**: replace `.delete()` with `.update("isDeleted", true, "deletedAt", serverTimestamp())`; stop deleting audio files
- **Collection queries**: add `.whereEqualTo("isDeleted", false)` before any `.orderBy()` or alongside existing `.whereEqualTo()`

---

## 1. [RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt)

**3 query filters + 1 delete method (the bulk method delegates to it):**

- **Line 27-28** `observeRecordings()`: insert `.whereEqualTo("isDeleted", false)` before `.orderBy("createdAt", ...)`
- **Line 41-43** `observeByFolder()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("folderId", folderId)`
- **Line 111** `reassignFolder()`: change query to `collection().whereEqualTo("folderId", fromFolderId).whereEqualTo("isDeleted", false).get().await()`
- **Lines 78-85** `deleteRecording()`: replace entire body — update with `isDeleted: true` + `deletedAt: serverTimestamp()`. Remove the Storage delete block entirely (audio files are preserved).

```kotlin
suspend fun deleteRecording(recording: Recording) {
    collection().document(recording.id).update(
        mapOf(
            "isDeleted" to true,
            "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    ).await()
}
```

`deleteRecordings()` (line 116-118) delegates to `deleteRecording()` — no change needed.

---

## 2. [ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt)

**5 query filters + 2 delete methods:**

- **Line 26-27** `observeActionItems()`: insert `.whereEqualTo("isDeleted", false)` before `.orderBy("createdAt", ...)`
- **Lines 99-100** `getByRecordingId()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("recordingId", recordingId)`
- **Lines 122-123** `observeActionItemsForRecording()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("recordingId", recordingId)`
- **Lines 170-171** `observeSharedTasks()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereNotEqualTo("sharedFromUid", null)`
- **Lines 163-164** `hasGeneratedTasksForSharedRecording()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("recordingId", ...)`
- **Lines 43-45** `deleteItem()`: replace `.delete()` with `.update(mapOf("isDeleted" to true, "deletedAt" to serverTimestamp()))`
- **Lines 47-51** `deleteItems()`: replace `batch.delete(...)` with `batch.update(..., mapOf("isDeleted" to true, "deletedAt" to serverTimestamp()))`

---

## 3. [CollectiveSummaryRepository.kt](android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt)

**1 query filter + 1 delete method:**

- **Line 26-27** `observeSummaries()`: insert `.whereEqualTo("isDeleted", false)` before `.orderBy("createdAt", ...)`
- **Lines 63-65** `deleteSummary()`: replace `.delete()` with `.update(mapOf("isDeleted" to true, "deletedAt" to serverTimestamp()))`

---

## 4. [FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt)

**2 query filters + 1 delete method:**

- **Line 23-24** `observeFolders()`: insert `.whereEqualTo("isDeleted", false)` before `.orderBy("createdAt", ...)`
- **Line 37** `seedDefaultsIfEmpty()`: change `collection().get()` to `collection().whereEqualTo("isDeleted", false).get()` so it only counts non-deleted folders
- **Lines 68-71** `deleteFolder()`: replace `.delete()` with `.update(mapOf("isDeleted" to true, "deletedAt" to serverTimestamp()))`

---

## 5. [SharingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt)

**4 query filters only (no delete methods — dismiss/revoke are Cloud Functions):**

- **Line 31-32** `observeSharedWithMe()`: insert `.whereEqualTo("isDeleted", false)` AND `.whereEqualTo("ownerItemDeleted", false)` before `.orderBy("sharedAt", ...)`
- **Lines 44-45** `observeMyShares()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("itemId", itemId)`
- **Lines 57-58** `getUnreadCount()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("isRead", false)`
- **Lines 118-119** `getSharedItem()`: insert `.whereEqualTo("isDeleted", false)` alongside `.whereEqualTo("itemId", itemId)`

---

## Summary


| File                        | Query filters added | Delete methods updated |
| --------------------------- | ------------------- | ---------------------- |
| RecordingRepository         | 3                   | 1                      |
| ActionItemRepository        | 5                   | 2                      |
| CollectiveSummaryRepository | 1                   | 1                      |
| FolderRepository            | 2                   | 1                      |
| SharingRepository           | 4                   | 0                      |
| **Total**                   | **15**              | **5**                  |


