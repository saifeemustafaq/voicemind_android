# Code Guidelines Compliance — Implementation Phases

Each phase is self-contained: once complete, it does not need to be revisited. Phases are ordered by dependency risk — self-contained internal refactors first, then ViewModel internals, then cross-file changes, then backend. A developer should complete every checklist item in a phase before moving on.

---

## Phase 1: Safe Cosmetic Fixes (0 cross-file impact)

**Goal:** Fix three isolated guideline violations — duplicated composables, a banned `!!` assertion, and emoji in UI text. These changes are purely internal to their files with no public API, state shape, or function signature changes. Nothing outside these files needs to update.

### 1a. RecordingWidget.kt — Extract duplicated prompt composable

`SignedOutContent()` and `MicPermissionContent()` are near-identical: both render a "VoiceMind" title, a subtitle, and a styled button. They differ only in subtitle text, button label, and intent extras.

**Guideline violated:** DeveloperGuide §1 — "Never duplicate logic... extract into a shared function." / DeveloperGuide §4 — "Parameterize, don't duplicate."

**Downstream impact:** None. `RecordingService.kt` and `VoiceMindApp.kt` only call `RecordingWidget().update(...)` and write to `RecordingWidgetStateKeys` — they never touch these private composables. `RecordingWidgetReceiver` in the same file is unaffected.

