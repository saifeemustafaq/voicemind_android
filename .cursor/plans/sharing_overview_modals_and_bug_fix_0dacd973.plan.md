---
name: Sharing Overview Modals and Bug Fix
overview: Add an eye/reveal icon to the "Shared with Me" and "Shared by Me" folder rows that opens a lightweight overview modal showing type breakdown and per-person stats. Also fix the bug where `revokeShare` and `dismissSharedItem` don't clean up the `sharedWith` array on the source document.
todos:
  - id: overview-data
    content: Add SharingOverview data classes and state fields to FoldersViewModel, populate from existing flows
    status: completed
  - id: overview-ui
    content: Add Visibility icon to SharedItemsRow and SharedByMeRow, create SharingOverviewDialog composable in FoldersScreen
    status: completed
  - id: fix-revoke
    content: Add arrayRemove of recipientUid from sharedWith in revokeShare (sharing.ts)
    status: completed
  - id: fix-dismiss
    content: Add arrayRemove of callerUid from sharedWith in dismissSharedItem (sharing.ts)
    status: completed
isProject: false
---

# Sharing Overview Modals and sharedWith Cleanup Bug Fix

## Part 1: Overview Modals on Folder Rows

### Data needed

**Shared with Me overview:**

- Count of recordings, tasks, summaries (from `observeSharedWithMe()` grouped by `itemType`, plus `observeSharedTasks()` for tasks)
- Unread items grouped by sender name (from `observeSharedWithMe()` filtered by `isRead == false`, grouped by `ownerName`)

**Shared by Me overview:**

- Count of recordings, tasks, summaries (from `observeAllMyShares()` grouped by `itemType`, then `distinctBy { itemId }` per type)
- Items grouped by recipient (from `observeAllMyShares()` grouped by `recipientName`)

### FoldersViewModel changes

[FoldersViewModel.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersViewModel.kt)

Add two new data classes and state fields:

```kotlin
data class SharingOverview(
    val recordingCount: Int = 0,
    val taskCount: Int = 0,
    val summaryCount: Int = 0,
    val total: Int = 0,
    val perPerson: List<PersonStat> = emptyList(),
)

data class PersonStat(
    val name: String,
    val count: Int,
)
```

Add to `FoldersUiState`:

- `sharedWithMeOverview: SharingOverview` -- computed from `observeSharedWithMe()` (type counts) + `observeSharedTasks()` (task count), unread items grouped by `ownerName`
- `sharedByMeOverview: SharingOverview` -- computed from `observeAllMyShares()`, distinct items by type, items grouped by `recipientName`

Populate via existing flows already collected in `init`:

- The `sharedWithMe` flow is not currently collected in FoldersViewModel -- add a new `viewModelScope.launch` that collects `sharingRepository.observeSharedWithMe()` and `actionItemRepository.observeSharedTasks()` to build the overview
- The `sharedByMe` flow already collects `observeAllMyShares()` -- extend that collector to also build the overview

### FoldersScreen changes

[FoldersScreen.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersScreen.kt)

**SharedItemsRow** (line ~309): Add a `Visibility` icon button between the badge and the chevron. Wire `onClick` to set `showSharedWithMeOverview = true`.

**SharedByMeRow** (line ~279): Add a `Visibility` icon button between the count text and the chevron. Wire `onClick` to set `showSharedByMeOverview = true`.

**SharingOverviewDialog**: New private composable in the same file. An `AlertDialog` that takes a title string and a `SharingOverview`. Layout:

- Title: "Shared with Me -- Overview" or "Shared by Me -- Overview"
- Three rows: Recordings / Tasks / Summaries with counts, right-aligned
- Divider
- Total row
- Divider
- "Unread" label (for shared-with-me) or "Shared with" label (for shared-by-me)
- `perPerson` list: each row shows person name + count, right-aligned
- If `perPerson` is empty, show "None" in onSurfaceVariant
- Close button

Add two boolean state vars at the top of `FoldersScreen`:

```kotlin
var showSharedWithMeOverview by remember { mutableStateOf(false) }
var showSharedByMeOverview by remember { mutableStateOf(false) }
```

Show the dialog when the corresponding boolean is true.

---

## Part 2: Fix sharedWith Array Cleanup Bug

### Problem

Both `revokeShare` and `dismissSharedItem` in [sharing.ts](functions/src/sharing.ts) soft-delete the `myShares` and `sharedWithMe` entries but do NOT remove the user's UID from the `sharedWith` array on the source document (recording/collectiveSummary). This means:

- Revoked/dismissed users still have read access via security rules (`request.auth.uid in resource.data.sharedWith`)
- The `sharedWith` array grows but never shrinks

### Fix in `revokeShare` (line ~205)

After reading the `myShareDoc` (which has `itemType` and `itemId`), before the batch commit, add:

```typescript
if (itemType && itemId && itemType !== "task") {
  const itemRef = db.doc(`users/${callerUid}/${getItemCollection(itemType)}/${itemId}`);
  batch.update(itemRef, {
    sharedWith: admin.firestore.FieldValue.arrayRemove(storedRecipientUid),
  });
}
```

The `getItemCollection` helper already exists in sharing.ts (maps `"recording"` to `"recordings"`, `"collectiveSummary"` to `"collectiveSummaries"`).

### Fix in `dismissSharedItem` (line ~255)

After reading the `inboxDoc` (which has `ownerUid`, `itemType`, `itemId`), before the batch commit, add:

```typescript
const { ownerUid, itemType, itemId } = inboxDoc.data() as {
  ownerUid: string;
  itemType?: string;
  itemId?: string;
};

if (itemType && itemId && itemType !== "task") {
  const itemRef = db.doc(`users/${ownerUid}/${getItemCollection(itemType)}/${itemId}`);
  batch.update(itemRef, {
    sharedWith: admin.firestore.FieldValue.arrayRemove(callerUid),
  });
}
```

For recordings, also clean up linked action items' `sharedWith` arrays (same pattern as the existing `onRecordingSoftDeleted` trigger).