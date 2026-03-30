---
name: Soft Delete PRD
overview: A comprehensive Product Requirements Document for implementing soft delete across all entities in VoiceMind — recordings, action items, collective summaries, folders, and share relationships. Every delete operation will mark items as deleted rather than removing them, enabling admin recovery at any time. Account deletion remains a hard delete for GDPR compliance.
todos:
  - id: phase-1-models
    content: "Phase 1: Add isDeleted/deletedAt fields to all 6 Android data models + ownerItemDeleted on SharedItem"
    status: completed
  - id: phase-1-migration
    content: "Phase 1: Write and run migration Cloud Function to backfill isDeleted: false on all existing documents"
    status: in_progress
  - id: phase-1-indexes
    content: "Phase 1: Create all required composite Firestore indexes (10 indexes across 6 collections)"
    status: pending
  - id: phase-2-recording-repo
    content: "Phase 2: Update RecordingRepository — soft delete methods + isDeleted query filters on all collection queries"
    status: pending
  - id: phase-2-action-repo
    content: "Phase 2: Update ActionItemRepository — soft delete methods + isDeleted query filters"
    status: pending
  - id: phase-2-summary-repo
    content: "Phase 2: Update CollectiveSummaryRepository — soft delete method + query filter"
    status: pending
  - id: phase-2-folder-repo
    content: "Phase 2: Update FolderRepository — soft delete method + query filters"
    status: pending
  - id: phase-2-sharing-repo
    content: "Phase 2: Update SharingRepository — add isDeleted + ownerItemDeleted filters to all queries"
    status: pending
  - id: phase-3-recording-trigger
    content: "Phase 3: Replace onRecordingDeleted with onRecordingSoftDeleted (onDocumentUpdated) in sharing.ts"
    status: pending
  - id: phase-3-summary-trigger
    content: "Phase 3: Replace onCollectiveSummaryDeleted with onCollectiveSummarySoftDeleted in sharing.ts"
    status: pending
  - id: phase-3-revoke-dismiss
    content: "Phase 3: Update revokeShare and dismissSharedItem to soft-delete share docs instead of hard delete"
    status: pending
  - id: phase-3-google-tasks
    content: "Phase 3: Update googleTasks.ts action item trigger to handle soft delete instead of onDocumentDeleted"
    status: pending
  - id: phase-4-ui-rename
    content: "Phase 4: Rename TaskDetailUiState.isDeleted to isNavigatingAway to avoid field name collision"
    status: pending
  - id: phase-5-deploy-test
    content: "Phase 5: Deploy migration, Cloud Functions, Android app — end-to-end verification"
    status: pending
isProject: false
---

# Soft Delete Feature — Product Requirements Document (PRD)

## 1. Overview

The **Soft Delete** feature replaces all hard delete operations in VoiceMind with soft deletes. Instead of permanently removing documents from Firestore and files from Cloud Storage, the system marks items as deleted using metadata fields. The data remains in the database, invisible to users, but recoverable by an administrator at any time.

This feature covers:

- **All user-facing entities**: recordings, action items (tasks), collective summaries, folders
- **Share relationship documents**: `sharedWithMe` and `myShares` entries
- **Audio files in Cloud Storage**: retained during soft delete (not deleted)
- **Interaction with sharing**: soft-deleted items hidden from recipients, restorable
- **One exception**: account deletion remains a hard delete for GDPR compliance

---

## 2. Core Principles

1. **No user data is ever permanently lost** (except on account deletion)
2. **Soft-deleted items are invisible to users** — they behave as if deleted from the user's perspective
3. **All queries filter out soft-deleted items** — no UI changes needed beyond filtering
4. **Audio files are preserved** — the most irreplaceable data is never destroyed
5. **Recovery is admin-only** — no user-facing Trash screen; restoration is done via Firebase Console
6. **Retention is indefinite** — soft-deleted items are never auto-purged
7. **Account deletion is the only hard delete** — GDPR compliance requires permanent removal

---

## 3. What is Soft Delete

### 3.1 Hard Delete (Current Behavior)

```kotlin
// Current: document is permanently removed
collection().document(recording.id).delete().await()
storage.reference.child(recording.audioPath).delete().await()
```

The document and audio file are gone forever. No recovery possible.

### 3.2 Soft Delete (New Behavior)

```kotlin
// New: document is marked as deleted but stays in the database
collection().document(recording.id).update(
    mapOf(
        "isDeleted" to true,
        "deletedAt" to FieldValue.serverTimestamp()
    )
).await()
// Audio file is NOT deleted
```

