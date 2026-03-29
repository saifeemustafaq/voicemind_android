---
name: Frontend Visual Shells
overview: Build all visual UI shells for the sharing feature (screens, dialogs, navigation, settings section) using hardcoded/mock state, with zero backend dependency and zero risk of breaking existing functionality.
todos:
  - id: routes-nav
    content: Add route constants to Routes.kt and composable entries to AppNavHost.kt
    status: completed
  - id: privacy-settings
    content: Add PRIVACY section with discoverability toggle to SettingsScreen.kt
    status: completed
  - id: shared-items-row
    content: Add pinned Shared Items row to FoldersScreen.kt LazyColumn
    status: completed
  - id: shared-items-screen
    content: Create SharedItemsScreen.kt with empty state and section layout
    status: completed
  - id: share-dialog
    content: Create ShareDialog.kt bottom sheet with email lookup UI shell
    status: completed
  - id: shared-recording-detail
    content: Create SharedRecordingDetailScreen.kt read-only detail shell
    status: completed
  - id: share-menu-items
    content: Add 'Share with User' menu item to RecordingsScreen.kt and RecordingDetailScreen.kt
    status: completed
isProject: false
---

# Frontend Visual Shells for Sharing

## What Is Safe (and What Is Not)

All of this work is **purely additive** -- new files, new routes, new composable entries, and new sections appended to existing screens. Nothing existing gets removed or re-wired. ViewModels use hardcoded/placeholder state so no Firestore queries or Cloud Function calls are made.

### Safe to build now


| Item                        | Phase | Risk | Why safe                                                                                                                                                                |
| --------------------------- | ----- | ---- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Navigation routes           | 4, 6  | None | Adding constants and helper functions to [Routes.kt](android/app/src/main/java/com/voicemind/ui/navigation/Routes.kt)                                                   |
| Nav composable entries      | 4, 6  | None | Adding `composable(...)` blocks to [AppNavHost.kt](android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt) `detailRoutes`                                  |
| Privacy section in Settings | 1     | None | Appending a new section + GlassCard to [SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt), local state for now                  |
| Shared Items row in Folders | 4     | None | Prepending one `item { }` to the `LazyColumn` in [FoldersScreen.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersScreen.kt), before `items(state.folders)` |
| SharedItemsScreen           | 4     | None | Brand new screen file with empty state, section headers, placeholder list                                                                                               |
| ShareDialog                 | 5     | None | Brand new dialog/bottom sheet, not wired to any backend                                                                                                                 |
| SharedRecordingDetailScreen | 6     | None | Brand new read-only detail screen shell, shows placeholder content                                                                                                      |
| "Share with User" menu item | 5     | None | Adds a `DropdownMenuItem` to recording overflow menus, opens ShareDialog (which is a no-op shell)                                                                       |


### NOT safe to build now (skip these)

- **Data models** (`SharedItem.kt`, `MyShare.kt`) -- not visual work; creates unused code
- **SharingRepository** -- calls Cloud Functions that don't exist yet
- **Cross-user reads** in RecordingRepository / ActionItemRepository -- need security rules deployed first
- **Badge count logic** in FoldersViewModel -- needs SharingRepository; use hardcoded 0 instead
- **Any Firestore writes** (discoverable toggle, markAsRead, dismiss) -- connect later

---

## Implementation Details

### 1. Routes (in [Routes.kt](android/app/src/main/java/com/voicemind/ui/navigation/Routes.kt))

Add these constants alongside the existing `FOLDER_DETAIL_ROUTE`, `TASK_DETAIL_ROUTE`, `RECORDING_DETAIL_ROUTE`:

```kotlin
const val SHARED_ITEMS_ROUTE = "shared_items"

const val SHARED_RECORDING_DETAIL_ROUTE = "shared_recording/{ownerUid}/{recordingId}"
fun sharedRecordingDetailRoute(ownerUid: String, recordingId: String) =
    "shared_recording/$ownerUid/$recordingId"
```

### 2. Navigation entries (in [AppNavHost.kt](android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt))

Add two `composable(...)` blocks inside `detailRoutes(...)`, following the same pattern as `FOLDER_DETAIL_ROUTE`:

- `composable(SHARED_ITEMS_ROUTE)` -> `SharedItemsScreen`
- `composable(SHARED_RECORDING_DETAIL_ROUTE)` -> `SharedRecordingDetailScreen`

