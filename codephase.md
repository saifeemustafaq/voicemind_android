# Code Standards Audit — Implementation Phases

Each phase is self-contained: once complete, it does not need to be revisited. Phases are ordered by dependency — tightly coupled files that import each other, share types, or have duplicated code between them are fixed together to prevent breakage. A developer should complete every checklist item in a phase before moving on.

**Audit guides:** `DeveloperGuide.md`, `Android_Developer_Brief.md`, `Patterns_Guide.md`

**Files under audit:**

| LOC | File | Phase |
|-----|------|-------|
| 1070 | `android/.../ui/recording/RecordingsScreen.kt` | 1 |
| 600 | `android/.../ui/recording/RecordingsViewModel.kt` | 1 |
| 467 | `android/.../ui/recording/RecordingDialogs.kt` | 1 |
| 475 | `android/.../ui/recording/RecordingDetailScreen.kt` | 1 |
| 513 | `android/.../ui/sharing/SharedRecordingDetailScreen.kt` | 2 |
| 618 | `android/.../ui/checklist/TaskDetailScreen.kt` | 2 |
| 490 | `android/.../ui/folders/FoldersScreen.kt` | 2 |
| 913 | `android/.../ui/settings/SettingsScreen.kt` | 3 |
| 499 | `android/.../service/RecordingService.kt` | 3 |
| 725 | `functions/src/sharing.ts` | 4 |

---

## Phase 1: Recording UI Cluster

**Goal:** Fix the four tightly coupled recording UI files — `RecordingsScreen.kt`, `RecordingsViewModel.kt`, `RecordingDialogs.kt`, `RecordingDetailScreen.kt` — and extract shared components. These files import each other, share types (`NeedsInternetReason`, `TranscriptSheetState`, `RecordingsListState`), and contain duplicated code (NeedsInternet dialog, folder picker, processing chip, tab orchestration).

```
RecordingsScreen.kt ──imports──► RecordingsViewModel.kt
RecordingsScreen.kt ──uses──────► RecordingDialogs.kt (via RecordingDialogsHost)
RecordingDetailScreen.kt ──imports──► RecordingsViewModel.kt (parent back stack entry)
RecordingDetailScreen.kt ──uses──────► RecordingDialogs.kt (TranscriptContent, SummaryContent, TasksContent, dialogs)
```

### Phase 1A — Extract shared components from `RecordingsScreen.kt`

**Violations addressed:**
- DeveloperGuide S1 (DRY): `BulkMoveToFolderDialog` duplicates `MoveToFolderDialog`
- DeveloperGuide S1 (DRY): NeedsInternet dialog block copy-pasted across two screens
- DeveloperGuide S4 (Reuse): Processing status chip inlined in two places
- DeveloperGuide S5 (MVVM): Clipboard logic lives in composable instead of ViewModel
- Patterns_Guide S8c: Uses `Toast.makeText` instead of M3 Snackbar

#### Unify `BulkMoveToFolderDialog` with `MoveToFolderDialog`

