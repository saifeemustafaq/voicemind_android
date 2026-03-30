---
name: Recording UI Improvements
overview: "Five UI improvements: simplified play button on recordings list, inline three-pill content tabs on recording detail (no auto-play, stop on back), three-pill layout for shared recordings with on-trigger task generation, and better task generation messaging."
todos:
  - id: simplify-play-button
    content: Remove InlinePlayerControls from RecordingRow, make play icon a simple play/stop toggle
    status: pending
  - id: no-autoplay-stop-on-back
    content: Remove auto-play LaunchedEffect, add DisposableEffect to stop playback on back navigation
    status: pending
  - id: inline-pills-detail
    content: Replace View Content button with inline Transcript/Summary/Task pills on RecordingDetailScreen
    status: pending
  - id: extract-composables
    content: Change TranscriptTab and content composables in RecordingDialogs.kt from private to internal
    status: pending
  - id: shared-three-pills
    content: Replace SharedRecordingDetailScreen card layout with three-pill Transcript/Summary/Task UI
    status: pending
  - id: task-generation-message
    content: Update shared recording task generation snackbar to mention tasks are added to personal checklist
    status: pending
isProject: false
---

# Recording UI Improvements

## Change 1: Simplified Play Icon on Recordings List

**File:** [RecordingsScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsScreen.kt)

- In `RecordingRow`, remove the `AnimatedVisibility` block (lines 1018-1033) that mounts `InlinePlayerControls` when a recording is expanded.
- Remove the `isExpanded` parameter and all inline-control-related params (`onPause`, `onResume`, `onSeekTo`, `onSkipForward`, `onSkipBackward`, `isPlaybackPaused`, `playbackPositionMs`, `playbackDurationMs`) from `RecordingRow`.
- The play icon tap (`onPlayPause`) currently calls `stopPlayback()` if the same recording is tapped. Change so:
  - If nothing is playing this recording: call `playAudio(recording)` (icon shows `PlayCircle`).
  - If this recording is playing: call `stopPlayback()` (icon shows `PauseCircle`).
- The icon toggles between `PlayCircle` and `PauseCircle` based on `isPlaying` (which already works).
- Clean up the call site where `RecordingRow` is instantiated (~lines 316-359) to remove the now-unused parameters.

## Change 2: Recording Detail - No Auto-Play, Stop on Back

**File:** [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt)

- **Remove auto-play:** Delete the `LaunchedEffect(recordingId, recording)` block (lines 84-88) that calls `playbackViewModel.playAudio(recording)`.
- **Stop on back:** Add a `DisposableEffect` that calls `playbackViewModel.stopPlayback()` in its `onDispose` callback, so playback stops when the user navigates away.

## Change 3: Recording Detail - Inline Three Pills Replace "View Content"

**File:** [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt)

- Remove the "View Content" `Button` (lines 287-295) and the `showSheet` state / `TranscriptSheet` block (lines 102, 302-308).
- Add a `LaunchedEffect` to call `playbackViewModel.openTranscriptSheet(recording)` to load action items and summary state on entry.
- Add three `FilterChip` pills inline (Transcript selected by default): Transcript, Summary, Task — reusing the same `TranscriptTab` enum and chip logic from [RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt).
- Below the pills, use `Crossfade` to switch between `TranscriptContent`, `SummaryContent`, and `TasksContent` (these composables need to become `internal` instead of `private` in `RecordingDialogs.kt` so they can be reused).
- The Generate Tasks chip logic (when no tasks exist) also moves inline.
- Summary generation triggers when the Summary pill is selected (same as current `TranscriptSheet` behavior).

**File:** [RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt)

- Change `TranscriptTab`, `TranscriptContent`, `SummaryContent`, `TasksContent`, and `TaskDateLabels` from `private` to `internal` so they can be reused from `RecordingDetailScreen`.

## Change 4: Shared Recording Detail - Three Pills Layout

**File:** [SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt)

- Replace the vertical card layout (`SharedContentCard` for Transcription, `SharedContentCard` for Summary, `SharedTasksCard`, and the "Generate Tasks" button) with three `FilterChip` pills: Transcript (default), Summary, Task.
- **Transcript pill:** Show `recording.transcription` or "No transcription available" (same as current).
- **Summary pill:** If `recording.summary` exists, show it. If not, show a note: "{ownerName} has not generated a summary for this recording." — no generate button.
- **Task pill:** Show the owner's tasks list (same `SharedTasksCard` content). Below that, if `!hasGeneratedTasks` and transcription exists, show the "Generate Tasks" button. Each owner task still has the "Add to checklist" icon.
- Remove `SharedContentCard` and `SharedTasksCard` composables (they become unused).

## Change 5: Better Task Generation Messaging for Shared Recordings

**File:** [SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt)

- Update the snackbar message (line 100) from `"Generated $count task(s)"` to `"Generated $count task(s) — added to your checklist"`.

---

## Files Modified Summary


| File                             | Changes                                                                                |
| -------------------------------- | -------------------------------------------------------------------------------------- |
| `RecordingsScreen.kt`            | Remove `InlinePlayerControls` expansion from `RecordingRow`, simplify params           |
| `RecordingDetailScreen.kt`       | Remove auto-play, add stop-on-back, replace "View Content" with inline pills           |
| `RecordingDialogs.kt`            | Change visibility of `TranscriptTab`, content composables from `private` to `internal` |
| `SharedRecordingDetailScreen.kt` | Replace card layout with three pills, update snackbar message                          |