### 3. Privacy section in Settings (in [SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt))

Insert between the ACCOUNT and INTEGRATIONS sections (matching the PRD Section 28.2). Pattern: `SettingsSectionHeader("PRIVACY")` + `GlassCard` + `Row` with label/description + `Switch`. Use a local `remember { mutableStateOf(true) }` for the toggle state for now -- it will be connected to `UserSettingsRepository.setDiscoverable()` in Phase 1.

### 4. Shared Items row in Folders (in [FoldersScreen.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersScreen.kt))

Add an `item { }` block at line ~109 (before `items(state.folders, key = ...)`) with a `GlassCard` row using `Icons.Default.FolderShared` icon, "Shared Items" title, and a chevron. Tapping calls `navController.navigate(SHARED_ITEMS_ROUTE)`. No badge for now (hardcoded hidden). The row is visually identical to a `FolderRow` but without the rename/delete menu.

### 5. SharedItemsScreen (new file: `ui/sharing/SharedItemsScreen.kt`)

- `VoiceMindTopAppBar` with title "Shared Items", `Icons.Default.FolderShared`, back button
- Empty state (default): `GlassCard` with centered text "Items shared with you by other VoiceMind users will appear here"
- Section headers for "RECORDINGS", "TASKS", "SUMMARIES" (hidden when empty, so only empty state shows for now)
- Follows the screen layout pattern from `Style_Guide.md` Section 9

### 6. ShareDialog (new file: `ui/sharing/ShareDialog.kt`)

A modal bottom sheet (using `ModalBottomSheet` from M3) with:

- `OutlinedTextField` for email input using `voiceMindTextFieldColors()`
- "Find" `PrimaryButton`
- **Result states** (all visual, no backend):
  - Loading: `CircularProgressIndicator`
  - Found user: `GlassCard` showing name + email + "Share" button
  - Not found: text "No user found"
  - Already shared: text "Already shared with this user"
- "Shared with" section: placeholder list of recipients with "Revoke" icon buttons
- All actions are no-ops for now (just close the dialog or show a toast)

### 7. SharedRecordingDetailScreen (new file: `ui/sharing/SharedRecordingDetailScreen.kt`)

A read-only variant modeled after [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt) but stripped of edit actions:

- `Scaffold` + `TopAppBar` with back button only (no overflow menu with rename/delete/move)
- "Shared by [Name]" attribution subtitle (placeholder text for now)
- Audio playback area (time display, play/pause button, waveform) -- visually present but non-functional until signed URL backend exists
- Tabbed or sectioned content: Transcription, Summary, Tasks (all showing placeholder/empty text)
- No edit controls anywhere
- Overflow menu with only "Duplicate" (disabled/no-op for now)

### 8. "Share with User" menu item (in [RecordingsScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsScreen.kt) and [RecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDetailScreen.kt))

Add a new `DropdownMenuItem` with `Icons.Default.PersonAdd` and text "Share with User" after the existing "Share Audio" / "Share Transcript" items. Tapping sets a state flag that opens the `ShareDialog` bottom sheet.

---

## Files Created


| File                                        | Purpose                                        |
| ------------------------------------------- | ---------------------------------------------- |
| `ui/sharing/SharedItemsScreen.kt`           | Shared Items folder screen (empty state shell) |
| `ui/sharing/ShareDialog.kt`                 | Email lookup + share confirmation dialog shell |
| `ui/sharing/SharedRecordingDetailScreen.kt` | Read-only shared recording detail shell        |


## Files Modified


| File                                    | Change                                           |
| --------------------------------------- | ------------------------------------------------ |
| `ui/navigation/Routes.kt`               | Add 2 route constants + helper function          |
| `ui/navigation/AppNavHost.kt`           | Add 2 composable entries in detailRoutes         |
| `ui/settings/SettingsScreen.kt`         | Add PRIVACY section with discoverable toggle     |
| `ui/folders/FoldersScreen.kt`           | Add pinned Shared Items row at top of LazyColumn |
| `ui/recording/RecordingsScreen.kt`      | Add "Share with User" menu item to RecordingRow  |
| `ui/recording/RecordingDetailScreen.kt` | Add "Share with User" menu item to overflow      |


