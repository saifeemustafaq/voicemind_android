# Soft Delete Feature — Implementation Phases

Each phase is self-contained: once complete, it does not need to be revisited. Phases are ordered by dependency. A developer should complete every checklist item in a phase before moving on.

**Core rule:** Every delete operation becomes an update that sets `isDeleted: true` and `deletedAt: serverTimestamp()`. The document stays in Firestore. Audio files stay in Cloud Storage. All queries add `.whereEqualTo("isDeleted", false)` to hide soft-deleted items. Account deletion is the only exception — it remains a hard delete for GDPR compliance.

---

## Phase 1: Data Models + Migration

**Goal:** Every soft-deletable entity gains `isDeleted` and `deletedAt` fields. Existing Firestore documents are backfilled with `isDeleted: false` so they continue to appear in queries. All required composite Firestore indexes are created.

### Android — Data Models

- [ ] Update `Recording.kt` — add two fields at the end of the data class:
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  ```

- [ ] Update `ActionItem.kt` — add two fields at the end of the data class:
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  ```

- [ ] Update `CollectiveSummary.kt` — add two fields at the end of the data class:
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  ```

- [ ] Update `Folder.kt` — add two fields after `createdAt` (before the `companion object`):
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  ```

- [ ] Update `SharedItem.kt` — add three fields at the end of the data class:
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  val ownerItemDeleted: Boolean = false,
  ```
  `ownerItemDeleted` is managed exclusively by Cloud Functions — set to `true` when the owner soft-deletes the underlying recording/summary, set back to `false` when the admin restores it.

- [ ] Update `MyShare.kt` — add two fields at the end of the data class:
  ```kotlin
  val isDeleted: Boolean = false,
  val deletedAt: Timestamp? = null,
  ```

### Backend — Migration Cloud Function

- [ ] Create `backfillIsDeleted` HTTPS-callable admin function in `functions/src/migration.ts`
  - Iterates all `users` documents
  - For each user, iterates all 6 subcollections: `recordings`, `actionItems`, `collectiveSummaries`, `folders`, `sharedWithMe`, `myShares`
  - For each document where `isDeleted` is absent, sets `isDeleted: false` via batched writes (500 per batch)
  - Returns total count of documents updated
  - This is critical because `whereEqualTo("isDeleted", false)` in Firestore does **NOT** match documents where the field is missing — without this migration, all existing data disappears from the UI

- [ ] Register `backfillIsDeleted` in `functions/src/index.ts`

### Firestore Indexes

- [ ] Create composite index: `users/{uid}/recordings` — `isDeleted` (ASC), `createdAt` (DESC)
- [ ] Create composite index: `users/{uid}/recordings` — `isDeleted` (ASC), `folderId` (ASC), `createdAt` (DESC)
- [ ] Create composite index: `users/{uid}/actionItems` — `isDeleted` (ASC), `createdAt` (DESC)
- [ ] Create composite index: `users/{uid}/actionItems` — `isDeleted` (ASC), `recordingId` (ASC)
- [ ] Create composite index: `users/{uid}/actionItems` — `isDeleted` (ASC), `sharedFromUid` (ASC), `createdAt` (DESC)
- [ ] Create composite index: `users/{uid}/collectiveSummaries` — `isDeleted` (ASC), `createdAt` (DESC)
- [ ] Create composite index: `users/{uid}/folders` — `isDeleted` (ASC), `createdAt` (ASC)
- [ ] Create composite index: `users/{uid}/sharedWithMe` — `isDeleted` (ASC), `sharedAt` (DESC)
- [ ] Create composite index: `users/{uid}/sharedWithMe` — `isDeleted` (ASC), `isRead` (ASC)
- [ ] Create composite index: `users/{uid}/myShares` — `isDeleted` (ASC), `itemId` (ASC)

Indexes can be created manually in Firebase Console or by running the affected queries and clicking the auto-generated index creation links in the error messages.

### Verification

- [ ] All 6 Android data models compile with the new fields
- [ ] Migration function successfully backfills `isDeleted: false` on all existing documents across all collections
- [ ] After migration, every document in every affected collection has `isDeleted: false`
- [ ] All 10 composite indexes are created and active in Firebase Console

---

## Phase 2: Android Repository Layer — Soft Delete Methods + Query Filters

**Goal:** Every Android repository replaces hard delete calls with soft delete updates, and every collection query filters out soft-deleted items. No UI changes — the repository layer is the only thing that changes.

### RecordingRepository.kt

- [ ] Update `observeRecordings()`: add `.whereEqualTo("isDeleted", false)` before `orderBy("createdAt", DESC)`

- [ ] Update `observeByFolder(folderId)`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("folderId", folderId)`

