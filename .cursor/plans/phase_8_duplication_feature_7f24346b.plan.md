---
name: Phase 8 Duplication Feature
overview: "Implement the Phase 8 Duplication Feature: a Cloud Function to copy a shared recording (document + audio + action items) into the recipient's account, a repository method to call it, a folder picker dialog, and wiring the existing disabled \"Duplicate\" button on SharedRecordingDetailScreen."
todos:
  - id: fix-build-error
    content: Fix the existing smart-cast compiler error in SharedRecordingDetailScreen.kt (line 259)
    status: completed
  - id: cloud-function
    content: Implement duplicateSharedRecording callable in functions/src/sharing.ts with auth check, sharedWith verification, doc copy, audio copy, action items copy, and error cleanup
    status: completed
  - id: data-layer
    content: Add duplicateSharedRecording method to SharingRepository.kt
    status: completed
  - id: viewmodel
    content: "Update SharedRecordingDetailViewModel: inject FolderRepository, observe folders, add duplication state and duplicateToFolder action"
    status: completed
  - id: ui-wiring
    content: "Update SharedRecordingDetailScreen: enable Duplicate button, add folder picker dialog (reuse MoveToFolderDialog), handle loading/success states"
    status: completed
  - id: compile-verify
    content: Run compileDebugKotlin to verify zero errors
    status: completed
isProject: false
---

# Phase 8: Duplication Feature

## Pre-existing Build Error (fix first)

There is a Kotlin compiler error in [SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt) at line 259: *"Smart cast to 'kotlin.String' is impossible, because 'error' is a delegated property."* Fix by replacing the `val error = state.error` + null-check pattern with `state.error?.let { errorMsg -> ... }`.

---

## 1. Cloud Function: `duplicateSharedRecording`

**File:** [functions/src/sharing.ts](functions/src/sharing.ts) -- add a new `onCall` export.

**Input:** `{ ownerUid: string, recordingId: string, destinationFolderId: string }`

**Logic:**

- Auth check (same pattern as existing callables)
- Read `users/{ownerUid}/recordings/{recordingId}`, verify caller UID is in `sharedWith` array
- Generate new ID: `rec-${Date.now()}-${randomBytes(4).toString('hex')}`
- Copy recording document to `users/{callerUid}/recordings/{newId}` with:
  - Content fields: `title`, `transcription`, `summary`, `durationSeconds`
  - `folderId` = `destinationFolderId`
  - `audioPath` = `users/{callerUid}/audio/{newId}.m4a`
  - `createdAt` = server timestamp
  - `sharedWith` omitted (caller now owns it)
- Copy audio file in Cloud Storage: `storage.bucket().file(originalAudioPath).copy(newAudioPath)` (Admin SDK `file.copy()`)
- Query `users/{ownerUid}/actionItems` where `recordingId == originalRecordingId`
- Copy each action item to `users/{callerUid}/actionItems/{auto-id}` with `recordingId` set to `newId`, `sharedWith` removed, `googleTaskId`/`calendarEventId` nulled, `completed` preserved
- Return `{ success: true, newRecordingId: newId }`
- Error handling: if audio copy fails, clean up partial writes (delete the recording doc and any copied action items)

**Follows existing patterns:** uses `db`, `storage` from [functions/src/lib/firestore.ts](functions/src/lib/firestore.ts), same `HttpsError` codes, batched writes for action items.

---

## 2. Android Data Layer

**File:** [SharingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt)

Add one method following the existing callable pattern:

```kotlin
suspend fun duplicateSharedRecording(
    ownerUid: String,
    recordingId: String,
    destinationFolderId: String,
): String {
    val result = functions
        .getHttpsCallable("duplicateSharedRecording")
        .call(hashMapOf(
            "ownerUid" to ownerUid,
            "recordingId" to recordingId,
            "destinationFolderId" to destinationFolderId,
        ))
        .await()
    val data = result.getData() as? Map<String, Any>
        ?: throw Exception("duplicateSharedRecording returned no data")
    return data["newRecordingId"] as? String
        ?: throw Exception("duplicateSharedRecording returned no newRecordingId")
}
```

---

## 3. Android UI: ViewModel Changes

**File:** [SharedRecordingDetailViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt)

- Inject `FolderRepository` into the ViewModel constructor
- Add `folders: List<Folder>` to `SharedRecordingDetailUiState` (observed from `FolderRepository.observeFolders()`)
- Add `isDuplicating: Boolean = false` and `duplicateSuccess: String? = null` to state
- Add `duplicateToFolder(folderId: String)` function:
  - Sets `isDuplicating = true`
  - Calls `sharingRepository.duplicateSharedRecording(ownerUid, recordingId, folderId)`
  - On success: sets `duplicateSuccess = newRecordingId`, `isDuplicating = false`
  - On failure: sets `error = "Failed to duplicate recording"`, `isDuplicating = false`

---

## 4. Android UI: Folder Picker Dialog + Duplicate Button Wiring

**File:** [SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt)

- Reuse the existing `MoveToFolderDialog` from [RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt) (it takes `folders: List<Folder>`, `onConfirm: (String) -> Unit`, `onDismiss`) -- this follows the DeveloperGuide's "Reuse first, create second" principle
- Add local state: `var showFolderPicker by remember { mutableStateOf(false) }`
- Enable the existing disabled "Duplicate" menu item and wire `onClick` to set `showFolderPicker = true`
- Show `MoveToFolderDialog` (with title override or as-is) when `showFolderPicker == true`:
  - `folders` = `state.folders`
  - `onConfirm` = `viewModel.duplicateToFolder(folderId)`
- Show loading overlay or disable UI when `state.isDuplicating`
- On `state.duplicateSuccess` becoming non-null, show a Snackbar "Recording duplicated" and optionally navigate to the duplicated recording

---

## Summary of Files Changed

- **[functions/src/sharing.ts](functions/src/sharing.ts)** -- add `duplicateSharedRecording` callable (~60 lines)
- **[SharingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt)** -- add one method (~15 lines)
- **[SharedRecordingDetailViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt)** -- inject `FolderRepository`, add folder observation, add duplication logic (~30 lines)
- **[SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt)** -- fix build error, enable Duplicate button, add folder picker dialog, handle success/loading states (~30 lines)

No new files created.

---

## Action Items on Your End

1. **Deploy the cloud function** after implementation: `cd functions && npm run build && firebase deploy --only functions:duplicateSharedRecording`
2. **Verify Cloud Storage CORS/IAM**: The `storage.bucket().file().copy()` API requires the service account to have read access to the source bucket (this is already the case since all audio is in the same default bucket -- no action needed unless you use a custom bucket)
3. **Test end-to-end**: Share a recording from one account to another, then duplicate it from the recipient's account. Verify audio plays, transcription/summary/tasks are all copied, and the duplicate is fully editable

