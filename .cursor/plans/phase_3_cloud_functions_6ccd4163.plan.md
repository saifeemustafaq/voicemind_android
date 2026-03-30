---
name: Phase 3 Cloud Functions
overview: "Phase 3 converts Cloud Function triggers from onDocumentDeleted to onDocumentUpdated for soft delete, updates share revoke/dismiss to soft-delete, and handles soft delete in Google Tasks sync. Also fixes a critical cross-cutting bug: all document creation paths are missing isDeleted: false, which means newly created items would vanish from queries."
todos:
  - id: fix-cf-creation
    content: "Fix Cloud Function creation paths: add isDeleted: false to shareItem, shareTask, duplicateSharedRecording, buildAndCommitActionItems, generateCollectiveSummary"
    status: pending
  - id: fix-android-creation
    content: "Fix Android creation paths: add isDeleted: false to createRecording, createItem, addSharedTask, createFolder, seedDefaultsIfEmpty, duplicateSharedSummary"
    status: pending
  - id: replace-triggers
    content: Replace onRecordingDeleted and onCollectiveSummaryDeleted with onDocumentUpdated-based soft delete triggers
    status: pending
  - id: update-revoke-dismiss
    content: Update revokeShare and dismissSharedItem to soft-delete share docs instead of hard-deleting
    status: pending
  - id: update-google-tasks
    content: Add soft-delete handling to syncActionItemToGoogleTasks trigger
    status: pending
isProject: false
---

# Phase 3: Cloud Functions + Critical Creation Path Fix

## Critical Fix-Up: Document Creation Paths Missing `isDeleted: false`

Phase 2 added `.whereEqualTo("isDeleted", false)` to all collection queries. But every document creation path writes documents **without** `isDeleted: false`. Since Firestore's `whereEqualTo("isDeleted", false)` does NOT match documents where the field is absent, **every newly created document would be invisible** in the UI. This must be fixed.

### Cloud Function creation paths (5 locations):

**[functions/src/sharing.ts](functions/src/sharing.ts)**

- **Line 141** `shareItem` — `sharedWithMe` doc: add `isDeleted: false, ownerItemDeleted: false`
- **Line 151** `shareItem` — `myShares` doc: add `isDeleted: false`
- **Line 380** `duplicateSharedRecording` — recording doc: add `isDeleted: false`
- **Line 412** `duplicateSharedRecording` — copied action items: add `isDeleted: false`
- **Line 480** `shareTask` — action item doc: add `isDeleted: false`

**[functions/src/summary.ts](functions/src/summary.ts)**

- **Line 138** `generateCollectiveSummary` — summary doc: add `isDeleted: false`

**[functions/src/lib/firestore.ts](functions/src/lib/firestore.ts)**

- **Line 38** `buildAndCommitActionItems` — action item docs: add `isDeleted: false` to the doc map

### Android creation paths (6 locations):

**[RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt)**

- **Line 59** `createRecording()`: add `"isDeleted" to false` to the mapOf

**[ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt)**

- **Line 98** `createItem()`: add `"isDeleted" to false` to the hashMapOf
- **Line 155** `addSharedTask()`: add `"isDeleted" to false` to the mapOf

**[FolderRepository.kt](android/app/src/main/java/com/voicemind/data/repository/FolderRepository.kt)**

- **Line 58** `createFolder()`: add `"isDeleted" to false` to the mapOf
- **Line 45** `seedDefaultsIfEmpty()`: add `"isDeleted" to false` to each batch.set mapOf

**[CollectiveSummaryRepository.kt](android/app/src/main/java/com/voicemind/data/repository/CollectiveSummaryRepository.kt)**

- **Line 98** `duplicateSharedSummary()`: add `"isDeleted" to false` to the mapOf

---

## Phase 3A: Replace Triggers in sharing.ts

### Import change

Replace `onDocumentDeleted` with `onDocumentUpdated`:

```typescript
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
```

### Replace `onRecordingDeleted` (lines 601-644) with `onRecordingSoftDeleted`

- Trigger: `onDocumentUpdated({ document: "users/{uid}/recordings/{recordingId}" })`
- Guard: return early if `before.data().isDeleted === after.data().isDeleted` (isDeleted did not change)
- **If isDeleted changed to `true`** (soft delete):
  1. Query `users/{uid}/myShares` where `itemId == recordingId`, `itemType == "recording"`, `isDeleted == false`
  2. For each share: set `ownerItemDeleted: true` on `users/{recipientUid}/sharedWithMe/{shareId}` (batched 250/batch)
  3. Remove `sharedWith` field from linked action items via `FieldValue.delete()` (batched 500/batch)