- [ ] Update `reassignFolder()`: add `.whereEqualTo("isDeleted", false)` to the query `whereEqualTo("folderId", fromFolderId)`

- [ ] Update `deleteRecording(recording)`: replace the entire method body — instead of `document.delete()` + Storage `delete()`, update the document with `isDeleted: true` and `deletedAt: serverTimestamp()`. Do NOT delete the audio file from Cloud Storage.
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

- [ ] Update `deleteRecordings(recordings)`: replace the `forEach { deleteRecording(it) }` loop to use the new soft delete method (which no longer deletes audio, so batching is possible but not required)

### ActionItemRepository.kt

- [ ] Update `observeActionItems()`: add `.whereEqualTo("isDeleted", false)` before `orderBy("createdAt", DESC)`

- [ ] Update `getByRecordingId(recordingId)`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("recordingId", recordingId)`

- [ ] Update `observeActionItemsForRecording(ownerUid, recordingId)`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("recordingId", recordingId)`

- [ ] Update `observeSharedTasks()`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereNotEqualTo("sharedFromUid", null)`

- [ ] Update `hasGeneratedTasksForSharedRecording()`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("recordingId", syntheticId)`

- [ ] Update `deleteItem(itemId)`: replace `document(itemId).delete().await()` with:
  ```kotlin
  suspend fun deleteItem(itemId: String) {
      collection().document(itemId).update(
          mapOf(
              "isDeleted" to true,
              "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
          )
      ).await()
  }
  ```

- [ ] Update `deleteItems(itemIds)`: replace `batch.delete(document(id))` with `batch.update(document(id), mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp()))`

### CollectiveSummaryRepository.kt

- [ ] Update `observeSummaries()`: add `.whereEqualTo("isDeleted", false)` before `orderBy("createdAt", DESC)`

- [ ] Update `deleteSummary(summaryId)`: replace `document(summaryId).delete().await()` with:
  ```kotlin
  suspend fun deleteSummary(summaryId: String) {
      collection().document(summaryId).update(
          mapOf(
              "isDeleted" to true,
              "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
          )
      ).await()
  }
  ```

### FolderRepository.kt

- [ ] Update `observeFolders()`: add `.whereEqualTo("isDeleted", false)` before `orderBy("createdAt", ASC)`

- [ ] Update `seedDefaultsIfEmpty()`: add `.whereEqualTo("isDeleted", false)` to the `collection().get()` query so it only counts non-deleted folders when deciding whether to seed defaults

- [ ] Update `deleteFolder(folderId)`: replace `document(folderId).delete().await()` with:
  ```kotlin
  suspend fun deleteFolder(folderId: String) {
      if (folderId == Folder.UNFILED_ID) return
      collection().document(folderId).update(
          mapOf(
              "isDeleted" to true,
              "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
          )
      ).await()
  }
  ```
  Note: recordings are still reassigned to "unfiled" by `FoldersViewModel` before this method is called — that behavior does not change.

### SharingRepository.kt

- [ ] Update `observeSharedWithMe()`: add `.whereEqualTo("isDeleted", false)` and `.whereEqualTo("ownerItemDeleted", false)` before `orderBy("sharedAt", DESC)`

- [ ] Update `observeMyShares(itemId)`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("itemId", itemId)`

- [ ] Update `getUnreadCount()`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("isRead", false)`

- [ ] Update `getSharedItem(itemId)`: add `.whereEqualTo("isDeleted", false)` alongside the existing `whereEqualTo("itemId", itemId)`

### Verification

