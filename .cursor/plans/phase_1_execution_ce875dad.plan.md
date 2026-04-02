---
name: Phase 1 Execution
overview: "Execute Phase 1 of the code standards audit: fix the four tightly coupled recording UI files (RecordingsScreen.kt, RecordingsViewModel.kt, RecordingDialogs.kt, RecordingDetailScreen.kt) by extracting shared components, decomposing large files, and eliminating DRY violations."
todos:
  - id: phase-1a-unify-move-dialog
    content: Unify BulkMoveToFolderDialog with MoveToFolderDialog — add showFolderIcon param, delete duplicate, update call site
    status: done
  - id: phase-1a-needs-internet
    content: Extract NeedsInternetDialog + NeedsInternetReason into ui/components/NeedsInternetDialog.kt, replace in both screens
    status: done
  - id: phase-1a-processing-chip
    content: Extract ProcessingStatusChip into ui/components/ProcessingStatusChip.kt, replace inline code in RecordingRow
    status: done
  - id: phase-1a-clipboard-snackbar
    content: Move clipboard logic to ViewModel, add snackbarMessage state, replace Toast with Snackbar in RecordingsScreen
    status: done
  - id: phase-1b-extract-recording-row
    content: Extract RecordingRow (lines 869-1070) to ui/recording/RecordingRow.kt as internal
    status: done
  - id: phase-1b-extract-summarization
    content: Extract SummarizationPopup + PulseRingIcon (lines 547-714) to ui/recording/SummarizationPopup.kt
    status: done
  - id: phase-1b-extract-completion-toast
    content: Extract CompletionToast (lines 716-746) to ui/recording/CompletionToast.kt
    status: done
  - id: phase-1b-extract-multiselect-topbar
    content: Extract MultiSelectTopBar (lines 748-795) to ui/recording/MultiSelectTopBar.kt
    status: done
  - id: phase-1b-extract-hint-banner
    content: Extract MultiSelectHintBanner (lines 832-867) to ui/recording/MultiSelectHintBanner.kt
    status: done
  - id: phase-1b-fix-remaining
    content: Fix hardcoded 16.dp -> VmDimens.ScreenHorizontalPadding, rename folderId shadow to targetFolderId
    status: done
  - id: phase-1c-fix-viewmodel
    content: Fix !! on line 173, standardize _state.update pattern, verify imports and new methods
    status: done
  - id: phase-1d-extract-tabs
    content: Extract RecordingContentTabs shared composable, replace in RecordingDetailScreen and TranscriptSheet
    status: done
  - id: phase-1d-verify-detail-screen
    content: Verify NeedsInternet dialog replaced, screen is ~250 lines
    status: done
  - id: phase-1e-verify-dialogs
    content: Verify dialog patterns, fix VmDimens in RecordingDialogs.kt, verify TranscriptSheet uses RecordingContentTabs
    status: done
  - id: phase-1-verify
    content: Build verification — ensure project compiles, no lint errors, mark completed items in codephase.md
    status: done
isProject: false
---

# Phase 1: Recording UI Cluster Execution Plan

Phase 1 is ordered as five sequential sub-phases (1A through 1E). Each builds on the prior. The four target files are tightly coupled through shared types, imports, and duplicated code.

**Files under modification:**

- [RecordingsScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsScreen.kt) (1070 LOC)
- [RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt) (600 LOC)
- [RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt) (467 LOC)
- [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt) (475 LOC)
- [RecordingDialogsHost.kt](android/app/src/main/java/com/voicemind/ui/components/RecordingDialogsHost.kt) (66 LOC)

---

## Phase 1A — Extract Shared Components

### 1. Unify `BulkMoveToFolderDialog` with `MoveToFolderDialog`

The `MoveToFolderDialog` in `RecordingDialogs.kt` (lines 419-446) renders plain text rows. The `BulkMoveToFolderDialog` in `RecordingsScreen.kt` (lines 797-830) adds a folder icon per row. Fix:

- Add `showFolderIcon: Boolean = false` param to `MoveToFolderDialog` in `RecordingDialogs.kt`
- When true, render `Icon(Icons.Default.Folder)` + `Spacer(8.dp)` before folder name in a `Row`
- Delete `BulkMoveToFolderDialog` from `RecordingsScreen.kt` (lines 797-830)
- Update bulk-move call site (line 522) to use `MoveToFolderDialog(folders = ..., showFolderIcon = true, onConfirm = { targetFolderId -> ... }, onDismiss = ...)`
- `RecordingDialogsHost.kt` line 45 uses default `false` — no change needed
- `SharedRecordingDetailScreen.kt` passes `title = "Duplicate to Folder"` — verify still compiles