- **If isDeleted changed to `false`** (admin restore):
  1. Query `users/{uid}/myShares` where `itemId == recordingId`, `itemType == "recording"`, `isDeleted == false`
  2. For each share: set `ownerItemDeleted: false` on `users/{recipientUid}/sharedWithMe/{shareId}`
  3. Re-add recipient UIDs to `sharedWith` on linked action items via `arrayUnion`

### Replace `onCollectiveSummaryDeleted` (lines 559-587) with `onCollectiveSummarySoftDeleted`

Same pattern but simpler (no action items involved):

- Trigger: `onDocumentUpdated({ document: "users/{uid}/collectiveSummaries/{summaryId}" })`
- Guard: return early if `isDeleted` did not change
- **If isDeleted -> true**: set `ownerItemDeleted: true` on all matching `sharedWithMe` entries
- **If isDeleted -> false**: set `ownerItemDeleted: false` on all matching `sharedWithMe` entries

### Remove `removeFromActionItemsSharedWith` helper (lines 39-56)

This helper is no longer called after `revokeShare` and `dismissSharedItem` stop modifying the `sharedWith` array.

---

## Phase 3B: Update revokeShare and dismissSharedItem

### `revokeShare` (lines 220-265)

- Replace `batch.delete(db.doc(...sharedWithMe/{shareId}))` with `batch.update(..., { isDeleted: true, deletedAt: serverTimestamp() })`
- Replace `batch.delete(myShareRef)` with `batch.update(myShareRef, { isDeleted: true, deletedAt: serverTimestamp() })`
- **Remove** `batch.update(itemRef, { sharedWith: arrayRemove(...) })` — do NOT modify the item's `sharedWith` array
- **Remove** the `removeFromActionItemsSharedWith` call for recordings
- The `itemRef` / `collectionName` / `itemRef` variables and their lookup can be removed since we no longer touch the underlying item

### `dismissSharedItem` (lines 267-304)

- Replace `batch.delete(inboxRef)` with `batch.update(inboxRef, { isDeleted: true, deletedAt: serverTimestamp() })`
- Replace `batch.delete(db.doc(...myShares/{shareId}))` with `batch.update(..., { isDeleted: true, deletedAt: serverTimestamp() })`
- **Remove** `batch.update(itemRef, { sharedWith: arrayRemove(callerUid) })` — do NOT modify the item's `sharedWith` array
- **Remove** the `removeFromActionItemsSharedWith` call for recordings
- The `collectionName` / `itemRef` variables can be removed

---

## Phase 3C: Update googleTasks.ts

### `syncActionItemToGoogleTasks` (line 250)

This trigger already uses `onDocumentWritten` so it fires on updates. Add soft-delete handling **after** the existing metadata guard (line 273) and **before** the `!after` check (line 292):

```typescript
// Soft delete — remove external integrations and stop
if (after && after.isDeleted === true && (!before || before.isDeleted !== true)) {
  if (taskId) await deleteGoogleTask(tasks, taskId, uid);
  if (calEventId) await deleteCalendarEvent(calendar, calEventId, uid);
  const removals: Record<string, unknown> = {};
  if (taskId) removals.googleTaskId = admin.firestore.FieldValue.delete();
  if (calEventId) removals.calendarEventId = admin.firestore.FieldValue.delete();
  if (Object.keys(removals).length > 0) {
    await event.data?.after?.ref.update(removals);
  }
  return;
}

// Skip any other updates on soft-deleted items
if (after?.isDeleted === true) return;
```

---

## Phase 3D: Update index.ts exports

The renamed exports (`onRecordingSoftDeleted`, `onCollectiveSummarySoftDeleted`) replace the old names. Since [index.ts](functions/src/index.ts) uses `export * from "./sharing.js"`, no changes to index.ts are needed — the barrel export picks up whatever sharing.ts exports.

---

## Files modified


| File                                         | Changes                                                                     |
| -------------------------------------------- | --------------------------------------------------------------------------- |
| `functions/src/sharing.ts`                   | Replace 2 triggers, update 2 share ops, fix 5 creation paths, remove helper |
| `functions/src/googleTasks.ts`               | Add soft-delete handling to sync trigger                                    |
| `functions/src/lib/firestore.ts`             | Add `isDeleted: false` to `buildAndCommitActionItems`                       |
| `functions/src/summary.ts`                   | Add `isDeleted: false` to `generateCollectiveSummary`                       |
| `android/.../RecordingRepository.kt`         | Add `isDeleted` to `createRecording`                                        |
| `android/.../ActionItemRepository.kt`        | Add `isDeleted` to `createItem`, `addSharedTask`                            |
| `android/.../FolderRepository.kt`            | Add `isDeleted` to `createFolder`, `seedDefaultsIfEmpty`                    |
| `android/.../CollectiveSummaryRepository.kt` | Add `isDeleted` to `duplicateSharedSummary`                                 |