- [x] Update `MoveToFolderDialog` in `RecordingDialogs.kt`: add optional `showFolderIcon: Boolean = false` parameter
  - When `true`, each folder row renders `Icons.Default.Folder` before the name (matching `BulkMoveToFolderDialog`'s current layout)
  - Signature becomes:
    ```kotlin
    @Composable
    fun MoveToFolderDialog(
        folders: List<Folder>,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
        title: String = "Move to Folder",
        showFolderIcon: Boolean = false,
    )
    ```
- [x] Delete the private `BulkMoveToFolderDialog` composable from `RecordingsScreen.kt` (lines 798–830)
- [x] Update `RecordingsScreen.kt` bulk-move call site (line 522) to use `MoveToFolderDialog(folders = ..., showFolderIcon = true, title = "Move to Folder", onConfirm = { folderId -> ... }, onDismiss = ...)`
- [x] Verify `RecordingDialogsHost.kt` still compiles (it calls `MoveToFolderDialog` without the new param — default `false` covers this)
- [x] Verify `SharedRecordingDetailScreen.kt` still compiles (it calls `MoveToFolderDialog` with `title = "Duplicate to Folder"`)

#### Extract `NeedsInternetDialog` shared composable

- [x] Create `ui/components/NeedsInternetDialog.kt`:
  ```kotlin
  @Composable
  fun NeedsInternetDialog(
      reason: NeedsInternetReason?,
      onDismiss: () -> Unit,
  ) {
      reason ?: return
      val (title, body) = when (reason) {
          NeedsInternetReason.GenerateSummary, NeedsInternetReason.GenerateTasks ->
              "Internet Required" to "This recording hasn't been processed yet. ..."
          NeedsInternetReason.CollectiveSummarize ->
              "Internet Required" to "Generating a collective summary requires ..."
          NeedsInternetReason.StillProcessing ->
              "Still Processing" to "This recording is still being processed. ..."
          NeedsInternetReason.ShareWithUser ->
              "Internet Required" to "Sharing requires an internet connection. ..."
      }
      AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text(title) },
          text = { Text(body) },
          confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
      )
  }
  ```
- [x] Replace the inline NeedsInternet `AlertDialog` block in `RecordingsScreen.kt` (lines 472–493) with `NeedsInternetDialog(reason = listState.needsInternetDialog, onDismiss = { recordingsViewModel.dismissNeedsInternetDialog() })`
- [x] Replace the identical block in `RecordingDetailScreen.kt` (lines 453–474) with `NeedsInternetDialog(reason = state.needsInternetDialog, onDismiss = { playbackViewModel.dismissNeedsInternetDialog() })`
- [x] Move `NeedsInternetReason` sealed interface from `RecordingsViewModel.kt` (lines 42–48) into the same file as the dialog (`ui/components/NeedsInternetDialog.kt`) so both screens can import it without depending on the ViewModel file
- [x] Update `RecordingsViewModel.kt` to import `NeedsInternetReason` from the new location

#### Extract `ProcessingStatusChip` shared composable

- [x] Create `ui/components/ProcessingStatusChip.kt`:
  ```kotlin
  @Composable
  fun ProcessingStatusChip(
      hasTranscription: Boolean,
      processingFailed: Boolean,
      syncStatus: SyncStatus,
      modifier: Modifier = Modifier,
  )
  ```
  - Renders the same `Surface` + `Text` chip from `RecordingsScreen.kt` lines 1036–1067
  - Color logic: `processingFailed` → errorContainer, `PENDING_UPLOAD` → tertiaryContainer, else → secondaryContainer
  - Returns early (renders nothing) when `hasTranscription && !processingFailed`
- [x] Replace the inline processing chip in `RecordingsScreen.kt` (`RecordingRow`, lines 1036–1067) with `ProcessingStatusChip(...)`
- [x] Evaluate use in `RecordingDialogs.kt` `TranscriptContent` (lines 197–234) — the content tab shows processing state differently (with `CircularProgressIndicator` and retry button), so it should remain separate; the chip is for list rows only

#### Move clipboard logic to ViewModel

- [x] Add method to `RecordingsViewModel.kt`:
  ```kotlin
  fun copyTranscriptToClipboard(recording: Recording) {
      val text = recording.transcription ?: return
      val clip = ClipData.newPlainText("Transcript", text)
      (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
          .setPrimaryClip(clip)
      _state.update { it.copy(snackbarMessage = "Transcript copied") }
  }
  ```
- [x] Add `snackbarMessage: String? = null` field to `RecordingsListState` and `clearSnackbar()` method
- [x] Add `SnackbarHost` to `RecordingsScreen.kt` (replacing `Toast.makeText`)
- [x] Replace the inline `onCopyTranscript` lambda (lines 337–343) with `onCopyTranscript = { recordingsViewModel.copyTranscriptToClipboard(recording) }`
- [x] Add `LaunchedEffect(listState.snackbarMessage)` to show Snackbar and auto-clear

### Phase 1B — Decompose `RecordingsScreen.kt`

**Violations addressed:**
- DeveloperGuide S4: Main composable exceeds ~200 lines (~415 lines)
- DeveloperGuide S8: File is 1070 lines with 8 private composables inlined
- DeveloperGuide S8: `RecordingRow` is ~180 lines (should be its own file)
- Patterns_Guide S10: Hardcoded `16.dp` instead of `VmDimens.ScreenHorizontalPadding`
- DeveloperGuide S2: `folderId` lambda parameter shadows outer scope

#### Extract `RecordingRow` to its own file

- [x] Create `ui/recording/RecordingRow.kt` — move the `RecordingRow` composable (lines 870–1070) along with its imports
- [x] Change visibility from `private` to `internal` so `RecordingsScreen.kt` can use it
- [x] `RecordingRow` now uses the extracted `ProcessingStatusChip` from Phase 1A

#### Extract `SummarizationPopup` + `PulseRingIcon` to their own file

- [x] Create `ui/recording/SummarizationPopup.kt` — move `SummarizationPopup` (lines 548–647) and `PulseRingIcon` (lines 649–714) with their imports
- [x] Change visibility from `private` to `internal`

#### Extract `CompletionToast` to its own file

- [x] Create `ui/recording/CompletionToast.kt` — move `CompletionToast` (lines 717–746)
- [x] Change visibility from `private` to `internal`

#### Extract `MultiSelectTopBar` to its own file

- [x] Create `ui/recording/MultiSelectTopBar.kt` — move `MultiSelectTopBar` (lines 749–795)
- [x] Change visibility from `private` to `internal`

#### Extract `MultiSelectHintBanner` to its own file

- [x] Create `ui/recording/MultiSelectHintBanner.kt` — move `MultiSelectHintBanner` (lines 833–867)
- [x] Change visibility from `private` to `internal`

#### Fix remaining violations in `RecordingsScreen.kt`

- [x] Replace hardcoded `16.dp` on line 226 with `VmDimens.ScreenHorizontalPadding`
- [x] Rename `folderId` lambda parameter (line 525) to `targetFolderId` to avoid shadowing the screen's `folderId` parameter (line 134)
- [x] Verify `RecordingsScreen.kt` is now ~300–350 lines (main composable only, orchestrating state and calling sub-composables)

### Phase 1C — Fix `RecordingsViewModel.kt`

**Violations addressed:**
- DeveloperGuide S2: `!!` usage without documented justification
- DeveloperGuide S2: Inconsistent state update pattern (mix of `.value = .copy()` and `.update {}`)
- DeveloperGuide S1 (DRY): `NeedsInternetReason` defined here but used by multiple screens

#### Audit and fix violations

- [x] `NeedsInternetReason` already moved to shared location in Phase 1A — verify the import in this file is updated
- [x] `SummaryState` and `TranscriptSheetState` remain here (only used by recording-related screens and `RecordingDialogs.kt`) — no move needed
- [x] Fix `!!` on line 173: `!needsDownload -> localPath!!` — replace with `requireNotNull(localPath) { "localPath should exist when needsDownload is false" }`
- [x] Standardize state updates: 21 uses of `_state.value = _state.value.copy(...)` and 20 uses of `_state.update { ... }` — pick one pattern; prefer `_state.update { it.copy(...) }` per DeveloperGuide S2 (concise Kotlin) and thread-safety
- [x] Add `snackbarMessage` field and `clearSnackbar()` method (per Phase 1A clipboard refactor)
- [x] Verify `copyTranscriptToClipboard()` method was added (per Phase 1A)
- [ ] Check all `viewModelScope.launch` calls use `Dispatchers.IO` for network/file/Firebase work — verify no main-thread blocking

### Phase 1D — Fix `RecordingDetailScreen.kt`

**Violations addressed:**
- DeveloperGuide S1 (DRY): NeedsInternet dialog block duplicated (fixed in 1A)
- DeveloperGuide S1 (DRY): Tab orchestration (FilterChip tabs + Crossfade + generate-tasks chip) duplicated between this screen and `TranscriptSheet` in `RecordingDialogs.kt`
- DeveloperGuide S4: Composable is ~400 lines (should be <200)

#### Remove duplicated dialog

- [x] NeedsInternet dialog already replaced with shared `NeedsInternetDialog` in Phase 1A — verify

#### Extract shared tab orchestration

The tab logic (lines 98–101, 308–401) is nearly identical to `TranscriptSheet` (lines 69–194 in `RecordingDialogs.kt`): same `TranscriptTab` list, same `hasTasks` conditional third tab, same generate-tasks FilterChip, same `Crossfade` with `TranscriptContent` / `SummaryContent` / `TasksContent`, same no-results/failed text.

- [x] Create `ui/recording/RecordingContentTabs.kt` — a composable that encapsulates the shared tab row + task generation chip + error/no-results text + Crossfade content panel:
  ```kotlin
  @Composable
  internal fun RecordingContentTabs(
      recording: Recording,
      sheetState: TranscriptSheetState,
      onGenerateSummary: () -> Unit,
      onGenerateTasks: () -> Unit,
      onRetryProcessing: () -> Unit,
      onRetrySummary: () -> Unit,
      scrollableContent: Boolean = false,
      modifier: Modifier = Modifier,
  )
  ```
- [x] Replace the inline tab orchestration in `RecordingDetailScreen.kt` (lines 98–101, 307–401) with `RecordingContentTabs(...)`
- [x] Replace the inline tab orchestration in `TranscriptSheet` (`RecordingDialogs.kt`, lines 69–194) with `RecordingContentTabs(...)` wrapped in the bottom sheet layout

#### Reduce composable size

- [x] After extraction, `RecordingDetailScreen` should be ~250 lines: Scaffold + top bar + waveform + transport controls + `RecordingContentTabs` + dialogs
- [ ] Fix hardcoded `24.dp` padding — evaluate if `VmDimens` constants should be used

### Phase 1E — Fix `RecordingDialogs.kt`

**Violations addressed:**
- DeveloperGuide S4 (Reuse): Tab orchestration duplicated with `RecordingDetailScreen` (fixed in 1D)
- Patterns_Guide S2: Verify dialog patterns match canonical form

#### Apply shared tab orchestration

- [x] `TranscriptSheet` now uses the shared `RecordingContentTabs` from Phase 1D — verify it still works within the `ModalBottomSheet` context (scrollable content column)

#### Verify dialog patterns

- [x] `RenameRecordingDialog` (lines 392–417): follows Patterns_Guide S2b (nullable model) — verify `voiceMindTextFieldColors()` is used on `OutlinedTextField` (currently not applied — add if it exists in `ui/components/`)
- [x] `MoveToFolderDialog` (lines 420–446): now has `showFolderIcon` param from Phase 1A — verify
- [x] `DeleteRecordingDialog` (lines 449–467): follows Patterns_Guide S2a — confirm error-colored "Delete" button

#### Verify composable sizes

- [x] After extracting shared tab content, `TranscriptSheet` should be ~50 lines (just ModalBottomSheet wrapper + title + `RecordingContentTabs`)
- [x] `TranscriptContent`, `SummaryContent`, `TasksContent`, `TaskDateLabels` remain here as `internal` composables used by `RecordingContentTabs` — each is <50 lines, compliant

### Verification (Phase 1)

- [ ] Project builds with no errors
- [ ] `RecordingsScreen` renders correctly: list, multi-select, playback, dialogs, overlays
- [ ] `RecordingDetailScreen` renders correctly: waveform, playback, tabs, transcript/summary/tasks
- [ ] `TranscriptSheet` (via `RecordingDialogsHost`) renders correctly from the list screen
- [ ] Bulk move uses unified `MoveToFolderDialog` with folder icons
- [ ] NeedsInternet dialog shows correctly from both screens
- [ ] Clipboard copy shows Snackbar instead of Toast
- [ ] `RecordingsScreen.kt` is ~300–350 lines
- [ ] `RecordingDetailScreen.kt` is ~250 lines
- [ ] No lint errors introduced

---

## Phase 2: Satellite Screens

**Goal:** Fix three screens that have light coupling to Phase 1 outputs (`MoveToFolderDialog`, shared patterns) but are otherwise independent. These can be done in any order within the phase.

### Phase 2A — `SharedRecordingDetailScreen.kt` (513 LOC)

**Coupling to Phase 1:** Imports `MoveToFolderDialog` from `RecordingDialogs.kt`.

#### Audit findings

- Screen composable spans ~400 lines (lines 75–510) — exceeds 200-line guideline
- Uses `LocalClipboardManager` correctly (no change needed — this is the preferred Compose API)
- Uses `VmDimens` in some places but hardcodes `dp` values in others
- Parallel product shape to `RecordingDetailScreen` (scaffold, waveform, playback, tabs, overflow menu) — drift risk but no code sharing needed since the shared-recording context is different (read-only, signed URL audio, duplication flow)

#### Fix violations

- [ ] Extract private composable for the transport controls section (play/pause, skip, speed) — reuse across `RecordingDetailScreen` and `SharedRecordingDetailScreen` if layout is identical, or extract within the file if layout differs
- [x] Extract the overflow menu into a private `SharedRecordingOverflowMenu` composable
- [x] Extract the content tabs section into a private `SharedRecordingContentTabs` composable (FilterChip row + Crossfade with transcript/summary/tasks)
- [x] Replace hardcoded `8.dp` chips spacer with `VmDimens.SpaceSm`; fix `!!` on line 196 using smart-cast pattern
- [x] Verify `MoveToFolderDialog` still works after Phase 1A changes (new `showFolderIcon` param defaults to `false`)
- [x] Verify `SnackbarHost` pattern matches Patterns_Guide S8c

### Phase 2B — `TaskDetailScreen.kt` (618 LOC)

**Coupling to Phase 1:** None. Uses `ShareDialog` and `GlassCard` from shared components.

#### Audit findings

- Screen composable spans ~400 lines (lines 75–480) — exceeds 200-line guideline
- Contains `EditableTitle`, `NotesCard`, `DateDeadlineCard` as private composables — good decomposition but main screen still too long
- `DateDeadlineCard` contains nested date/time picker state management (~150 lines) — heavy for an inline composable
- Uses `BasicTextField` with `onFocusChanged` for commit-on-blur — matches Patterns_Guide S13
- Dialog patterns follow Patterns_Guide S2

#### Fix violations

- [x] Extract `DateDeadlineCard` (with its nested pickers) into a separate file `ui/checklist/DateDeadlineCard.kt`; change `private` to `internal`
- [ ] Extract the delete confirmation dialog into the main screen's dialog section (verify it follows Patterns_Guide S2a)
- [x] Replace all hardcoded `dp` values with `VmDimens` constants throughout `TaskDetailScreen.kt` and `DateDeadlineCard.kt`
- [x] Verify the overflow menu pattern matches Patterns_Guide S9 (conditional items first, destructive last with error color)
- [ ] Verify `ShareDialog` usage matches Patterns_Guide S15 (connectivity guard before showing) — noted as future work, not a code-standards fix

### Phase 2C — `FoldersScreen.kt` (490 LOC)

**Coupling to Phase 1:** None. Uses `GlassCard`, `VoiceMindTopAppBar` from shared components.

#### Audit findings

- Main screen composable is moderate (~200 lines) — borderline compliant
- Multiple private composables: `FolderRow`, `SharedByMeRow`, `SharedItemsRow`, `SharingOverviewDialog`, `OverviewStatRow`, `FolderNameDialog` — all repeat `GlassCard + Row + chevron/badge` layout
- `FolderNameDialog` follows dialog pattern correctly

#### Fix violations

- [x] Evaluate `FolderRow`, `SharedByMeRow`, `SharedItemsRow`: all use `GlassCard` with `Row`, icon, text, count/badge, and `ChevronRight` — extract shared `NavigationRow` composable into `ui/components/NavigationRow.kt` parameterized with `icon`, `label`, `trailingContent`, `onClick`
- [x] Replace the three row composables with `NavigationRow(...)` calls, passing appropriate trailing content
- [x] Verify `FolderNameDialog` uses `voiceMindTextFieldColors()` on `OutlinedTextField`
- [x] Replace hardcoded `dp` values with `VmDimens` where applicable (`16.dp` → `ScreenHorizontalPadding`, `8.dp` → `SpaceSm`, `4.dp` → `SpaceXs`, `2.dp` → `SpaceXxs`, `48.dp` → `TouchTarget`)
- [x] Verify the sort overflow menu (if present) matches Patterns_Guide S9 ordering — sort buttons are IconButtons in top bar, not a menu; correct

### Verification (Phase 2)

- [ ] Project builds with no errors
- [ ] `SharedRecordingDetailScreen` renders correctly: waveform, playback, tabs, duplication, copy
- [ ] `TaskDetailScreen` renders correctly: editable title, notes, date pickers, delete, share
- [ ] `FoldersScreen` renders correctly: folder list, shared items row, shared-by-me row, create/rename/delete
- [ ] All extracted composables work correctly
- [ ] No lint errors introduced

---

## Phase 3: Isolated Large Files

**Goal:** Fix two large, independently isolated files. `SettingsScreen.kt` has a ~750-line monolithic composable; `RecordingService.kt` has coroutine anti-patterns.

### Phase 3A — `SettingsScreen.kt` (913 LOC)

**Coupling:** None to Phase 1 or 2 files. Uses shared components (`GlassCard`, `PrimaryButton`, `VoiceMindTopAppBar`).

#### Audit findings

- `SettingsScreen` composable spans ~750 lines (lines 84–858) — massively exceeds 200-line guideline
- Contains 6 distinct settings sections inlined: Account, Navigation, Note to Self, Timezone, Integrations (Google Tasks), Privacy, Storage, Delete Account
- `LaunchedEffect(deleteState)` block (lines 114–146) contains Google Credential Manager logic — this is business logic in the composable, violating MVVM boundaries
- Uses `rememberCoroutineScope()` + `scope.launch` — should be in ViewModel
- Hardcodes `16.dp` instead of `VmDimens.ScreenHorizontalPadding` (line 179)
- `NtsTimePickerDialog` and `StorageRow` are already extracted as private composables — good

#### Fix violations: Decompose into section composables

- [x] Create `ui/settings/AccountSection.kt` — private composable for Account card (sign out, user info)
- [x] Create `ui/settings/NavigationSection.kt` — private composable for sidebar toggle, landing page, tab order, all within one `GlassCard`
- [x] Create `ui/settings/TaskSchedulingSection.kt` — private composable for NTS settings card (replaces NoteToSelfSection.kt naming in plan)
- [x] Create `ui/settings/TimezoneSection.kt` — private composable for timezone settings card
- [x] Create `ui/settings/IntegrationsSection.kt` — private composable for Google Tasks/Calendar connect cards
- [x] Create `ui/settings/PrivacySection.kt` — private composable for discoverability toggle
- [x] Create `ui/settings/SyncSection.kt` — private composable for sync status
- [x] Create `ui/settings/StorageSection.kt` — private composable for storage management (owns `StorageRow`)
- [x] Create `ui/settings/DeleteAccountSection.kt` — private composable for delete account flow

Each section composable takes the relevant ViewModel state + callbacks as parameters.

#### Fix MVVM boundary violations

- [x] Move the Google Credential Manager re-auth logic (lines 119–135) into `SettingsViewModel` — composable calls `settingsViewModel.initiateGoogleReAuth(activity)`; ViewModel handles `CredentialManager`; `deleteError` moved to ViewModel `StateFlow`
- [x] Remove `rememberCoroutineScope()` from `SettingsScreen` — all async work goes through the ViewModel
- [x] Replace hardcoded `16.dp` with `VmDimens.ScreenHorizontalPadding`

#### Post-decomposition target

- [x] `SettingsScreen.kt` is ~180 lines: collecting state, `LaunchedEffect` handlers, consent launcher, `VoiceMindTopAppBar`, section composable calls, error snackbars, version footer
- [x] Each section file is ~40–130 lines

### Phase 3B — `RecordingService.kt` (499 LOC)

**Coupling:** None to Phase 1 or 2 files. Interacted with by `RecordingViewModel`, `MainActivity`, and widget.

#### Audit findings

- Uses `runBlocking` on line 217 to read `navPreferenceRepository.appTimezone.first()` — blocks the calling thread (potentially main thread if `handleStopSave` is called from `onStartCommand`)
- Service spans ~450 lines of logic — monolithic but acceptable for a foreground service; no composable size rule applies
- Proper use of `CoroutineScope(SupervisorJob() + Dispatchers.IO)` for background work
- Notification channel creation and media session handling are correct

#### Fix violations

- [x] Replace `runBlocking` (line 217) with a cached timezone value: `@Volatile private var cachedTimezone` initialized to `TimeZone.getDefault()`; collected in `onCreate`; used directly in `handleStopSave`
- [x] Verify `wakeLock.acquire()` has a timeout parameter — `acquire(4 * 60 * 60 * 1000L)` already correct
- [x] Verify `scope.cancel()` is called in `onDestroy` — already correct (line 470)
- [x] Review logging statements: no PII logged — already correct
- [x] Verify `mediaSession.release()` is called in cleanup paths — already correct (line 472)

### Verification (Phase 3)

- [ ] Project builds with no errors
- [ ] Settings screen renders all sections correctly
- [ ] All settings toggles/pickers/buttons work
- [ ] Account deletion flow works (Google re-auth now goes through ViewModel)
- [ ] Recording service starts, records, stops, saves correctly
- [x] No `runBlocking` calls remain in service
- [ ] No lint errors introduced

---

## Phase 4: Backend

**Goal:** Audit the TypeScript Cloud Functions file against `Android_Developer_Brief.md` to verify function signatures, collections, and fields match what the brief defines. Extract repeated patterns into shared helpers.

### Phase 4A — `functions/src/sharing.ts` (725 LOC)

**Coupling:** Backend only — no Kotlin file imports. Android calls these functions via `FirebaseFunctions.getHttpsCallable()`.

#### Audit findings

- 10 exported callables/triggers in one file — high cognitive load but functionally coherent (all sharing-related)
- Repeated `if (!request.auth)` + `throw new HttpsError("unauthenticated", ...)` pattern at the top of every `onCall` handler — 7 identical blocks
- Repeated `sharedWith` array check pattern (verify caller UID is in `sharedWith`) used in `getSharedAudioUrl`, `duplicateSharedRecording`, `generateTasksFromSharedRecording` — 3 identical blocks
- `getItemCollection()` helper already extracted — good
- `checkRateLimit()` helper already extracted — good
- Batch delete pattern (250 ops per batch) duplicated between `onRecordingSoftDeleted` and `onCollectiveSummarySoftDeleted`

#### Fix violations

- [ ] Extract `requireAuth(request)` helper that throws `HttpsError("unauthenticated", ...)` and returns `request.auth.uid`:
  ```typescript
  function requireAuth(request: CallableRequest): string {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be signed in");
    return request.auth.uid;
  }
  ```
- [ ] Replace all 7 inline auth checks with `const callerUid = requireAuth(request)`
- [ ] Extract `requireSharedWith(ownerUid, collection, itemId, callerUid)` helper that reads the document and verifies `callerUid` is in `sharedWith`:
  ```typescript
  async function requireSharedWith(
    ownerUid: string,
    collection: string,
    itemId: string,
    callerUid: string,
  ): Promise<FirebaseFirestore.DocumentData> {
    const doc = await db.doc(`users/${ownerUid}/${collection}/${itemId}`).get();
    if (!doc.exists) throw new HttpsError("not-found", "Item not found");
    const data = doc.data()!;
    if (!data.sharedWith?.includes(callerUid)) throw new HttpsError("permission-denied", "Not shared with you");
    return data;
  }
  ```
- [ ] Replace inline sharedWith checks in `getSharedAudioUrl`, `duplicateSharedRecording`, `generateTasksFromSharedRecording` with `requireSharedWith(...)`
- [ ] Extract `batchDeleteShares(ownerUid, itemId, itemType, sharedWith)` helper for the batch-delete pattern used by both soft-delete triggers:
  ```typescript
  async function batchDeleteShares(
    ownerUid: string,
    itemId: string,
    itemType: string,
    sharedWith: string[],
  ): Promise<void>
  ```
- [ ] Replace inline batch-delete logic in `onRecordingSoftDeleted` and `onCollectiveSummarySoftDeleted` with `batchDeleteShares(...)`
- [ ] Verify all Firestore collections and fields match `Android_Developer_Brief.md` S12:
  - `recordings`, `folders`, `actionItems`, `collectiveSummaries` under `users/{uid}/`
  - `sharedWithMe`, `myShares` under `users/{uid}/`
  - `rateLimits/{uid}` at root level
  - `deviceTokens` under `users/{uid}/`
- [ ] Verify no new Firestore collections or fields have been invented (DeveloperGuide S14: "Don't invent Firebase collections or fields")

### Verification (Phase 4)

- [ ] `npm run build` completes with no TypeScript errors in `functions/`
- [ ] All callables still work (share, revoke, dismiss, duplicate, generate tasks, find user, get audio URL)
- [ ] Both soft-delete triggers still fire and clean up correctly
- [ ] Rate limiting still works for `findUserByEmail`
- [ ] No behavior changes — this is a pure refactor

---

## Phase Dependencies

```
Phase 1A ──► Phase 1B ──► Phase 1C ──► Phase 1D ──► Phase 1E
                                            │
                                            ▼
Phase 2A ◄── (verify MoveToFolderDialog after Phase 1A)
Phase 2B     (independent)
Phase 2C     (independent)

Phase 3A     (independent of Phases 1–2)
Phase 3B     (independent of Phases 1–2)

Phase 4A     (independent of all Android phases)
```

- Phase 1 sub-phases are strictly sequential (1A creates shared components, 1B decomposes the screen, 1C fixes the ViewModel, 1D fixes the detail screen using shared components, 1E verifies dialogs)
- Phases 2A–2C can proceed in any order after Phase 1 is complete
- Phase 2A should be done after Phase 1A (verifies `MoveToFolderDialog` changes)
- Phases 3A and 3B are independent of everything and can be done in parallel or in any order
- Phase 4A is independent (TypeScript, no Kotlin)

---

## New Files Created Across All Phases

| Phase | File | Type |
|-------|------|------|
| 1A | `android/.../ui/components/NeedsInternetDialog.kt` | Shared composable |
| 1A | `android/.../ui/components/ProcessingStatusChip.kt` | Shared composable |
| 1B | `android/.../ui/recording/RecordingRow.kt` | Recording composable |
| 1B | `android/.../ui/recording/SummarizationPopup.kt` | Recording composable |
| 1B | `android/.../ui/recording/CompletionToast.kt` | Recording composable |
| 1B | `android/.../ui/recording/MultiSelectTopBar.kt` | Recording composable |
| 1B | `android/.../ui/recording/MultiSelectHintBanner.kt` | Recording composable |
| 1D | `android/.../ui/recording/RecordingContentTabs.kt` | Shared tab composable |
| 2B | `android/.../ui/checklist/DateDeadlineCard.kt` | Extracted composable |
| 2C | `android/.../ui/components/NavigationRow.kt` | Shared composable |
| 3A | `android/.../ui/settings/AccountSection.kt` | Settings section |
| 3A | `android/.../ui/settings/NavigationSection.kt` | Settings section |
| 3A | `android/.../ui/settings/TaskSchedulingSection.kt` | Settings section |
| 3A | `android/.../ui/settings/TimezoneSection.kt` | Settings section |
| 3A | `android/.../ui/settings/IntegrationsSection.kt` | Settings section |
| 3A | `android/.../ui/settings/PrivacySection.kt` | Settings section |
| 3A | `android/.../ui/settings/SyncSection.kt` | Settings section |
| 3A | `android/.../ui/settings/StorageSection.kt` | Settings section |
| 3A | `android/.../ui/settings/DeleteAccountSection.kt` | Settings section |

## Modified Files Across All Phases

| Phase | File | Change Summary |
|-------|------|----------------|
| 1A | `RecordingDialogs.kt` | Add `showFolderIcon` param to `MoveToFolderDialog` |
| 1A | `RecordingsScreen.kt` | Delete `BulkMoveToFolderDialog`, use shared `NeedsInternetDialog`, use shared `ProcessingStatusChip`, refactor clipboard to ViewModel call |
| 1A | `RecordingDetailScreen.kt` | Use shared `NeedsInternetDialog` |
| 1A | `RecordingsViewModel.kt` | Move `NeedsInternetReason` out, add `snackbarMessage`, add `copyTranscriptToClipboard()` |
| 1B | `RecordingsScreen.kt` | Extract 5 composables to own files, fix `VmDimens`, fix `folderId` shadow |
| 1C | `RecordingsViewModel.kt` | Fix `!!`, standardize state updates |
| 1D | `RecordingDetailScreen.kt` | Use shared `RecordingContentTabs` |
| 1D | `RecordingDialogs.kt` | Use shared `RecordingContentTabs` in `TranscriptSheet` |
| 1E | `RecordingDialogs.kt` | Verify dialog patterns |
| 2A | `SharedRecordingDetailScreen.kt` | Extract sub-composables, fix `VmDimens` |
| 2B | `TaskDetailScreen.kt` | Extract `DateDeadlineCard`, fix `VmDimens` |
| 2C | `FoldersScreen.kt` | Use shared `NavigationRow`, fix `VmDimens` |
| 3A | `SettingsScreen.kt` | Decompose into 9 section files, remove `rememberCoroutineScope`, fix `VmDimens`, move re-auth logic to ViewModel |
| 3A | `SettingsViewModel.kt` | Add `initiateGoogleReAuth()`, `deleteError` StateFlow, `onDeleteError()`, `clearDeleteError()` |
| 3B | `RecordingService.kt` | Replace `runBlocking` with cached timezone, verify wake lock timeout |
| 4A | `functions/src/sharing.ts` | Extract `requireAuth`, `requireSharedWith`, `batchDeleteShares` helpers |