### 2. Extract `NeedsInternetDialog` shared composable

Identical `when (reason) { ... }` + `AlertDialog` blocks at:

- `RecordingsScreen.kt` lines 471-493
- `RecordingDetailScreen.kt` lines 452-474

Create `ui/components/NeedsInternetDialog.kt`:

```kotlin
@Composable
fun NeedsInternetDialog(
    reason: NeedsInternetReason?,
    onDismiss: () -> Unit,
)
```

Move `NeedsInternetReason` sealed interface (currently in `RecordingsViewModel.kt` lines 42-48) into this same file so both screens can import it without depending on the ViewModel.

- Replace inline dialog in `RecordingsScreen.kt` (lines 471-493) with `NeedsInternetDialog(reason = listState.needsInternetDialog, onDismiss = { recordingsViewModel.dismissNeedsInternetDialog() })`
- Replace inline dialog in `RecordingDetailScreen.kt` (lines 452-474) with `NeedsInternetDialog(reason = state.needsInternetDialog, onDismiss = { playbackViewModel.dismissNeedsInternetDialog() })`
- Update `RecordingsViewModel.kt` to import `NeedsInternetReason` from new location

### 3. Extract `ProcessingStatusChip` shared composable

Inline processing chip in `RecordingsScreen.kt` `RecordingRow` (lines 1035-1067). Create `ui/components/ProcessingStatusChip.kt`:

```kotlin
@Composable
fun ProcessingStatusChip(
    hasTranscription: Boolean,
    processingFailed: Boolean,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
)
```

Replace the inline code in `RecordingRow` with `ProcessingStatusChip(...)`. The `TranscriptContent` in `RecordingDialogs.kt` uses a different pattern (`CircularProgressIndicator` + retry button) — leave it as-is.

### 4. Move clipboard logic to ViewModel + Snackbar

Current: `RecordingsScreen.kt` lines 336-342 — `ClipData` + `ClipboardManager` + `Toast.makeText` in a composable lambda. Violates MVVM boundaries (DeveloperGuide S5) and uses Toast instead of Snackbar (Patterns_Guide S8c).

- Add `snackbarMessage: String? = null` to `RecordingsListState` (line 66-87)
- Add `clearSnackbar()` method and `copyTranscriptToClipboard(recording: Recording)` method to `RecordingsViewModel`
- Add `SnackbarHost` + `LaunchedEffect(listState.snackbarMessage)` to `RecordingsScreen.kt`
- Replace inline lambda (lines 336-342) with `onCopyTranscript = { recordingsViewModel.copyTranscriptToClipboard(recording) }`

---

## Phase 1B — Decompose `RecordingsScreen.kt`

Extract 5 private composables into their own files in `ui/recording/`:


| New file                   | Source lines | Composable(s)                                            |
| -------------------------- | ------------ | -------------------------------------------------------- |
| `RecordingRow.kt`          | 869-1070     | `RecordingRow` (now uses `ProcessingStatusChip` from 1A) |
| `SummarizationPopup.kt`    | 547-714      | `SummarizationPopup` + `PulseRingIcon`                   |
| `CompletionToast.kt`       | 716-746      | `CompletionToast`                                        |
| `MultiSelectTopBar.kt`     | 748-795      | `MultiSelectTopBar`                                      |
| `MultiSelectHintBanner.kt` | 832-867      | `MultiSelectHintBanner`                                  |


For each extraction:

- Change visibility from `private` to `internal`
- Move relevant imports to the new file
- Add import back in `RecordingsScreen.kt`

After extraction, fix remaining violations in the now-slimmed `RecordingsScreen.kt`:

- Replace hardcoded `16.dp` on line 226 with `VmDimens.ScreenHorizontalPadding`
- Rename `folderId` lambda parameter (line 525) to `targetFolderId` to avoid shadowing the screen's `folderId` parameter (line 134)

Target: `RecordingsScreen.kt` should be ~300-350 lines.

---

## Phase 1C — Fix `RecordingsViewModel.kt`