The document still exists. All queries add `.whereEqualTo("isDeleted", false)` to hide it.

---

## 4. Scope — Entities Covered


| Entity                 | Collection Path                             | Currently Hard Deleted?            | Soft Delete?                             |
| ---------------------- | ------------------------------------------- | ---------------------------------- | ---------------------------------------- |
| Recording              | `users/{uid}/recordings/{id}`               | Yes                                | Yes                                      |
| Action Item (Task)     | `users/{uid}/actionItems/{id}`              | Yes                                | Yes                                      |
| Collective Summary     | `users/{uid}/collectiveSummaries/{id}`      | Yes                                | Yes                                      |
| Folder                 | `users/{uid}/folders/{id}`                  | Yes                                | Yes                                      |
| Shared With Me (inbox) | `users/{uid}/sharedWithMe/{shareId}`        | Yes (dismiss/revoke)               | Yes                                      |
| My Shares (outbox)     | `users/{uid}/myShares/{shareId}`            | Yes (dismiss/revoke)               | Yes                                      |
| User Account           | Firebase Auth + all subcollections          | Yes                                | **No — stays hard delete (GDPR)**        |
| Audio Files (Storage)  | `users/{uid}/audio/{id}.m4a`                | Yes (on recording delete)          | **Retained — not deleted**               |
| Device Tokens          | `users/{uid}/deviceTokens/{id}`             | Yes (invalid FCM tokens)           | **No — these are ephemeral system data** |
| Rate Limit Docs        | `rateLimits/{uid}`                          | Yes (on account delete)            | **No — system data**                     |
| OAuth Tokens           | `tasksTokens/{uid}`, `calendarTokens/{uid}` | Yes (on disconnect/account delete) | **No — security-sensitive**              |


---

## 5. Data Model Changes

### 5.1 New Fields on All Soft-Deletable Entities

Every soft-deletable entity gains two new fields:


| Field       | Type         | Default | Notes                                              |
| ----------- | ------------ | ------- | -------------------------------------------------- |
| `isDeleted` | `Boolean`    | `false` | `true` when the item is soft-deleted               |
| `deletedAt` | `Timestamp?` | `null`  | Server timestamp of when the item was soft-deleted |


### 5.2 Android Data Model Updates

**[Recording.kt](android/app/src/main/java/com/voicemind/data/model/Recording.kt)** — add two fields:

```kotlin
data class Recording(
    @DocumentId val id: String = "",
    val title: String = "",
    val folderId: String = "unfiled",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val transcription: String? = null,
    val summary: String? = null,
    val audioPath: String = "",
    val durationSeconds: Long = 0,
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)
```

**[ActionItem.kt](android/app/src/main/java/com/voicemind/data/model/ActionItem.kt)** — add two fields:

```kotlin
data class ActionItem(
    // ... existing fields ...
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)
```

**[CollectiveSummary.kt](android/app/src/main/java/com/voicemind/data/model/CollectiveSummary.kt)** — add two fields:

```kotlin
data class CollectiveSummary(
    // ... existing fields ...
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)
```

**[Folder.kt](android/app/src/main/java/com/voicemind/data/model/Folder.kt)** — add two fields:

```kotlin
data class Folder(
    // ... existing fields ...
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)
```

**SharedItem and MyShare** — add two fields each (for dismiss/revoke soft delete):

```kotlin
// SharedItem.kt
data class SharedItem(
    // ... existing fields ...
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)

// MyShare.kt
data class MyShare(
    // ... existing fields ...
    val isDeleted: Boolean = false,        // NEW
    val deletedAt: Timestamp? = null,      // NEW
)
```

### 5.3 Existing Documents (Migration)

Existing documents in Firestore will NOT have the `isDeleted` field. This is safe because:

- Firestore `whereEqualTo("isDeleted", false)` will match documents where the field is `false` but will **not match** documents where the field is absent
- To handle this, we use `whereIn("isDeleted", listOf(false))` or add a Cloud Function migration to backfill `isDeleted: false` on all existing documents

**Recommended approach**: Write a one-time migration Cloud Function that adds `isDeleted: false` to every existing document across all affected collections. This is the safest option because `whereEqualTo("isDeleted", false)` in Firestore does NOT match documents where the field is missing.

---

## 6. Delete Behavior Per Entity

### 6.1 Recordings

**Current behavior** ([RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt) lines 78-84):

- Firestore document hard-deleted
- Audio file hard-deleted from Cloud Storage

**New behavior**:

- Firestore document updated: `isDeleted = true`, `deletedAt = serverTimestamp()`
- Audio file **NOT deleted** from Cloud Storage
- Cloud Function trigger detects the soft delete and hides the item from all recipients (see Section 8)

**Restore** (admin-only): Set `isDeleted = false`, `deletedAt = null` on the recording document in Firebase Console. Trigger detects restoration and unhides from recipients.

### 6.2 Action Items (Tasks)

**Current behavior** ([ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt) lines 43-50):

- Single: `document(itemId).delete().await()`
- Bulk: `batch.delete(document(id))`

**New behavior**:

- Single: `document(itemId).update("isDeleted", true, "deletedAt", serverTimestamp())`
- Bulk: `batch.update(document(id), "isDeleted", true, "deletedAt", serverTimestamp())`
- If the task has `googleTaskId` or `calendarEventId`, the existing `onDocumentUpdated` trigger in `googleTasks.ts` should detect the soft delete and remove the linked Google Task / Calendar event (this is intentional — external integrations should reflect the delete immediately, but the VoiceMind data stays)

### 6.3 Collective Summaries

**Current behavior** ([CollectiveSummaryRepository.kt](android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt) line 63):

- `document(summaryId).delete().await()`
- Triggers `onCollectiveSummaryDeleted` in sharing.ts

**New behavior**:

- `document(summaryId).update("isDeleted", true, "deletedAt", serverTimestamp())`
- The `onCollectiveSummaryDeleted` trigger is replaced by `onCollectiveSummaryUpdated` that checks if `isDeleted` changed to `true`

### 6.4 Folders

**Current behavior** ([FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt) lines 68-70):

- Recordings are reassigned to "unfiled" first (in FoldersViewModel)
- Then `document(folderId).delete().await()`

**New behavior**:

- Recordings are still reassigned to "unfiled" (this prevents orphaned recordings in a deleted folder)
- Then `document(folderId).update("isDeleted", true, "deletedAt", serverTimestamp())`

### 6.5 Shared Items (sharedWithMe / myShares)

**Current behavior** — `dismissSharedItem` and `revokeShare` in [sharing.ts](functions/src/sharing.ts):

- Both hard-delete `sharedWithMe/{shareId}` and `myShares/{shareId}` docs
- Both remove the recipient UID from the item's `sharedWith` array

**New behavior**:

- Both set `isDeleted: true`, `deletedAt: serverTimestamp()` on `sharedWithMe/{shareId}` and `myShares/{shareId}`
- The recipient UID is **NOT removed** from the item's `sharedWith` array (preserves the relationship for potential restore)
- The recipient's `observeSharedWithMe()` query filters `isDeleted == false`, so the item disappears from their UI
- The owner's `observeMyShares()` query filters `isDeleted == false`

---

## 7. Query Filtering

Every Firestore query that lists or observes entities must add an `isDeleted == false` filter. Below is the exhaustive list.

### 7.1 RecordingRepository


| Method                      | Current Query                            | New Filter                              |
| --------------------------- | ---------------------------------------- | --------------------------------------- |
| `observeRecordings()`       | `orderBy("createdAt", DESC)`             | Add `.whereEqualTo("isDeleted", false)` |
| `observeByFolder(folderId)` | `whereEqualTo("folderId", folderId)`     | Add `.whereEqualTo("isDeleted", false)` |
| `reassignFolder()`          | `whereEqualTo("folderId", fromFolderId)` | Add `.whereEqualTo("isDeleted", false)` |


Single-document reads (`getRecording`, `getSharedRecording`, `observeSharedRecording`) do NOT need the filter since they fetch by known ID. However, the consuming UI/ViewModel should check `isDeleted` before displaying.

### 7.2 ActionItemRepository


| Method                                                  | Current Query                              | New Filter                              |
| ------------------------------------------------------- | ------------------------------------------ | --------------------------------------- |
| `observeActionItems()`                                  | `orderBy("createdAt", DESC)`               | Add `.whereEqualTo("isDeleted", false)` |
| `getByRecordingId(recordingId)`                         | `whereEqualTo("recordingId", recordingId)` | Add `.whereEqualTo("isDeleted", false)` |
| `observeActionItemsForRecording(ownerUid, recordingId)` | `whereEqualTo("recordingId", recordingId)` | Add `.whereEqualTo("isDeleted", false)` |
| `observeSharedTasks()`                                  | `whereNotEqualTo("sharedFromUid", null)`   | Add `.whereEqualTo("isDeleted", false)` |
| `hasGeneratedTasksForSharedRecording()`                 | `whereEqualTo("recordingId", syntheticId)` | Add `.whereEqualTo("isDeleted", false)` |