- [ ] Deleting a recording sets `isDeleted: true` and `deletedAt` in Firestore — document still exists, audio file still in Storage
- [ ] Deleted recording no longer appears in the recordings list or folder view
- [ ] Deleting a task sets `isDeleted: true` — task disappears from checklist
- [ ] Bulk deleting tasks uses batch updates instead of batch deletes
- [ ] Deleting a collective summary sets `isDeleted: true` — summary disappears from list
- [ ] Deleting a folder reassigns recordings to "unfiled", then sets `isDeleted: true` on the folder
- [ ] Shared items queries correctly filter out both `isDeleted == true` and `ownerItemDeleted == true` entries
- [ ] `seedDefaultsIfEmpty()` does not re-seed defaults when all folders are soft-deleted (counts only non-deleted folders)

---

## Phase 3: Cloud Functions — Trigger Conversion + Soft Delete Share Operations

**Goal:** All Cloud Function triggers that fired on document deletion now fire on document updates and check for `isDeleted` field changes. Share revoke/dismiss operations soft-delete share documents instead of hard-deleting them. Google Tasks integration handles soft deletes.

### Replace `onRecordingDeleted` with `onRecordingSoftDeleted`

- [ ] Remove `onRecordingDeleted` from `functions/src/sharing.ts`

- [ ] Create `onRecordingSoftDeleted` in `functions/src/sharing.ts`
  - Trigger type: `onDocumentUpdated({ document: "users/{uid}/recordings/{recordingId}" })`
  - Guard: return early if `isDeleted` field did not change (`before.data().isDeleted === after.data().isDeleted`)
  - **If `isDeleted` changed to `true`** (owner soft-deleted the recording):
    1. Query `users/{uid}/myShares` where `itemId == recordingId` and `itemType == "recording"` and `isDeleted == false`
    2. For each share entry (batched 250/batch): set `ownerItemDeleted: true` on the recipient's `users/{recipientUid}/sharedWithMe/{shareId}`
    3. Remove `sharedWith` field from all linked `actionItems` where `recordingId == recordingId` using `FieldValue.delete()` (same as current behavior, batched 500/batch)
  - **If `isDeleted` changed to `false`** (admin restored the recording):
    1. Query `users/{uid}/myShares` where `itemId == recordingId` and `itemType == "recording"` and `isDeleted == false`
    2. For each share entry: set `ownerItemDeleted: false` on the recipient's `sharedWithMe/{shareId}`
    3. Re-add recipient UIDs to `sharedWith` on linked action items using `arrayUnion`

### Replace `onCollectiveSummaryDeleted` with `onCollectiveSummarySoftDeleted`

- [ ] Remove `onCollectiveSummaryDeleted` from `functions/src/sharing.ts`

- [ ] Create `onCollectiveSummarySoftDeleted` in `functions/src/sharing.ts`
  - Trigger type: `onDocumentUpdated({ document: "users/{uid}/collectiveSummaries/{summaryId}" })`
  - Guard: return early if `isDeleted` field did not change
  - **If `isDeleted` changed to `true`**: query `myShares` for this summary, set `ownerItemDeleted: true` on all matching `sharedWithMe` entries
  - **If `isDeleted` changed to `false`**: query `myShares`, set `ownerItemDeleted: false` on all matching `sharedWithMe` entries

### Update `revokeShare` to Soft Delete

- [ ] Update `revokeShare` in `functions/src/sharing.ts`
  - Replace `batch.delete(db.doc(...sharedWithMe/{shareId}))` with `batch.update(db.doc(...sharedWithMe/{shareId}), { isDeleted: true, deletedAt: serverTimestamp() })`
  - Replace `batch.delete(myShareRef)` with `batch.update(myShareRef, { isDeleted: true, deletedAt: serverTimestamp() })`
  - Remove the `batch.update(itemRef, { sharedWith: arrayRemove(recipientUid) })` — do NOT modify the `sharedWith` array (preserve for potential restore)
  - Remove the `removeFromActionItemsSharedWith` call for recordings — keep action item `sharedWith` arrays intact

### Update `dismissSharedItem` to Soft Delete