- Verify `NeedsInternetReason` import updated from new location (from 1A)
- Fix `!!` on line 173: `localPath!!` to `requireNotNull(localPath) { "localPath should exist when needsDownload is false" }`
- Standardize state updates: convert all `_state.value = _state.value.copy(...)` (~21 occurrences) to `_state.update { it.copy(...) }` for thread-safety and consistency; same for `_sheetState`
- Verify `copyTranscriptToClipboard()` and `snackbarMessage`/`clearSnackbar()` were added in 1A
- Verify all `viewModelScope.launch` calls use `Dispatchers.IO` for network/file/Firebase work

---

## Phase 1D — Fix `RecordingDetailScreen.kt`

### 1. Verify NeedsInternet dialog replaced (from 1A)

### 2. Extract shared tab orchestration

The tab logic (FilterChip row + generate-tasks chip + error/no-results text + Crossfade) is nearly identical between:

- `RecordingDetailScreen.kt` lines 307-400
- `TranscriptSheet` in `RecordingDialogs.kt` lines 92-191

Create `ui/recording/RecordingContentTabs.kt`:

```kotlin
@Composable
internal fun RecordingContentTabs(
    recording: Recording,
    sheetState: TranscriptSheetState,
    onGenerateSummary: () -> Unit,
    onGenerateTasks: () -> Unit,
    onRetryProcessing: () -> Unit,
    onRetrySummary: () -> Unit,
    modifier: Modifier = Modifier,
)
```

This composable encapsulates:

- Tab list construction (`TranscriptTab.Transcript`, `Summary`, conditionally `Tasks`)
- `FilterChip` row with generate-tasks chip
- Error/no-results text for task generation
- `Crossfade` with `TranscriptContent` / `SummaryContent` / `TasksContent`

Replace the inline tab orchestration in:

- `RecordingDetailScreen.kt` (lines 97-101, 307-400) with `RecordingContentTabs(...)`
- `TranscriptSheet` in `RecordingDialogs.kt` (lines 69-191) with `RecordingContentTabs(...)` wrapped in the bottom sheet layout

Key difference: `TranscriptSheet` wraps `Crossfade` content in `Column(Modifier.verticalScroll(...))` — handle this via a `scrollable: Boolean = false` parameter or by having the caller wrap.

### 3. Reduce composable size

After extraction, `RecordingDetailScreen` should be ~250 lines: Scaffold + top bar + waveform + transport controls + `RecordingContentTabs` + dialogs.

---

## Phase 1E — Fix `RecordingDialogs.kt`

- Verify `TranscriptSheet` now uses shared `RecordingContentTabs` from 1D within its `ModalBottomSheet`
- After extraction, `TranscriptSheet` should be ~50 lines (ModalBottomSheet wrapper + title + `RecordingContentTabs`)
- `TranscriptContent`, `SummaryContent`, `TasksContent`, `TaskDateLabels` remain as `internal` composables used by `RecordingContentTabs`
- Verify `RenameRecordingDialog` uses `voiceMindTextFieldColors()` on `OutlinedTextField` (check if not applied — add if `VoiceMindTextFieldColors.kt` provides it)
- Verify `MoveToFolderDialog` has `showFolderIcon` param from 1A
- Verify `DeleteRecordingDialog` has error-colored "Delete" button (line 460 — confirmed)
- Fix hardcoded `dp` values: replace `24.dp` and `16.dp` with appropriate `VmDimens` constants where they exist

---

## New Files Created (Phase 1)


| Sub-phase | File path                                           |
| --------- | --------------------------------------------------- |
| 1A        | `android/.../ui/components/NeedsInternetDialog.kt`  |
| 1A        | `android/.../ui/components/ProcessingStatusChip.kt` |
| 1B        | `android/.../ui/recording/RecordingRow.kt`          |
| 1B        | `android/.../ui/recording/SummarizationPopup.kt`    |
| 1B        | `android/.../ui/recording/CompletionToast.kt`       |
| 1B        | `android/.../ui/recording/MultiSelectTopBar.kt`     |
| 1B        | `android/.../ui/recording/MultiSelectHintBanner.kt` |
| 1D        | `android/.../ui/recording/RecordingContentTabs.kt`  |


## Verification

- Project builds with no errors
- `RecordingsScreen.kt` is ~300-350 lines (down from 1070)
- `RecordingDetailScreen.kt` is ~250 lines (down from 475)
- `TranscriptSheet` in `RecordingDialogs.kt` is ~50 lines
- NeedsInternet dialog renders from both screens via shared component
- Bulk move uses unified `MoveToFolderDialog` with folder icons
- Clipboard copy shows Snackbar instead of Toast
- No lint errors introduced