Note: `observeActionItem(itemId)` is a single-doc listener — no collection filter needed, but the ViewModel should check `isDeleted`.

### 7.3 CollectiveSummaryRepository


| Method               | Current Query                | New Filter                              |
| -------------------- | ---------------------------- | --------------------------------------- |
| `observeSummaries()` | `orderBy("createdAt", DESC)` | Add `.whereEqualTo("isDeleted", false)` |


### 7.4 FolderRepository


| Method                  | Current Query               | New Filter                              |
| ----------------------- | --------------------------- | --------------------------------------- |
| `observeFolders()`      | `orderBy("createdAt", ASC)` | Add `.whereEqualTo("isDeleted", false)` |
| `seedDefaultsIfEmpty()` | `collection().get()`        | Add `.whereEqualTo("isDeleted", false)` |


### 7.5 SharingRepository


| Method                    | Current Query                    | New Filter                              |
| ------------------------- | -------------------------------- | --------------------------------------- |
| `observeSharedWithMe()`   | `orderBy("sharedAt", DESC)`      | Add `.whereEqualTo("isDeleted", false)` |
| `observeMyShares(itemId)` | `whereEqualTo("itemId", itemId)` | Add `.whereEqualTo("isDeleted", false)` |
| `getUnreadCount()`        | `whereEqualTo("isRead", false)`  | Add `.whereEqualTo("isDeleted", false)` |
| `getSharedItem(itemId)`   | `whereEqualTo("itemId", itemId)` | Add `.whereEqualTo("isDeleted", false)` |


---

## 8. Interaction with Sharing

### 8.1 Owner Soft-Deletes a Shared Recording

When the owner soft-deletes a recording that has been shared:

1. The recording document gets `isDeleted: true`, `deletedAt: timestamp`
2. A Cloud Function trigger (`onRecordingUpdated`) detects `isDeleted` changed to `true`
3. The trigger sets `ownerItemDeleted: true` on every matching `sharedWithMe` entry for this recording
4. The recipient's `observeSharedWithMe()` query includes a filter for `ownerItemDeleted != true` (or the client filters this field)
5. The item disappears from the recipient's Shared Items list

**Why not just delete the share references?** Because if the admin restores the recording later, the share relationships should automatically be restored too. By keeping the share docs intact and using a flag, restoration is as simple as flipping `isDeleted` back to `false`.

### 8.2 Owner Soft-Deletes a Shared Collective Summary

Same pattern as 8.1 but with `onCollectiveSummaryUpdated`.

### 8.3 Admin Restores a Soft-Deleted Shared Item

1. Admin sets `isDeleted: false`, `deletedAt: null` on the recording/summary in Firebase Console
2. The same `onRecordingUpdated` / `onCollectiveSummaryUpdated` trigger detects `isDeleted` changed back to `false`
3. The trigger sets `ownerItemDeleted: false` on all matching `sharedWithMe` entries
4. The item reappears in all recipients' Shared Items lists

### 8.4 New Field on SharedItem

Add `ownerItemDeleted: Boolean = false` to the `SharedItem` data model. This field is managed exclusively by Cloud Functions — clients never write it.

### 8.5 Recipient Dismisses or Owner Revokes

These now soft-delete the share relationship itself:

- `sharedWithMe/{shareId}` → `isDeleted: true`, `deletedAt: timestamp`
- `myShares/{shareId}` → `isDeleted: true`, `deletedAt: timestamp`
- The `sharedWith` array on the underlying item is **NOT modified** (preserves relationship for potential restore)

---

## 9. Cloud Function Changes

### 9.1 Summary of Trigger Changes


| Current                                          | New                                                  | Reason                                                             |
| ------------------------------------------------ | ---------------------------------------------------- | ------------------------------------------------------------------ |
| `onRecordingDeleted` (onDocumentDeleted)         | `onRecordingSoftDeleted` (onDocumentUpdated)         | Document is no longer deleted; it's updated with `isDeleted: true` |
| `onCollectiveSummaryDeleted` (onDocumentDeleted) | `onCollectiveSummarySoftDeleted` (onDocumentUpdated) | Same reason                                                        |


### 9.2 `onRecordingSoftDeleted` (replaces `onRecordingDeleted`)

**File**: [sharing.ts](functions/src/sharing.ts)

