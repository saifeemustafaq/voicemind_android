---
name: SharedByMe tabs and tasks
overview: Add pill-style filter tabs (Recordings / Tasks / Summaries) to the SharedByMeScreen, and update the shareTask Cloud Function to write a myShares ledger entry so shared tasks appear in the "Shared by Me" view with revoke support.
todos:
  - id: backend-share-task
    content: Update shareTask Cloud Function to write a myShares ledger entry alongside the task copy
    status: done
  - id: backend-revoke-task
    content: Update revokeShare to handle task type by soft-deleting recipient's actionItems doc instead of sharedWithMe
    status: done
  - id: action-item-get
    content: Add getActionItem(itemId) one-shot method to ActionItemRepository
    status: done
  - id: vm-tabs
    content: Add SharedByMeTab enum, selectedTab, and per-type lists to SharedByMeViewModel
    status: done
  - id: screen-tabs
    content: Add FilterChip row and per-tab content switching to SharedByMeScreen
    status: done
isProject: false
---

# Shared by Me: Filter Tabs + Task Tracking

## 1. Backend: `shareTask` writes a `myShares` ledger entry

In [functions/src/sharing.ts](functions/src/sharing.ts), after the existing `targetRef.set(...)` call (line 454), add a `myShares` write following the same pattern as `shareItem` (line 135-143):

```typescript
const shareId = db.collection("_").doc().id;
batch.set(db.doc(`users/${callerUid}/myShares/${shareId}`), {
  recipientUid,
  recipientName: recipientData.displayName || "",
  recipientEmail: recipientData.email || "",
  itemType: "task",
  itemId: taskId,
  sharedAt: admin.firestore.FieldValue.serverTimestamp(),
  isDeleted: false,
});
```

This needs to be converted to use a batch (currently it's a single `targetRef.set`). We also need to read `recipientDoc.data()` for the name/email (already fetched on line 437).

## 2. Backend: `revokeShare` handles tasks

In [functions/src/sharing.ts](functions/src/sharing.ts), the current `revokeShare` (line 205-244) soft-deletes `myShares/{shareId}` and `sharedWithMe/{shareId}`. For tasks, there is no `sharedWithMe` doc -- instead the recipient has an `actionItems/shared-{callerUid}-{taskId}` doc.

Modify `revokeShare` to read `itemType` and `itemId` from the `myShareDoc`. If `itemType === "task"`:

- Soft-delete `myShares/{shareId}` (same as now)
- Soft-delete the recipient's `actionItems/shared-${callerUid}-${itemId}` instead of `sharedWithMe/{shareId}`

If not a task, keep the existing behavior unchanged.

## 3. Frontend: Add filter tabs to SharedByMeScreen

### [SharedByMeViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedByMeViewModel.kt)

- Add `SharedByMeTab` enum: `Recordings`, `Tasks`, `Summaries`
- Add `selectedTab` and separate lists (`recordings`, `tasks`, `summaries`) to `SharedByMeUiState`, replacing the single `items` list
- Split the grouped items by `itemType` into the three lists
- Add `selectTab()` function
- Inject `ActionItemRepository` and add a `"task"` case to `resolveTitle()` that calls a new `getActionItem(taskId)` method
- Add `getActionItem(itemId)` one-shot method to [ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt):

```kotlin
suspend fun getActionItem(itemId: String): ActionItem? =
    collection().document(itemId).get().await()
        .toObject(ActionItem::class.java)
```

### [SharedByMeScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedByMeScreen.kt)

- Add a `FilterChip` row (same pattern as [SharedItemsScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedItemsScreen.kt) lines 74-88) between the top bar and the LazyColumn
- Switch list content based on `state.selectedTab`
- Each tab shows its filtered items or an appropriate empty state ("No shared recordings yet", "No shared tasks yet", "No shared summaries yet")

### [FoldersViewModel.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersViewModel.kt)

No changes needed -- `observeAllMyShares()` will automatically pick up new task shares since they also write to `myShares`.

## Note on existing shared tasks

Tasks shared before this change will NOT have `myShares` entries, so they won't appear in "Shared by Me." Only newly shared tasks going forward will be tracked. This is acceptable for a clean cutover; a backfill migration could be added later if needed.