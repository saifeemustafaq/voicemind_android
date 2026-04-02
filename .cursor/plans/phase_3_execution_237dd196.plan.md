---
name: Phase 3 Execution
overview: "Execute Phase 3 of the code standards audit: decompose SettingsScreen.kt (~750-line composable) into 9 section files with MVVM boundary fixes, and fix RecordingService.kt's runBlocking anti-pattern with a cached timezone approach. Then mark completed items in codephase.md."
todos:
  - id: 3a-sections
    content: "Phase 3A: Create 9 section composable files (AccountSection, NavigationSection, TimezoneSection, TaskSchedulingSection, PrivacySection, IntegrationsSection, SyncSection, StorageSection, DeleteAccountSection)"
    status: pending
  - id: 3a-mvvm
    content: "Phase 3A: Move Google Credential Manager re-auth logic + deleteError state to SettingsViewModel"
    status: pending
  - id: 3a-refactor
    content: "Phase 3A: Refactor SettingsScreen.kt to compose section calls, remove unused scope, fix VmDimens"
    status: pending
  - id: 3b-runblocking
    content: "Phase 3B: Replace runBlocking with cached timezone in RecordingService.kt"
    status: pending
  - id: 3b-verify
    content: "Phase 3B: Verify wake lock timeout, scope.cancel, mediaSession.release, logging (already correct)"
    status: pending
  - id: mark-complete
    content: Mark completed Phase 3 items in codephase.md
    status: pending
isProject: false
---

# Phase 3: Isolated Large Files

## Phase 3A -- Decompose `SettingsScreen.kt` (913 LOC)

### Current State

[SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt) has a single `SettingsScreen` composable spanning ~750 lines (84-833) containing 9 inline sections, MVVM boundary violations, unused imports, and hardcoded dp values.

### Violations to Fix

- **DeveloperGuide S4:** Composable exceeds ~~200 lines (~~750 lines)
- **DeveloperGuide S5 (MVVM):** Google Credential Manager re-auth logic lives in `LaunchedEffect` (lines 118-135) instead of ViewModel
- **DeveloperGuide S2:** Unused `rememberCoroutineScope()` on line 104 (imported but `scope` is never called)
- **Patterns_Guide S10:** Hardcoded `16.dp` on line 179 instead of `VmDimens.ScreenHorizontalPadding`
- **Patterns_Guide S10:** Multiple hardcoded dp values throughout (16.dp, 8.dp, 24.dp spacing) should use VmDimens

### Section Extraction Plan

Extract 9 `internal` composable files under `ui/settings/`. Each section owns its local dialog state (e.g., `showTimeZonePicker` moves from SettingsScreen into TimezoneSection). Each section takes relevant ViewModel state + callbacks as parameters -- no ViewModel reference passed directly.


| New File                   | Lines Extracted | Owns Dialog State                                               |
| -------------------------- | --------------- | --------------------------------------------------------------- |
| `AccountSection.kt`        | 181-199         | None                                                            |
| `NavigationSection.kt`     | 201-336         | None                                                            |
| `TimezoneSection.kt`       | 338-375         | `showTimeZonePicker`                                            |
| `TaskSchedulingSection.kt` | 377-477         | `showNtsTimePicker`                                             |
| `PrivacySection.kt`        | 479-505         | None                                                            |
| `IntegrationsSection.kt`   | 507-549         | None                                                            |
| `SyncSection.kt`           | 551-587         | None                                                            |
| `StorageSection.kt`        | 589-676         | `showClearSharedConfirmDialog`, `showClearAllConfirmDialog`     |
| `DeleteAccountSection.kt`  | 680-820         | `showDeleteConfirmDialog`, `showReAuthDialog`, `reAuthPassword` |


### MVVM Boundary Fix

Move Google Credential Manager re-auth logic into [SettingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt):

- Add `initiateGoogleReAuth(activity: Activity)` method that builds the `GetCredentialRequest`, calls `credentialManager.getCredential()`, extracts the `idToken`, and calls `reauthAndDeleteWithGoogle(idToken)`
- Add `private val _deleteError = MutableStateFlow<String?>(null)` and `val deleteError: StateFlow<String?>` to replace the composable-local `deleteError` state
- Add `clearDeleteError()` method
- The `LaunchedEffect(deleteState)` in the composable becomes ~10 lines: when `NeedsReAuth`, call `settingsViewModel.initiateGoogleReAuth(context as Activity)` or `showReAuthDialog = true`; when `Error`, call `settingsViewModel.setDeleteError()`

### Cleanup

- Remove unused `rememberCoroutineScope()` import and variable (line 52, 104)
- Replace `16.dp` on line 179 with `VmDimens.ScreenHorizontalPadding`
- Replace spacing dp values with VmDimens equivalents where appropriate: `16.dp` -> `VmDimens.SpaceLg`, `8.dp` -> `VmDimens.SpaceSm`, `24.dp` -> `VmDimens.SpaceXl`, `4.dp` -> `VmDimens.SpaceXs`, `2.dp` -> `VmDimens.SpaceXxs`
- Icon sizes (22.dp, 28.dp, 18.dp, 14.dp) and stroke widths (2.dp, 1.5.dp) remain as literal dp values (component-specific, no VmDimens equivalent)

### Post-Decomposition Target

- `SettingsScreen.kt` ~150 lines: state collection, `LaunchedEffect` handlers, consent launcher, `VoiceMindTopAppBar`, section composable calls, error snackbars, version footer
- Each section file ~40-100 lines
- Private helpers `SettingsSectionHeader`, `StorageRow`, `NtsTimePickerDialog`, `formatTime` remain in `SettingsScreen.kt` (or move with their respective sections if only used there)

---

## Phase 3B -- Fix `RecordingService.kt` (499 LOC)

### Current State

[RecordingService.kt](android/app/src/main/java/com/voicemind/service/RecordingService.kt) is a foreground service with one `runBlocking` anti-pattern and otherwise correct structure.

### Violations to Fix

- **DeveloperGuide S3:** `runBlocking` on line 217 blocks the calling thread to read timezone preference

### Fix: Replace `runBlocking` with Cached Timezone

```kotlin
@Volatile
private var cachedTimezone: TimeZone = TimeZone.getDefault()
```

In the service's `onCreate()`, after `setupMediaSession()`, launch a coroutine that collects `navPreferenceRepository.appTimezone` and stores it in `cachedTimezone`:

```kotlin
scope.launch(Dispatchers.IO) {
    navPreferenceRepository.appTimezone.collect { tzId ->
        cachedTimezone = TimeZone.getTimeZone(tzId)
    }
}
```

In `handleStopSave()`, replace lines 217-219:

```kotlin
// Before (blocking):
val fallbackTz = runBlocking {
    java.util.TimeZone.getTimeZone(navPreferenceRepository.appTimezone.first())
}

// After (cached):
val fallbackTz = cachedTimezone
```

Remove the `import kotlinx.coroutines.runBlocking` (line 53).

### Verification Checklist (items already correct)

- Wake lock has 4-hour timeout: `acquire(4 * 60 * 60 * 1000L)` on line 373 -- already correct
- `scope.cancel()` called in `onDestroy()` line 470 -- already correct
- `mediaSession?.release()` called in `onDestroy()` line 472 -- already correct
- No PII in logs -- already correct (only logs recordingId, event names, error messages)

---

## Marking Completed Items

After implementation, update [codephase.md](codephase.md) Phase 3 checklist items from `[ ]` to `[x]`, and mark the Phase 3 verification items.