- **Trigger**: `onDocumentUpdated({ document: "users/{uid}/recordings/{recordingId}" })`
- **Guard**: Only runs if `isDeleted` changed (check `before.data().isDeleted !== after.data().isDeleted`)
- **If `isDeleted` changed to `true`** (soft delete):
  1. Query `myShares` for this recording
  2. For each share entry: set `ownerItemDeleted: true` on the recipient's `sharedWithMe/{shareId}`
  3. Remove `sharedWith` field from linked action items (same as current behavior — using `FieldValue.delete()`)
- **If `isDeleted` changed to `false`** (restore):
  1. Query `myShares` for this recording (these are not deleted — they were preserved)
  2. For each share entry: set `ownerItemDeleted: false` on the recipient's `sharedWithMe/{shareId}`
  3. Re-add recipient UIDs to `sharedWith` on linked action items (reverse of the delete operation)

### 9.3 `onCollectiveSummarySoftDeleted` (replaces `onCollectiveSummaryDeleted`)

- **Trigger**: `onDocumentUpdated({ document: "users/{uid}/collectiveSummaries/{summaryId}" })`
- **Guard**: Only runs if `isDeleted` changed
- **If `isDeleted` changed to `true`**: Set `ownerItemDeleted: true` on all matching `sharedWithMe` entries
- **If `isDeleted` changed to `false`**: Set `ownerItemDeleted: false` on all matching `sharedWithMe` entries

### 9.4 `revokeShare` Changes

**File**: [sharing.ts](functions/src/sharing.ts) lines 228-261

**Current**: `batch.delete(sharedWithMe)`, `batch.delete(myShareRef)`, `arrayRemove` on item

**New**:

- `batch.update(sharedWithMe, { isDeleted: true, deletedAt: serverTimestamp() })`
- `batch.update(myShareRef, { isDeleted: true, deletedAt: serverTimestamp() })`
- **DO NOT** `arrayRemove` on the item's `sharedWith` (preserve for restore)

### 9.5 `dismissSharedItem` Changes

**File**: [sharing.ts](functions/src/sharing.ts) lines 265-299

**Current**: `batch.delete(inboxRef)`, `batch.delete(myShares)`, `arrayRemove` on item

**New**:

- `batch.update(inboxRef, { isDeleted: true, deletedAt: serverTimestamp() })`
- `batch.update(mySharesRef, { isDeleted: true, deletedAt: serverTimestamp() })`
- **DO NOT** `arrayRemove` on the item's `sharedWith`

### 9.6 `duplicateSharedRecording` Rollback

**File**: [sharing.ts](functions/src/sharing.ts) lines 359-435

Rollback operations (on copy failure) currently hard-delete the partially created recording doc, audio file, and copied action items. These rollback deletes should **remain as hard deletes** — they are cleaning up a failed operation, not user-initiated deletes. Partially created data that never successfully existed should not be preserved.

### 9.7 FCM Invalid Token Cleanup

**File**: [sharing.ts](functions/src/sharing.ts) line 195

Cleanup of invalid FCM device tokens should **remain as hard delete**. These are ephemeral system records, not user data.

### 9.8 `onUserDeleted` (Auth Trigger)

**File**: [userProfile.ts](functions/src/userProfile.ts) lines 44-127

**NO CHANGES.** Account deletion remains a hard delete per GDPR. The existing cascade (Phases A through E) stays exactly as-is: all subcollections are hard-deleted, all storage files are permanently removed, all cross-user share references are cleaned up.

### 9.9 Google Tasks Integration

**File**: `googleTasks.ts`

The `onDocumentDeleted` trigger for action items (which removes linked Google Tasks / Calendar events) needs to change to `onDocumentUpdated`. When `isDeleted` changes to `true`, it should still delete the linked Google Task and Calendar event from Google's APIs (these external resources should reflect the delete immediately). The `googleTaskId` and `calendarEventId` fields should be cleared from the action item doc using `FieldValue.delete()` — this way, if the item is later restored, it won't try to reference stale external IDs.

---

## 10. Firestore Indexes

Firestore requires composite indexes for queries that combine `whereEqualTo` with `orderBy` on different fields. The following composite indexes must be created:


| Collection                        | Fields                                                       | Query Direction             |
| --------------------------------- | ------------------------------------------------------------ | --------------------------- |
| `users/{uid}/recordings`          | `isDeleted` (ASC), `createdAt` (DESC)                        | For `observeRecordings()`   |
| `users/{uid}/recordings`          | `isDeleted` (ASC), `folderId` (ASC), `createdAt` (DESC)      | For `observeByFolder()`     |
| `users/{uid}/actionItems`         | `isDeleted` (ASC), `createdAt` (DESC)                        | For `observeActionItems()`  |
| `users/{uid}/actionItems`         | `isDeleted` (ASC), `recordingId` (ASC)                       | For `getByRecordingId()`    |
| `users/{uid}/actionItems`         | `isDeleted` (ASC), `sharedFromUid` (ASC), `createdAt` (DESC) | For `observeSharedTasks()`  |
| `users/{uid}/collectiveSummaries` | `isDeleted` (ASC), `createdAt` (DESC)                        | For `observeSummaries()`    |
| `users/{uid}/folders`             | `isDeleted` (ASC), `createdAt` (ASC)                         | For `observeFolders()`      |
| `users/{uid}/sharedWithMe`        | `isDeleted` (ASC), `sharedAt` (DESC)                         | For `observeSharedWithMe()` |
| `users/{uid}/sharedWithMe`        | `isDeleted` (ASC), `isRead` (ASC)                            | For `getUnreadCount()`      |
| `users/{uid}/myShares`            | `isDeleted` (ASC), `itemId` (ASC)                            | For `observeMyShares()`     |


These can be created either manually in Firebase Console or by running the affected queries and clicking the auto-generated index creation links in the error messages.

---

## 11. Firestore Security Rules

No significant changes needed. The existing rules allow users to read/write their own subcollections. The `isDeleted` field is written by the client on the user's own documents, which is already permitted.

One optional enhancement: prevent clients from setting `isDeleted: false` (i.e., self-restoring). This would ensure only admins can restore items. However, this adds complexity to the rules and is low-priority since there is no UI for restoration.

---

## 12. Account Deletion (Exception)

Account deletion in [userProfile.ts](functions/src/userProfile.ts) (`onUserDeleted`) **remains a hard delete**. When a user deletes their Firebase Auth account:

- All Firestore subcollections are permanently deleted (recordings, folders, actionItems, collectiveSummaries, sharedWithMe, myShares, ntsCounters, deviceTokens)
- All root-level documents are deleted (`users/{uid}`, `tasksTokens/{uid}`, `rateLimits/{uid}`, `calendarTokens/{uid}`)
- All Cloud Storage files under `users/{uid}/` are permanently deleted
- All cross-user share references (inbox entries in other users' `sharedWithMe`) are cleaned up

This is required for GDPR / privacy law compliance: when a user requests account deletion, all their personal data must be permanently removed.

---

## 13. Audio File Handling

### 13.1 During Soft Delete

When a recording is soft-deleted, the audio file in Cloud Storage is **NOT deleted**. The audio file path (`audioPath` field) remains on the recording document, ensuring the audio can be played if the recording is ever restored.

### 13.2 During Account Deletion

Audio files are permanently deleted as part of the `onUserDeleted` cascade (Phase E in [userProfile.ts](functions/src/userProfile.ts)).

### 13.3 Storage Cost Implications

Since audio files are retained indefinitely, storage costs will grow over time. This is an acceptable trade-off for data safety. If storage costs become a concern in the future, a scheduled cleanup function can be added to permanently delete audio for recordings that have been soft-deleted for longer than a configured period (e.g., 1 year).

---

## 14. Admin Restoration Procedure

Since there is no user-facing Trash UI, restoration is performed by an administrator via Firebase Console.

### 14.1 Restoring a Single Item

1. Navigate to `Firestore Database > users/{uid}/{collection}/{docId}`
2. Set `isDeleted` to `false`
3. Set `deletedAt` to `null` (or delete the field)
4. If the item was shared, the `onDocumentUpdated` trigger will automatically unhide it for recipients

### 14.2 Restoring a Recording

Same as 14.1, but the admin should also verify that:

- The audio file still exists in Cloud Storage at the path stored in `audioPath`
- Any linked action items that should also be restored are restored separately

### 14.3 Restoring a Shared Item Relationship

1. Navigate to the recipient's `sharedWithMe/{shareId}` document
2. Set `isDeleted` to `false`, `deletedAt` to `null`
3. Navigate to the owner's `myShares/{shareId}` document
4. Set `isDeleted` to `false`, `deletedAt` to `null`

---

## 15. Android Layer Changes (Overview)

### 15.1 Data Model Changes (6 files)


| File                                                                                            | Change                                                                                                |
| ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| [Recording.kt](android/app/src/main/java/com/voicemind/data/model/Recording.kt)                 | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`                                      |
| [ActionItem.kt](android/app/src/main/java/com/voicemind/data/model/ActionItem.kt)               | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`                                      |
| [CollectiveSummary.kt](android/app/src/main/java/com/voicemind/data/model/CollectiveSummary.kt) | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`                                      |
| Folder.kt                                                                                       | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`                                      |
| SharedItem.kt                                                                                   | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`, `ownerItemDeleted: Boolean = false` |
| MyShare.kt                                                                                      | Add `isDeleted: Boolean = false`, `deletedAt: Timestamp? = null`                                      |


### 15.2 Repository Changes (5 files)


| File                                                                                                                     | Changes                                                                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt)                 | `deleteRecording()` and `deleteRecordings()` → update `isDeleted`/`deletedAt` instead of `.delete()`; remove Storage delete; add `.whereEqualTo("isDeleted", false)` to `observeRecordings()`, `observeByFolder()`, `reassignFolder()` |
| [ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt)               | `deleteItem()` and `deleteItems()` → update instead of delete; add filter to `observeActionItems()`, `getByRecordingId()`, `observeActionItemsForRecording()`, `observeSharedTasks()`, `hasGeneratedTasksForSharedRecording()`         |
| [CollectiveSummaryRepository.kt](android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt) | `deleteSummary()` → update instead of delete; add filter to `observeSummaries()`                                                                                                                                                       |
| [FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt)                       | `deleteFolder()` → update instead of delete; add filter to `observeFolders()`, `seedDefaultsIfEmpty()`                                                                                                                                 |
| [SharingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt)                     | Add filter to `observeSharedWithMe()`, `observeMyShares()`, `getUnreadCount()`, `getSharedItem()`. Also filter `ownerItemDeleted == false` on `observeSharedWithMe()`                                                                  |


### 15.3 ViewModel Changes (5 files)

No ViewModel logic changes needed — ViewModels call repository methods, and the repository layer handles the soft delete logic. The only exception:


| File                                                                                                  | Change                                                                                                                                                                                                                                           |
| ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [TaskDetailViewModel.kt](android/app/src/main/java/com/voicemind/ui/checklist/TaskDetailViewModel.kt) | The `isDeleted` UI state flag is already used for navigation — no conflict since the Firestore `isDeleted` field is on the data model, not the UI state. However, rename the UI state field to `isNavigatingAway` or similar to avoid confusion. |


### 15.4 UI Changes

**No UI changes needed.** The delete buttons, confirmation dialogs, and delete actions all remain identical from the user's perspective. The only change is what happens under the hood (update vs delete).

### 15.5 Cloud Function Changes (2 files)


| File                                   | Changes                                                                                                                                                                                                                                                                                                    |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [sharing.ts](functions/src/sharing.ts) | Replace `onRecordingDeleted` with `onRecordingSoftDeleted` (onDocumentUpdated); replace `onCollectiveSummaryDeleted` with `onCollectiveSummarySoftDeleted` (onDocumentUpdated); update `revokeShare` and `dismissSharedItem` to soft-delete instead of hard-delete; add `ownerItemDeleted` flag management |
| googleTasks.ts                         | Change action item `onDocumentDeleted` trigger to `onDocumentUpdated` that checks `isDeleted`; clear external IDs on soft delete                                                                                                                                                                           |


---

## 16. Data Migration

### 16.1 Backfill Existing Documents

A one-time Cloud Function must be run to add `isDeleted: false` to every existing document in all affected collections. Without this, existing documents will **not match** the `whereEqualTo("isDeleted", false)` queries and will disappear from the UI.

**Collections to backfill**:

- `users/{uid}/recordings`
- `users/{uid}/actionItems`
- `users/{uid}/collectiveSummaries`
- `users/{uid}/folders`
- `users/{uid}/sharedWithMe`
- `users/{uid}/myShares`

**Approach**: An HTTPS-callable admin function that iterates all users and all documents, using batched writes (500 per batch) to set `isDeleted: false` where the field is absent.

---

## 17. Implementation Phases

### Phase 1 — Data Models + Migration


| #   | Task                                                                                            |
| --- | ----------------------------------------------------------------------------------------------- |
| 1   | Add `isDeleted` and `deletedAt` fields to all Android data models (6 files)                     |
| 2   | Add `ownerItemDeleted` field to `SharedItem` model                                              |
| 3   | Write and run migration Cloud Function to backfill `isDeleted: false` on all existing documents |
| 4   | Create all required composite Firestore indexes                                                 |


### Phase 2 — Repository Layer (Android)


| #   | Task                                                                    |
| --- | ----------------------------------------------------------------------- |
| 1   | Update `RecordingRepository`: soft delete methods + query filters       |
| 2   | Update `ActionItemRepository`: soft delete methods + query filters      |
| 3   | Update `CollectiveSummaryRepository`: soft delete method + query filter |
| 4   | Update `FolderRepository`: soft delete method + query filters           |
| 5   | Update `SharingRepository`: query filters + `ownerItemDeleted` filter   |


### Phase 3 — Cloud Functions


| #   | Task                                                                                           |
| --- | ---------------------------------------------------------------------------------------------- |
| 1   | Replace `onRecordingDeleted` with `onRecordingSoftDeleted` (onDocumentUpdated)                 |
| 2   | Replace `onCollectiveSummaryDeleted` with `onCollectiveSummarySoftDeleted` (onDocumentUpdated) |
| 3   | Update `revokeShare` to soft-delete share docs                                                 |
| 4   | Update `dismissSharedItem` to soft-delete share docs                                           |
| 5   | Update `googleTasks.ts` action item trigger for soft delete                                    |


### Phase 4 — Rename UI State Conflict


| #   | Task                                                                                                   |
| --- | ------------------------------------------------------------------------------------------------------ |
| 1   | Rename `TaskDetailUiState.isDeleted` to `isNavigatingAway` to avoid confusion with the Firestore field |


### Phase 5 — Testing and Deployment


| #   | Task                                                                  |
| --- | --------------------------------------------------------------------- |
| 1   | Deploy migration function and run it                                  |
| 2   | Deploy updated Cloud Functions                                        |
| 3   | Deploy updated Android app                                            |
| 4   | Verify all queries correctly filter soft-deleted items                |
| 5   | Verify sharing interactions (owner soft-delete hides from recipients) |
| 6   | Verify account deletion still hard-deletes everything                 |


---

## 18. Edge Cases


| Scenario                                                                    | Behavior                                                                                                                                                                    |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner soft-deletes a recording → admin restores it                          | Recording reappears in owner's list; trigger restores visibility for all recipients                                                                                         |
| Owner soft-deletes a recording, then deletes their account                  | Hard delete cascade wipes everything including the soft-deleted recording                                                                                                   |
| User soft-deletes a task with Google Tasks integration                      | Google Task and Calendar event are deleted from Google; `googleTaskId`/`calendarEventId` cleared; if restored, task reappears without the Google links                      |
| User soft-deletes a folder                                                  | Recordings are reassigned to "unfiled" first, then folder is soft-deleted                                                                                                   |
| User soft-deletes a recording inside a folder, then soft-deletes the folder | Both are independently soft-deleted; restoring the folder does NOT auto-restore the recording                                                                               |
| Recipient dismisses a shared item, then owner restores the original         | The share relationship is soft-deleted (by dismiss), so the item does NOT reappear for the recipient. Admin must also restore the sharedWithMe/myShares entries separately. |
| Existing documents without `isDeleted` field                                | Migration backfills `isDeleted: false` before app update is released                                                                                                        |
| `duplicateSharedRecording` fails mid-operation                              | Rollback operations remain as hard deletes (cleaning up failed partial writes)                                                                                              |
| Multiple users share the same recording, owner soft-deletes                 | All recipients' `sharedWithMe` entries get `ownerItemDeleted: true` via trigger                                                                                             |
| Owner soft-deletes, then re-records with same topic                         | No conflict — the soft-deleted recording and the new recording are separate documents                                                                                       |


---

## 19. Summary


| Entity                       | Delete Type | Recovery        | Audio Preserved     | Shares Preserved       |
| ---------------------------- | ----------- | --------------- | ------------------- | ---------------------- |
| Recording                    | Soft        | Admin only      | Yes                 | Yes (hidden via flag)  |
| Action Item                  | Soft        | Admin only      | N/A                 | N/A                    |
| Collective Summary           | Soft        | Admin only      | N/A                 | Yes (hidden via flag)  |
| Folder                       | Soft        | Admin only      | N/A                 | N/A                    |
| Shared Item (dismiss/revoke) | Soft        | Admin only      | N/A                 | Relationship preserved |
| User Account                 | **Hard**    | Not recoverable | Deleted permanently | Cleaned up permanently |
| Device Tokens                | **Hard**    | Not applicable  | N/A                 | N/A                    |
| OAuth Tokens                 | **Hard**    | Not applicable  | N/A                 | N/A                    |


---

**End of PRD**