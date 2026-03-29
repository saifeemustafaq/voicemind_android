---
name: Complete Phase 7 Cascade
overview: Add an `onRecordingDeleted` Firestore trigger to `functions/src/sharing.ts` that cleans up all `myShares`, `sharedWithMe`, and actionItem `sharedWith` entries when an owner deletes a shared recording.
todos:
  - id: add-trigger
    content: Add onDocumentDeleted import and onRecordingDeleted trigger to functions/src/sharing.ts
    status: completed
  - id: deploy-verify
    content: Deploy functions and test deletion cascade with two accounts
    status: pending
isProject: false
---

# Complete Phase 7: Recording Deletion Cascade

## Scope

This is a single Cloud Function addition -- no Android code changes. When an owner deletes a recording, the trigger automatically removes all sharing references so recipients' Shared Items update in real time.

## Current State

[functions/src/sharing.ts](functions/src/sharing.ts) has 5 exported callables (`findUserByEmail`, `shareItem`, `revokeShare`, `dismissSharedItem`, `getSharedAudioUrl`) and 2 helpers (`checkRateLimit`, `removeFromActionItemsSharedWith`, `getItemCollection`). No Firestore triggers exist in this file yet.

The codebase already uses v2 Firestore triggers elsewhere:

- `onDocumentCreated` in [functions/src/nts.ts](functions/src/nts.ts) (line 10)
- `onDocumentWritten` in [functions/src/googleTasks.ts](functions/src/googleTasks.ts) (line 250)

[functions/src/index.ts](functions/src/index.ts) already re-exports everything from `sharing.ts` (line 8), so the new export will be automatically registered.

## Implementation

Add `onRecordingDeleted` to [functions/src/sharing.ts](functions/src/sharing.ts):

**Import**: Add `onDocumentDeleted` from `firebase-functions/v2/firestore` alongside the existing `onCall` import.

**Trigger path**: `users/{uid}/recordings/{recordingId}`

**Logic**:

1. Read deleted document data from `event.data` (v2 API gives the pre-delete snapshot)
2. Extract `sharedWith` array -- if empty or absent, return early (recording was never shared, nothing to clean up)
3. Query `users/{uid}/myShares` where `itemId == recordingId` and `itemType == "recording"`
4. For each `myShares` document found:
  - Read `recipientUid` and `shareId` (document ID)
  - Delete `users/{recipientUid}/sharedWithMe/{shareId}` (recipient inbox entry)
  - Delete the `myShares/{shareId}` document itself (owner outbox entry)
5. Clear `sharedWith` field from all linked actionItems: query `users/{uid}/actionItems` where `recordingId == recordingId`, batch-update to delete the `sharedWith` field using `FieldValue.delete()`

**Batch handling**: Process deletions in batches of up to 500 operations (Firestore batch limit). In practice a recording is unlikely to be shared with 250+ users, but the implementation should be correct regardless.

**Key detail on v2 API**: `onDocumentDeleted` provides `event.data` as the snapshot of the deleted document (not `event.data.before` like v1). Access fields via `event.data.data()`. Wildcard params via `event.params.uid` and `event.params.recordingId`.

**Reference pattern** from the codebase (line 10-12 of [nts.ts](functions/src/nts.ts)):

```typescript
export const autoScheduleActionItem = onDocumentCreated(
  { document: "users/{uid}/actionItems/{itemId}" },
  async (event) => {
    const uid = event.params.uid;
```

## File Changes


| File                       | Change                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------- |
| `functions/src/sharing.ts` | Add `onDocumentDeleted` import; add `onRecordingDeleted` exported trigger (~40 lines) |


No changes to any other file. `index.ts` already re-exports all of `sharing.ts`.

## Action Items for You

1. **Deploy after implementation**: Run `firebase deploy --only functions` from the `functions/` directory to deploy the new trigger
2. **Test with two accounts**: Share a recording from Account A to Account B, then delete the recording from Account A. Verify:
  - Recording disappears from Account B's Shared Items list (real-time via snapshot listener)
  - Check Firestore console: `myShares` and `sharedWithMe` entries for that recording are gone
  - If shared with multiple users, all are cleaned up
3. **Verify no side effects**: Owner's other recordings and shares remain intact; recipient's other shared items remain intact