- [ ] Update `dismissSharedItem` in `functions/src/sharing.ts`
  - Replace `batch.delete(inboxRef)` with `batch.update(inboxRef, { isDeleted: true, deletedAt: serverTimestamp() })`
  - Replace `batch.delete(db.doc(...myShares/{shareId}))` with `batch.update(db.doc(...myShares/{shareId}), { isDeleted: true, deletedAt: serverTimestamp() })`
  - Remove the `batch.update(itemRef, { sharedWith: arrayRemove(callerUid) })` — do NOT modify the `sharedWith` array
  - Remove the `removeFromActionItemsSharedWith` call for recordings

### Update `googleTasks.ts` for Soft Delete

- [ ] In `googleTasks.ts`, change the action item `onDocumentDeleted` trigger to `onDocumentUpdated`
  - Guard: only run if `isDeleted` changed from `false` to `true`
  - When `isDeleted` becomes `true`: delete the linked Google Task and Calendar event from Google's APIs (external integrations should reflect the delete immediately)
  - After deleting external resources: clear `googleTaskId` and `calendarEventId` from the action item document using `FieldValue.delete()` — this way, if the item is later restored, it won't try to reference stale external IDs

### No Changes to These Functions

- [ ] Confirm `duplicateSharedRecording` rollback operations remain as **hard deletes** — they clean up failed partial writes, not user-initiated deletes
- [ ] Confirm FCM invalid token cleanup (`cleanBatch.delete`) remains as **hard delete** — ephemeral system data
- [ ] Confirm `onUserDeleted` in `userProfile.ts` remains unchanged — account deletion stays as hard delete per GDPR
- [ ] Confirm `shareItem` function is unchanged — it creates documents, does not delete them
- [ ] Confirm `buildAndCommitActionItems` in `firestore.ts` is unchanged — no deletion involved

### Verification

- [ ] Owner soft-deletes a shared recording → all recipients' `sharedWithMe` entries get `ownerItemDeleted: true`, items disappear from their Shared Items list
- [ ] Admin restores the recording (sets `isDeleted: false`) → all recipients' `sharedWithMe` entries get `ownerItemDeleted: false`, items reappear
- [ ] Owner soft-deletes a shared collective summary → same pattern as recordings
- [ ] Revoking a share sets `isDeleted: true` on both `sharedWithMe` and `myShares` docs — documents still exist in Firestore
- [ ] Dismissing a shared item sets `isDeleted: true` on both `sharedWithMe` and `myShares` docs
- [ ] After revoke/dismiss, the `sharedWith` array on the underlying item is NOT modified
- [ ] Soft-deleting a task with Google Tasks integration removes the linked Google Task and Calendar event from Google, clears the IDs on the Firestore doc
- [ ] Account deletion still hard-deletes everything (unchanged behavior)

---

## Phase 4: Rename UI State Conflict

**Goal:** Resolve the naming conflict between the Firestore `isDeleted` field on `ActionItem` and the `isDeleted` UI state flag on `TaskDetailUiState` used for navigation.

### Android — ViewModel + Screen

- [ ] In `TaskDetailViewModel.kt`: rename `TaskDetailUiState.isDeleted` to `isNavigatingAway`
  ```kotlin
  data class TaskDetailUiState(
      val item: ActionItem? = null,
      val recordingTitle: String? = null,
      val isLoading: Boolean = true,
      val isNavigatingAway: Boolean = false,   // was: isDeleted
  )
  ```

- [ ] In `TaskDetailViewModel.kt`: update the `deleteItem()` method to use the new field name:
  ```kotlin
  fun deleteItem() {
      viewModelScope.launch(Dispatchers.IO) {
          _uiState.value = _uiState.value.copy(isNavigatingAway = true)
          actionItemRepository.deleteItem(itemId)
      }
  }
  ```

- [ ] In `TaskDetailViewModel.kt`: update the `init` block observer to check `!_uiState.value.isNavigatingAway` instead of `!_uiState.value.isDeleted`

- [ ] In `TaskDetailScreen.kt`: update all references from `state.isDeleted` to `state.isNavigatingAway` (used in `LaunchedEffect` for back-navigation after delete)

### Verification

- [ ] Deleting a task from the detail screen still triggers back-navigation correctly
- [ ] No compilation errors related to the rename
- [ ] The Firestore `isDeleted` field on `ActionItem` data model does not conflict with any UI state

---

## Phase 5: Testing & Deployment

**Goal:** Deploy all changes in the correct order and verify end-to-end soft delete behavior.

