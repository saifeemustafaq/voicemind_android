---
name: Phase 6 Storage Management
overview: Add a storage management section to Settings with storage breakdown, cache clearing, full data reset, and a one-time user consent dialog for the local-first storage model.
todos:
  - id: datastore-consent-key
    content: Add local_storage_consent_shown DataStore key to NavPreferenceRepository
    status: done
  - id: settings-vm-storage
    content: Add StorageInfo state, inject LocalAudioManager/AppDatabase/context, implement refreshStorageInfo/clearSharedAudioCache/clearAllLocalData in SettingsViewModel
    status: done
  - id: settings-screen-storage
    content: Add STORAGE section to SettingsScreen with breakdown rows, clear buttons, and confirmation dialogs
    status: done
  - id: consent-dialog
    content: Add one-time local storage consent dialog in MainActivity
    status: done
isProject: false
---

# Phase 6: Storage Management + User Consent

## Context

Phase 6 is the final phase of the local-first architecture migration. The app already stores audio files in `filesDir/audio/` and `filesDir/shared_audio/` via [LocalAudioManager.kt](android/app/src/main/java/com/voicemind/data/local/LocalAudioManager.kt), and metadata in Room (`voicemind.db`). `LocalAudioManager` already exposes `getOwnAudioSizeBytes()`, `getSharedAudioSizeBytes()`, `clearSharedAudio()`, and `clearAllAudio()`.

**Storage permission** is already handled -- the app uses `context.filesDir` (app-private internal storage), which requires no runtime permission on any API level. No changes needed.

## 1. Storage Usage Section in Settings

Add a **STORAGE** section to [SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt) (between the existing SYNC section and the footer). It will display:

- **Total storage used** (formatted via `Formatter.formatFileSize`)
- **Breakdown rows**: own recordings audio, shared recordings audio, database
- **"Clear Shared Audio Cache" button** (FilledTonalButton) -- deletes `filesDir/shared_audio/`*
- **"Clear All Local Data" button** (FilledTonalButton, errorContainer colors) -- full reset that re-triggers device setup

### SettingsViewModel changes

In [SettingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt):

- Inject `LocalAudioManager`, `AppDatabase`, and `@ApplicationContext context: Context`
- Add a `StorageInfo` data class:

```kotlin
data class StorageInfo(
    val ownAudioBytes: Long = 0,
    val sharedAudioBytes: Long = 0,
    val databaseBytes: Long = 0,
) {
    val totalBytes: Long get() = ownAudioBytes + sharedAudioBytes + databaseBytes
}
```

- Expose `storageInfo: StateFlow<StorageInfo>` -- computed on init and refreshed after clear operations
- Add `refreshStorageInfo()` -- recalculates sizes from `LocalAudioManager` + `context.getDatabasePath("voicemind.db").length()`
- Add `clearSharedAudioCache()` -- calls `localAudioManager.clearSharedAudio()`, then `refreshStorageInfo()`
- Add `clearAllLocalData()` -- calls `localAudioManager.clearAllAudio()` + `appDatabase.clearAllTables()` + resets `device_setup_complete` and `initial_sync_complete` in `NavPreferenceRepository` to `false`. The UI automatically transitions to `DeviceSetupScreen` via the existing DataStore gate in [MainActivity.kt](android/app/src/main/java/com/voicemind/MainActivity.kt) (lines 99-112)

### SettingsScreen UI

Add after the SYNC `GlassCard`, before the version footer:

```
STORAGE (SettingsSectionHeader)
┌─ GlassCard ──────────────────────────────────────────┐
│ Total: 245.3 MB                                      │
│ ── HorizontalDivider ──                              │
│ Own recordings     198.1 MB                          │
│ Shared audio        42.7 MB                          │
│ Database             4.5 MB                          │
│ ── HorizontalDivider ──                              │
│ [Clear Shared Audio Cache]  (FilledTonalButton)      │
│ [Clear All Local Data]      (FilledTonalButton/error) │
└──────────────────────────────────────────────────────┘
```

Both clear buttons show an `AlertDialog` for confirmation before executing. The "Clear All Local Data" dialog warns that this will remove all local recordings and re-trigger setup.

## 2. User Consent Dialog

On first app launch after the local-first migration, show a one-time informational dialog explaining local storage.

### NavPreferenceRepository

Add a new DataStore key in [NavPreferenceRepository.kt](android/app/src/main/java/com/voicemind/data/repository/NavPreferenceRepository.kt):

- `local_storage_consent_shown: Boolean` (default `false`)
- Expose `isLocalStorageConsentShown: Flow<Boolean>` and `suspend fun setLocalStorageConsentShown(shown: Boolean)`

### MainActivity

In [MainActivity.kt](android/app/src/main/java/com/voicemind/MainActivity.kt), inside the `if (isSignedIn)` block (after the device setup gate, before `AppNavHost`):

- Collect `navPreferenceRepository.isLocalStorageConsentShown`
- If `false`, show an `AlertDialog` with:
  - Title: "Local Storage Enabled"
  - Body: Explains the app now stores data locally for instant playback and offline access. Users can manage storage in Settings.
  - Single "Got It" dismiss button that calls `setLocalStorageConsentShown(true)`
- This dialog should appear *after* the device setup screen (existing users who already have data will never see the setup screen but will see this consent dialog once)

## Files Changed Summary


| File                         | Change                                                                                              |
| ---------------------------- | --------------------------------------------------------------------------------------------------- |
| `NavPreferenceRepository.kt` | Add `local_storage_consent_shown` key + flow + setter                                               |
| `SettingsViewModel.kt`       | Inject `LocalAudioManager`, `AppDatabase`, context; add `StorageInfo`, storage state, clear methods |
| `SettingsScreen.kt`          | Add STORAGE section with breakdown, clear buttons, confirmation dialogs                             |
| `MainActivity.kt`            | Add one-time consent dialog                                                                         |