- [x] Extract a shared `private fun PromptContent(subtitle: String, buttonLabel: String, intent: Intent)` composable that renders the common title + subtitle + button layout
- [x] Replace `SignedOutContent()` body with a call to `PromptContent(subtitle = "Sign in to record", buttonLabel = "Sign In", intent = ...)`
- [x] Replace `MicPermissionContent()` body with a call to `PromptContent(subtitle = "Microphone access needed", buttonLabel = "Open App", intent = ...)`
- [x] Confirm both `SignedOutContent()` and `MicPermissionContent()` functions still exist as named wrappers calling `PromptContent` (preserves readability in `WidgetRoot`'s `when` block)

**File:** `android/app/src/main/java/com/voicemind/widget/RecordingWidget.kt`

### 1b. SharedSummaryDetailScreen.kt — Remove `!!` assertion

Line 169 uses `state.summary!!` inside an else-branch. Even though the branch guarantees non-null, the `!!` operator is banned by the coding guidelines.

**Guideline violated:** DeveloperGuide §2 — "Avoid `!!` (not-null assertion). Only allowed if proven impossible to be null and documented."

**Downstream impact:** None. The screen is only referenced by `AppNavHost.kt` which passes `ownerUid`, `summaryId`, and `onBack` — none change.

- [x] Replace the `else -> { val summary = state.summary!! ... }` block with a safe alternative: either restructure as `state.summary?.let { summary -> ... }` that returns early, or use `val summary = checkNotNull(state.summary)` with no message needed since the `when` branch already establishes non-null context
- [x] Verify the full `else ->` branch content is preserved — the `Column` with `RichText`, Sources section, and error display must remain identical

**File:** `android/app/src/main/java/com/voicemind/ui/sharing/SharedSummaryDetailScreen.kt`

### 1c. SummariesScreen.kt — Remove emoji from UI text

Line 320 uses `\u2728` (sparkle emoji) in instructional text inside the `SummariesInfoSheet` composable.

**Guideline violated:** DeveloperGuide §1 / Style_Guide — "Don't use emoji in UI, copy, or code; use Material Icons."

**Downstream impact:** None. Text-only change in a private composable.

- [x] Change `"Tap the \u2728 Summarize button in the toolbar."` to `"Tap the Summarize button in the toolbar."`

**File:** `android/app/src/main/java/com/voicemind/ui/summaries/SummariesScreen.kt`

### Verification

- [ ] `./gradlew assembleDebug` builds without errors
- [ ] Widget renders correctly in both signed-out and mic-permission states (visual check)
- [ ] SharedSummaryDetailScreen displays summary content without crashes
- [ ] SummariesInfoSheet steps text reads naturally without emoji

---

## Phase 2: ViewModel Internal Refactors (0 state shape changes)

**Goal:** Eliminate duplicated logic inside two ViewModels by extracting shared private helpers. The `UiState` data classes and all public method signatures stay identical, so consuming screens are completely unaffected.

### 2a. SharedRecordingDetailViewModel.kt — Extract MediaPlayer setup helper

`playOrResume()` (lines 174-206) and `retryWithFreshUrl()` (lines 330-363) both construct a `MediaPlayer` with nearly identical listener wiring: `setDataSource`, `setOnPreparedListener`, `setOnCompletionListener`, `setOnErrorListener`, and `prepareAsync()`.

**Guideline violated:** DeveloperGuide §1 — DRY / DeveloperGuide §14 — "Don't copy-paste code across features."

**Key concern:** `playOrResume` includes retry logic in its error listener (`urlRetryCount`), while `retryWithFreshUrl` does not. The extracted helper must accept the error listener as a lambda parameter so callers can customize error behavior.

**Downstream impact:** None. `SharedRecordingDetailUiState` is unchanged. `SharedRecordingDetailScreen.kt` only reads state and calls public methods — all of which keep their signatures.

**Connected files (read-only check):** `SharedRecordingDetailScreen.kt` consumes this ViewModel via `hiltViewModel()`. No changes needed there.

- [x] Create a `private fun buildAndPreparePlayer(url: String, onError: (what: Int, extra: Int) -> Boolean): MediaPlayer` that encapsulates: `setDataSource(url)`, common `setOnPreparedListener` (start playback, update state), common `setOnCompletionListener` (cancel polling, reset state), the passed-in `onError` lambda, and `prepareAsync()`
- [x] Refactor `playOrResume()` to call `buildAndPreparePlayer(url) { what, extra -> /* retry logic with urlRetryCount */ }`
- [x] Refactor `retryWithFreshUrl()` to call `buildAndPreparePlayer(url) { what, extra -> /* no retry, just set error state */ }`
- [x] Remove the duplicated `MediaPlayer().apply { ... }` blocks from both methods
- [x] Verify `releaseMediaPlayer()` is still called before each `buildAndPreparePlayer` call (existing behavior preserved)

**File:** `android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt`

### 2b. SettingsViewModel.kt — Extract shared reauth-then-delete flow

`reauthAndDeleteWithGoogle()` and `reauthAndDelete()` duplicate the identical pattern: set state to `Deleting` -> launch on `Dispatchers.IO` -> call reauth -> on success call `deleteAccount()` -> handle success/failure.

**Guideline violated:** DeveloperGuide §1 — DRY / DeveloperGuide §14 — "Don't copy-paste code across features."

**Downstream impact:** None. `SettingsScreen.kt` calls `reauthAndDeleteWithGoogle(idToken)` and `reauthAndDelete(email, password)` — both keep their signatures. `DeleteAccountState` and `StorageInfo` data classes are unchanged.

**Connected files (read-only check):** `SettingsScreen.kt` is the sole consumer. No changes needed there.

- [x] Create a `private fun executeReauthAndDelete(reauthBlock: suspend () -> Result<Unit>)` that encapsulates: setting `_deleteState.value = DeleteAccountState.Deleting`, launching on `Dispatchers.IO`, calling `reauthBlock()`, chaining `.onSuccess { authRepository.deleteAccount().onSuccess/onFailure }` and `.onFailure { set Error state }`
- [x] Refactor `reauthAndDeleteWithGoogle(idToken)` to: `executeReauthAndDelete { authRepository.reauthenticateWithGoogle(idToken) }`
- [x] Refactor `reauthAndDelete(email, password)` to: `executeReauthAndDelete { authRepository.reauthenticateWithEmail(email, password) }`
- [x] Verify both public method signatures remain unchanged: `fun reauthAndDeleteWithGoogle(idToken: String)` and `fun reauthAndDelete(email: String, password: String)`

**File:** `android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt`

### Verification

- [ ] `./gradlew assembleDebug` builds without errors
- [ ] Shared recording playback works: play, pause, resume, seek, speed change, retry-on-error
- [ ] Account deletion flow works for both email/password and Google re-auth paths
- [ ] No regressions in settings screen (navigation mode, timezone, storage, tasks connect/disconnect)

---

## Phase 3: Cross-File Refactors

**Goal:** Fix two guideline violations that require coordinated edits across multiple files. These changes affect the navigation host and the auth flow.

### 3a. AppNavHost.kt — Hoist duplicated LaunchedEffect

The `LaunchedEffect(openSharedItemsOnStart)` block is copy-pasted identically in both the sidebar and bottom-bar branches. Both navigate to `SHARED_ITEMS_ROUTE` via `navController.navigate(...)`.

**Guideline violated:** DeveloperGuide §1 — DRY.

**Note on recordings deep-link:** The recordings deep-link LaunchedEffect **differs** between branches — sidebar calls `navigateTo(Routes.Recordings)`, bottom-bar calls `pagerState.scrollToPage(recIndex)`. This one cannot be hoisted and stays duplicated. A brief comment should explain why.

**Downstream impact:** `MainActivity.kt` is the sole caller of `AppNavHost`. Its signature does not change. Behavior is preserved.

- [x] Move the `LaunchedEffect(openSharedItemsOnStart) { ... }` block above the `if (useSidebar) { ... } else { ... }` branch (both branches use `navController.navigate(SHARED_ITEMS_ROUTE)` identically)
- [x] Remove the duplicate `LaunchedEffect(openSharedItemsOnStart)` from both the sidebar and bottom-bar branches
- [x] Add a brief comment above the recordings deep-link LaunchedEffects in each branch explaining they differ by implementation (navigateTo vs scrollToPage) and cannot be hoisted

**File:** `android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt`

### 3b. SignInScreen.kt + AuthViewModel.kt — Move Google credential flow to ViewModel

The Google Sign-In `onClick` handler (lines 212-236 of `SignInScreen.kt`) is 25 lines of inline coroutine logic in the composable: it creates a `CredentialManager`, builds a request, extracts the `idToken`, and calls `viewModel.signInWithGoogle(idToken)`.

**Guideline violated:** DeveloperGuide §4 — "Composables should be 'mostly pure': derive UI from state. Side effects go in LaunchedEffect, DisposableEffect, or the ViewModel."

**Key concern:** `AuthViewModel` currently has `signInWithGoogle(idToken: String)` which takes a pre-extracted token. The new method wraps the credential manager flow around it, so the existing method stays untouched. `MainActivity.kt` also uses `AuthViewModel` but only calls `signOut()`, `registerFcmToken()`, and reads `isSignedIn` — none of which change.

**Connected files (verified safe):**
- `MainActivity.kt` — uses `AuthViewModel` for `signOut()`, `registerFcmToken()`, `isSignedIn` only. Unaffected.
- `SignInScreen.kt` — the only file that calls the Google credential flow.
- `AuthViewModel.kt` — gains one new public method; no existing methods change.

#### AuthViewModel.kt changes

- [x] Add `import android.app.Activity` and credential manager imports (`CredentialManager`, `GetCredentialRequest`, `GetGoogleIdOption`, `GoogleIdTokenCredential`, `GetCredentialCancellationException`, `NoCredentialException`)
- [x] Add new public method `fun signInWithGoogleCredential(activity: Activity)` that: creates `CredentialManager`, builds `GetGoogleIdOption` with `filterByAuthorizedAccounts(false)` and `serverClientId(WEB_CLIENT_ID)`, calls `credentialManager.getCredential(activity, request)`, extracts `idToken`, calls existing `signInWithGoogle(idToken)`. Catches `GetCredentialCancellationException` (no-op), `NoCredentialException` (calls `setError("No Google accounts found...")`), and generic `Exception` (calls `setError("Google Sign-In failed: ${e.message}")`)
- [x] Verify existing `signInWithGoogle(idToken: String)` method is unchanged

#### SignInScreen.kt changes

- [x] Replace the 25-line inline `scope.launch { try { ... } }` Google Sign-In lambda with a single call: `viewModel.signInWithGoogleCredential(context as Activity)`
- [x] Remove unused imports that were only needed by the inline credential flow: `CredentialManager`, `GetCredentialRequest`, `GetGoogleIdOption`, `GoogleIdTokenCredential`, `GetCredentialCancellationException`, `NoCredentialException`, `rememberCoroutineScope`, `kotlinx.coroutines.launch`, `AuthRepository` (if no longer used)
- [x] Remove the `val scope = rememberCoroutineScope()` line if it's no longer used elsewhere in the composable
- [x] Verify the `OutlinedButton(onClick = { viewModel.signInWithGoogleCredential(context as Activity) }, ...)` compiles and the button remains enabled/disabled based on `!uiState.isLoading`

**Files:**
- `android/app/src/main/java/com/voicemind/ui/auth/AuthViewModel.kt`
- `android/app/src/main/java/com/voicemind/ui/auth/SignInScreen.kt`

### Verification

- [ ] `./gradlew assembleDebug` builds without errors
- [ ] Notification deep-link to Shared Items works from both navigation modes (sidebar and bottom bar)
- [ ] Google Sign-In flow works end-to-end: tap button -> Google account picker -> signed in
- [ ] Google Sign-In cancellation is handled gracefully (no crash, no error shown)
- [ ] "No Google accounts" scenario shows appropriate error message
- [ ] Email/password sign-in remains unaffected

---

## Phase 4: Backend TypeScript Refactors (0 API changes)

**Goal:** Clean up the `googleTasks.ts` Cloud Function file by extracting a repeated error-casting pattern and splitting a 141-line function into focused helpers. No exported function names, parameters, or return types change. The Android client is completely unaffected.

### 4a. googleTasks.ts — Extract error helper

The error-casting pattern `const e = err as { code?: number; status?: number }; if (e.code === 401 || e.status === 401)` appears 7 times across the file.

**Guideline violated:** DRY — repeated boilerplate that should be a shared utility.

- [x] Add a `function getHttpCode(err: unknown): number | undefined` helper near the top of the file (below imports, above OAuth helper) that casts `err` to `{ code?: number; status?: number }` and returns `e.code ?? e.status`
- [x] Replace all 7 inline error-cast-and-check patterns with `getHttpCode(err)`:
  - `createAndStoreTask` catch block (line ~95)
  - `deleteGoogleTask` catch block (line ~113)
  - `createAndStoreCalendarEvent` catch block (line ~142)
  - `deleteCalendarEvent` catch block (line ~161)
  - `syncActionItemToGoogleTasks` task update catch block (line ~349)
  - `syncActionItemToGoogleTasks` calendar update catch block (line ~377)
  - (verify count matches — search for `as { code?:` to find all instances)

### 4b. googleTasks.ts — Split syncActionItemToGoogleTasks

`syncActionItemToGoogleTasks` is 141 lines and handles: metadata guard, soft delete cascade, hard delete, date-removed cleanup, Google Tasks sync (create/update), and Google Calendar sync (create/update/delete). This can be decomposed into focused helpers.

**Guideline violated:** Functions should be single-responsibility. 141 lines with multiple concerns makes the logic harder to follow and maintain.

- [x] Extract `handleSoftDelete(tasks, calendar, event, taskId, calEventId, uid)` — handles the soft-delete branch (lines ~292-302): deletes Google Task and Calendar event, clears IDs from Firestore document
- [x] Extract `syncTaskToGoogle(tasks, event, taskBody, taskId, itemId, uid)` — handles the Google Tasks create-or-update logic (lines ~343-361): attempts update, falls back to create on 404, handles token expiry on 401
- [x] Extract `syncCalendarEvent(calendar, event, title, dueDate, notes, calEventId, itemId, uid)` — handles the Google Calendar create-or-update-or-delete logic (lines ~364-390): creates/updates event for dueDate items, deletes event when dueDate is cleared
- [x] Verify the main `syncActionItemToGoogleTasks` body now reads as a clear decision tree: guard → get token → soft delete? → hard delete? → date removed? → sync task → sync calendar
- [x] Verify all three exported functions remain unchanged: `exchangeTasksAuthCode`, `disconnectTasks`, `syncActionItemToGoogleTasks`
- [x] Verify `functions/src/index.ts` re-export (`export * from "./googleTasks.js"`) needs no changes

### Verification

- [ ] `npm run build` in `functions/` completes without TypeScript errors
- [ ] `functions/lib/googleTasks.js` is regenerated (compiled output)
- [ ] Deployed function names remain: `exchangeTasksAuthCode`, `disconnectTasks`, `syncActionItemToGoogleTasks`
- [ ] No changes to function call signatures (Android `getHttpsCallable()` calls unaffected)

---

## Deferred: Hardcoded Dimensions Sweep

Several files use raw `dp` values where `VmDimens` constants exist. These are low-severity cosmetic issues spread across many files. This sweep should be done after Phases 1-4 are verified, to keep diffs focused and reviewable.

### Mapping reference

| Raw value | VmDimens constant | Context |
|-----------|-------------------|---------|
| `4.dp` | `VmDimens.SpaceXs` | Small gaps |
| `8.dp` | `VmDimens.SpaceSm` | Section gaps |
| `12.dp` | `VmDimens.SpaceMd` | Medium spacing |
| `16.dp` | `VmDimens.SpaceLg` or `VmDimens.ScreenHorizontalPadding` | Horizontal padding, section gaps |
| `24.dp` | `VmDimens.SpaceXl` | Large padding |
| `32.dp` | `VmDimens.SpaceXxl` | Extra-large padding |
| `48.dp` | `VmDimens.SpaceXxxl` or `VmDimens.TouchTarget` | Touch targets |

### Affected files

| File | Examples of hardcoded values |
|------|------------------------------|
| `ChecklistScreen.kt` | `padding(horizontal = 16.dp)` should be `VmDimens.ScreenHorizontalPadding` |
| `SummariesScreen.kt` | `24.dp`, `32.dp` in info sheet |
| `SignInScreen.kt` | `24.dp`, `80.dp`, `48.dp`, `16.dp` mixed with `VmDimens` |
| `TimeZonePickerDialog.kt` | `8.dp`, `20.dp`, `14.dp`, `12.dp` throughout |
| `SharedItemsScreen.kt` | `16.dp`, `14.dp` in row padding |

---

## Phase Dependencies

```
Phase 1  (cosmetic, isolated)
  ├── 1a  RecordingWidget.kt         ── no dependencies
  ├── 1b  SharedSummaryDetailScreen.kt ── no dependencies
  └── 1c  SummariesScreen.kt         ── no dependencies

Phase 2  (ViewModel internals, isolated)
  ├── 2a  SharedRecordingDetailViewModel.kt ── no dependencies
  └── 2b  SettingsViewModel.kt       ── no dependencies

Phase 3  (cross-file, coordinated)
  ├── 3a  AppNavHost.kt              ── verify with MainActivity.kt
  └── 3b  SignInScreen.kt            ── edit AuthViewModel.kt together

Phase 4  (backend, isolated)
  └── 4a  googleTasks.ts             ── verify build, no client changes

Deferred: Hardcoded dimensions sweep (5+ files)
```

- Phases 1-4 are sequential by risk but items within each phase are independent
- Phase 3b requires editing two files together (`SignInScreen.kt` + `AuthViewModel.kt`)
- Phase 4 is completely independent of Phases 1-3 (different platform)
- The deferred dimensions sweep can be done any time after all phases are verified

---

## Appendix: Files Modified Per Phase

| Phase | File | Change Summary |
|-------|------|----------------|
| 1a | `android/.../widget/RecordingWidget.kt` | Extract shared `PromptContent` composable |
| 1b | `android/.../ui/sharing/SharedSummaryDetailScreen.kt` | Replace `!!` with safe null handling |
| 1c | `android/.../ui/summaries/SummariesScreen.kt` | Remove `\u2728` emoji from text |
| 2a | `android/.../ui/sharing/SharedRecordingDetailViewModel.kt` | Extract `buildAndPreparePlayer` helper |
| 2b | `android/.../ui/settings/SettingsViewModel.kt` | Extract `executeReauthAndDelete` helper |
| 3a | `android/.../ui/navigation/AppNavHost.kt` | Hoist shared-items `LaunchedEffect` |
| 3b | `android/.../ui/auth/SignInScreen.kt` | Remove inline credential manager flow |
| 3b | `android/.../ui/auth/AuthViewModel.kt` | Add `signInWithGoogleCredential(activity)` method |
| 4a | `functions/src/googleTasks.ts` | Extract `getHttpCode` helper, split sync function |

## Appendix: Files Verified But Not Modified

These files were checked to confirm our changes don't break them. No edits needed.

| Phase | File | Why Checked |
|-------|------|-------------|
| 1a | `android/.../service/RecordingService.kt` | Calls `RecordingWidget().update()` — unaffected by internal composable refactor |
| 1a | `android/.../VoiceMindApp.kt` | Writes `RecordingWidgetStateKeys` — unaffected |
| 1b | `android/.../ui/navigation/AppNavHost.kt` | Hosts `SharedSummaryDetailScreen` — no signature change |
| 2a | `android/.../ui/sharing/SharedRecordingDetailScreen.kt` | Consumes `SharedRecordingDetailViewModel` — no state shape change |
| 2b | `android/.../ui/settings/SettingsScreen.kt` | Consumes `SettingsViewModel` — no public API change |
| 3a | `android/.../MainActivity.kt` | Sole caller of `AppNavHost` — no signature change |
| 3b | `android/.../MainActivity.kt` | Uses `AuthViewModel` for `signOut()`/`registerFcmToken()` — unaffected |
| 4a | `functions/src/index.ts` | Re-exports `googleTasks.ts` — no exported names change |