### Deployment Order

- [ ] Deploy the migration Cloud Function (`backfillIsDeleted`) and run it to backfill all existing documents with `isDeleted: false`
- [ ] Verify all documents have `isDeleted: false` by spot-checking in Firebase Console
- [ ] Deploy updated Cloud Functions (new triggers, updated `revokeShare`, `dismissSharedItem`, `googleTasks.ts`)
- [ ] Deploy updated Android app (data models, repositories, UI state rename)

### End-to-End Verification

- [ ] **Recording soft delete**: delete a recording → verify document exists with `isDeleted: true` in Firestore, audio file still in Cloud Storage, recording gone from app UI
- [ ] **Task soft delete**: delete a single task → verify document exists with `isDeleted: true`, task gone from checklist
- [ ] **Bulk task delete**: select multiple tasks and delete → verify all documents have `isDeleted: true`
- [ ] **Summary soft delete**: delete a collective summary → verify document exists with `isDeleted: true`, summary gone from list
- [ ] **Folder soft delete**: delete a folder → verify recordings reassigned to unfiled, folder document has `isDeleted: true`
- [ ] **Shared recording soft delete**: owner deletes a shared recording → verify `ownerItemDeleted: true` on recipient's `sharedWithMe` entry, item disappears from recipient's Shared Items
- [ ] **Shared summary soft delete**: same as above for collective summaries
- [ ] **Share revoke**: owner revokes a share → verify `isDeleted: true` on both `sharedWithMe` and `myShares` docs, `sharedWith` array on item unchanged
- [ ] **Share dismiss**: recipient dismisses a shared item → verify same soft delete pattern
- [ ] **Google Tasks integration**: delete a task with `googleTaskId` → verify Google Task removed from Google, `googleTaskId` cleared from Firestore doc, VoiceMind doc still exists with `isDeleted: true`
- [ ] **Account deletion**: delete a user account → verify everything is hard-deleted (Firestore docs, Storage files, cross-user share references)
- [ ] **Admin restore**: manually set `isDeleted: false` on a soft-deleted recording in Firebase Console → verify it reappears in the owner's list and (if shared) reappears for recipients

---

## Appendix: What Remains Hard Delete

These operations intentionally remain as hard deletes and should NOT be converted to soft delete:

| Operation | File | Reason |
|-----------|------|--------|
| Account deletion cascade | `functions/src/userProfile.ts` — `onUserDeleted` | GDPR compliance requires permanent data removal |
| `duplicateSharedRecording` rollback | `functions/src/sharing.ts` | Cleaning up failed partial writes, not user-initiated deletes |
| Invalid FCM token cleanup | `functions/src/sharing.ts` — `shareItem` | Ephemeral system data, not user content |
| OAuth token deletion | `functions/src/googleTasks.ts` — token disconnect | Security-sensitive credentials |
| Rate limit doc deletion | `functions/src/userProfile.ts` — `onUserDeleted` | System data, not user content |
| Local temp file deletion | `android/.../RecordingService.kt` | Temporary files on device, not cloud data |

---

## Appendix: Modified Files Across All Phases

