---
name: Fix blank recording content
overview: Fix the blank transcript/title/tasks issue for longer recordings by increasing cloud function resources, properly awaiting the processRecording callable with error handling, and making action items load reactively.
todos:
  - id: cloud-fn-resources
    content: Increase processRecording timeout to 300s and memory to 1GiB in transcription.ts
    status: pending
  - id: await-callable
    content: Await processRecording callable in RecordingService and write processingFailed flag on error
    status: pending
  - id: recording-model
    content: Add processingFailed field to Recording data class
    status: pending
  - id: transcript-ui-states
    content: Update TranscriptContent to show processing spinner, error+retry, or transcript
    status: pending
  - id: retry-processing
    content: Add retryProcessing function in RecordingsViewModel
    status: pending
  - id: reactive-action-items
    content: Add observeByRecordingId to ActionItemRepository and use it in RecordingsViewModel
    status: pending
isProject: false
---

# Fix Blank Recording Content for Longer Recordings

## Problem

After saving a ~10 minute recording, the user sees blank content: no auto-generated title, no transcript, and no tasks. Audio playback works fine because the file was uploaded to Storage before the cloud function was invoked.

The root cause is a combination of:

1. Cloud function timeout (120s) and insufficient memory for longer recordings
2. Fire-and-forget callable invocation with zero error visibility
3. One-shot action items loading that misses late-arriving tasks

## Changes

### 1. Increase Cloud Function Resources

In [functions/src/transcription.ts](functions/src/transcription.ts), increase `processRecording` timeout from 120s to 300s and explicitly set memory to 1GiB:

```typescript
export const processRecording = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 300, memory: "1GiB" },
  ...
);
```

### 2. Await the Callable and Handle Errors in RecordingService

In [android/app/src/main/java/com/voicemind/service/RecordingService.kt](android/app/src/main/java/com/voicemind/service/RecordingService.kt), change the fire-and-forget `.call()` to `.call().await()` inside the existing try/catch. This ensures:

- The coroutine waits for the function to complete (or fail)
- Failures are logged via the existing `catch` block
- The service stays alive (wake lock held) until processing finishes

### 3. Add a `processingFailed` Field to the Recording Model

In [android/app/src/main/java/com/voicemind/data/model/Recording.kt](android/app/src/main/java/com/voicemind/data/model/Recording.kt), add a `processingFailed: Boolean = false` field. In `RecordingService`, on callable failure, write this flag to Firestore so the UI can detect the failure even after the service dies.

### 4. Show Processing/Error State in the Detail Screen

In [android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt), update `TranscriptContent` to show three states:

- `processingFailed == true` and `transcription == null` → error message with "Retry" button
- `transcription == null` and `processingFailed != true` → "Processing transcript..." with a spinner
- `transcription != null` → the transcript text (current behavior)

### 5. Add Retry Transcription Support

In [android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt), add a `retryProcessing(recording)` function that:

- Clears the `processingFailed` flag in Firestore
- Re-invokes the `processRecording` callable (this time awaited)
- On failure, sets `processingFailed` again

### 6. Make Action Items Reactive

In [android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt), change `loadActionItems` from a one-shot `getByRecordingId` to a snapshot listener (similar to `observeRecordings`). This requires adding an `observeByRecordingId` method to [android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt) and collecting it in `openTranscriptSheet`.

## File Summary

- `functions/src/transcription.ts` — increase timeout + memory
- `RecordingService.kt` — await callable, write `processingFailed` on error
- `Recording.kt` — add `processingFailed` field
- `RecordingDialogs.kt` — show processing/error/retry states in `TranscriptContent`
- `RecordingsViewModel.kt` — add `retryProcessing`, make action items reactive
- `ActionItemRepository.kt` — add `observeByRecordingId` snapshot listener
- `RecordingDetailScreen.kt` — wire retry button to ViewModel