| Phase | File | Change Summary |
|-------|------|----------------|
| 1 | `android/.../data/model/Recording.kt` | Add `isDeleted`, `deletedAt` fields |
| 1 | `android/.../data/model/ActionItem.kt` | Add `isDeleted`, `deletedAt` fields |
| 1 | `android/.../data/model/CollectiveSummary.kt` | Add `isDeleted`, `deletedAt` fields |
| 1 | `android/.../data/model/Folder.kt` | Add `isDeleted`, `deletedAt` fields |
| 1 | `android/.../data/model/SharedItem.kt` | Add `isDeleted`, `deletedAt`, `ownerItemDeleted` fields |
| 1 | `android/.../data/model/MyShare.kt` | Add `isDeleted`, `deletedAt` fields |
| 1 | `functions/src/migration.ts` | New file — `backfillIsDeleted` callable |
| 1 | `functions/src/index.ts` | Register migration function |
| 2 | `android/.../data/repository/RecordingRepository.kt` | Soft delete methods + `isDeleted` query filters |
| 2 | `android/.../data/repository/ActionItemRepository.kt` | Soft delete methods + `isDeleted` query filters |
| 2 | `android/.../data/repository/CollectiveSummaryRepository.kt` | Soft delete method + `isDeleted` query filter |
| 2 | `android/.../data/repository/FolderRepository.kt` | Soft delete method + `isDeleted` query filters |
| 2 | `android/.../data/repository/SharingRepository.kt` | `isDeleted` + `ownerItemDeleted` query filters |
| 3 | `functions/src/sharing.ts` | Replace `onRecordingDeleted` with `onRecordingSoftDeleted`; replace `onCollectiveSummaryDeleted` with `onCollectiveSummarySoftDeleted`; soft-delete in `revokeShare` and `dismissSharedItem` |
| 3 | `functions/src/googleTasks.ts` | Change action item `onDocumentDeleted` to `onDocumentUpdated` checking `isDeleted` |
| 4 | `android/.../ui/checklist/TaskDetailViewModel.kt` | Rename `isDeleted` UI state to `isNavigatingAway` |
| 4 | `android/.../ui/checklist/TaskDetailScreen.kt` | Update references from `isDeleted` to `isNavigatingAway` |

---

## Appendix: New Files Created

| Phase | File | Type |
|-------|------|------|
| 1 | `functions/src/migration.ts` | Cloud Function — one-time migration |

---

## Appendix: Phase Dependencies

```
Phase 1  ──→  Phase 2  ──→  Phase 3  ──→  Phase 4  ──→  Phase 5
```

All phases are strictly sequential. Each phase depends on the previous one. Phase 1 must be deployed (especially the migration) before Phase 2 changes go live, otherwise existing documents without `isDeleted` will disappear from queries.

---

## Appendix: Edge Cases

| Scenario | Behavior |
|----------|----------|
| Owner soft-deletes a recording → admin restores it | Recording reappears in owner's list; trigger restores visibility for all recipients |
| Owner soft-deletes a recording, then deletes their account | Hard delete cascade wipes everything including the soft-deleted recording |
| User soft-deletes a task with Google Tasks integration | Google Task and Calendar event are deleted from Google; `googleTaskId`/`calendarEventId` cleared; if restored, task reappears without the Google links |
| User soft-deletes a folder | Recordings are reassigned to "unfiled" first, then folder is soft-deleted |
| User soft-deletes a recording inside a folder, then soft-deletes the folder | Both are independently soft-deleted; restoring the folder does NOT auto-restore the recording |
| Recipient dismisses a shared item, then owner restores the original | The share relationship is soft-deleted (by dismiss), so the item does NOT reappear for the recipient — admin must also restore the `sharedWithMe`/`myShares` entries separately |
| Existing documents without `isDeleted` field | Migration backfills `isDeleted: false` before app update is released |
| `duplicateSharedRecording` fails mid-operation | Rollback operations remain as hard deletes (cleaning up failed partial writes) |
| Multiple users share the same recording, owner soft-deletes | All recipients' `sharedWithMe` entries get `ownerItemDeleted: true` via trigger |
| Owner soft-deletes, then re-records with same topic | No conflict — the soft-deleted recording and the new recording are separate documents |

---

## Appendix: Admin Restoration Procedure

Since there is no user-facing Trash UI, restoration is performed by an administrator via Firebase Console.

### Restoring a Single Item

1. Navigate to `Firestore Database > users/{uid}/{collection}/{docId}`
2. Set `isDeleted` to `false`
3. Set `deletedAt` to `null` (or delete the field)
4. If the item was shared, the `onDocumentUpdated` trigger will automatically set `ownerItemDeleted: false` on recipients' inbox entries

### Restoring a Recording

Same as above, but also verify that:
- The audio file still exists in Cloud Storage at the path stored in `audioPath`
- Any linked action items that should also be restored are restored separately (each action item is independently soft-deleted)

### Restoring a Shared Item Relationship (after dismiss/revoke)

1. Navigate to the recipient's `users/{recipientUid}/sharedWithMe/{shareId}` document
2. Set `isDeleted` to `false`, `deletedAt` to `null`
3. Navigate to the owner's `users/{ownerUid}/myShares/{shareId}` document
4. Set `isDeleted` to `false`, `deletedAt` to `null`
