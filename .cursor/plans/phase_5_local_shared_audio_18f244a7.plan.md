---
name: Phase 5 Local Shared Audio
overview: "Implement Phase 5 of the local-first architecture: shared recording audio is downloaded locally for offline playback, and a new device setup screen lets users choose their sync strategy (download everything, on-demand, or metadata-only)."
todos:
  - id: shared-audio-local
    content: "A1-A3: Shared audio local caching (ViewModel + StorageRepository download + dismiss cleanup)"
    status: done
  - id: datastore-keys
    content: "B1: Add deviceSyncStrategy + deviceSetupComplete to NavPreferenceRepository"
    status: done
  - id: setup-screen
    content: "B2-B3: Create DeviceSetupViewModel + DeviceSetupScreen"
    status: done
  - id: bulk-download-worker
    content: "B5: Create BulkDownloadWorker for 'full' strategy"
    status: done
  - id: on-demand-playback
    content: "B6: Update RecordingsViewModel.playAudio() for on-demand download"
    status: done
  - id: wire-setup-navigation
    content: "B7: Wire DeviceSetupScreen into MainActivity lifecycle"
    status: done
isProject: false
---

# Phase 5: Shared Content Local Storage and New Device Setup

## Part A: Shared Audio -- Download and Cache Locally

### A1. Update `SharedRecordingDetailViewModel` for local-first shared audio

File: [SharedRecordingDetailViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt)

- Inject `LocalAudioManager` and `StorageRepository`
- Derive a composite ID: `"${ownerUid}_${recordingId}"` for local file keying
- In the `init` block (where audio URL is fetched around lines 117-129), change the flow to:
  1. Check `localAudioManager.sharedAudioExists(compositeId)`
  2. If local file exists: set `audioUrl` to the local file path, extract waveform from local file
  3. If not: fetch signed URL via `sharingRepository.getSharedAudioUrl()`, download to `localAudioManager.sharedAudioFile(compositeId)` using `StorageRepository` or direct URL download, then set `audioUrl` to the local path
- Update `playOrResume()` / `getAudioUrlRefreshed()` to prefer the local path when available
- Add a `isDownloading: Boolean` field to `SharedRecordingDetailUiState` to show download progress

### A2. Add URL-based download to `StorageRepository`

File: [StorageRepository.kt](android/app/src/main/java/com/voicemind/data/repository/StorageRepository.kt)

- Add a `downloadFromUrl(url: String, destinationFile: File)` method that downloads from a signed URL to a local file (using `java.net.URL.openStream()`)

### A3. Clean up shared audio on dismiss

File: [SharedItemsViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedItemsViewModel.kt)

- Inject `LocalAudioManager`
- In `dismiss(shareId)`, before calling `sharingRepository.dismissSharedItem(shareId)`:
  - Look up the `SharedItemUiModel` from current state to get `ownerUid` + `itemId`
  - Call `localAudioManager.deleteSharedAudio("${ownerUid}_${itemId}")` to free disk space
- Only do this for `itemType == "recording"` items

---

## Part B: New Device / Reinstall Setup

### B1. Add DataStore keys to `NavPreferenceRepository`

File: [NavPreferenceRepository.kt](android/app/src/main/java/com/voicemind/data/repository/NavPreferenceRepository.kt)

- Add keys:
  - `deviceSyncStrategyKey = stringPreferencesKey("device_sync_strategy")`
  - `deviceSetupCompleteKey = booleanPreferencesKey("device_setup_complete")`
- Add flows:
  - `deviceSyncStrategy: Flow<String>` (default `"on_demand"`)
  - `isDeviceSetupComplete: Flow<Boolean>` (default `false`)
- Add setters:
  - `suspend fun setDeviceSyncStrategy(strategy: String)`
  - `suspend fun setDeviceSetupComplete(complete: Boolean)`

### B2. Create `DeviceSetupViewModel`

New file: `android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupViewModel.kt`

- State: selected strategy (`"full"`, `"on_demand"`, `"metadata_only"`)
- On confirm:
  1. Save strategy via `navPreferenceRepository.setDeviceSyncStrategy(strategy)`
  2. Run `initialSyncManager.runIfNeeded()` (hydrates metadata from Firestore to Room)
  3. If strategy is `"full"`: enqueue `BulkDownloadWorker`
  4. Set `navPreferenceRepository.setDeviceSetupComplete(true)`

### B3. Create `DeviceSetupScreen`

New file: `android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupScreen.kt`

- Full-screen composable with:
  - Title: "Set Up Local Storage"
  - Explanation text about local-first benefits
  - Three radio-button options with descriptions:
    - "Download Everything" -- sync all data + audio
    - "Download on Demand" -- sync metadata, audio downloads on first play
    - "Metadata Only" -- sync metadata, audio streams from cloud
  - "Set Up" confirm button
  - Footnote: "This choice cannot be changed without reinstalling the app."
- Follow `Style_Guide_Compose.md`: M3 surfaces, no emoji, Lucide icons where appropriate

### B4. Update `InitialSyncManager` to accept strategy

File: [InitialSyncManager.kt](android/app/src/main/java/com/voicemind/data/sync/InitialSyncManager.kt)

- Current `runIfNeeded()` already hydrates all metadata. No change needed for "on_demand" or "metadata_only" since both just need metadata.
- The distinction between strategies is handled by:
  - `BulkDownloadWorker` (only enqueued for `"full"`)
  - Playback logic in `RecordingsViewModel` (checks strategy at play time)

### B5. Create `BulkDownloadWorker`

New file: `android/app/src/main/java/com/voicemind/data/sync/BulkDownloadWorker.kt`

- `@HiltWorker` `CoroutineWorker`
- Inject `RecordingDao`, `StorageRepository`, `LocalAudioManager`
- Query all recordings where `localAudioPath IS NULL` and `audioPath` is not empty
- For each: download audio from Storage to `LocalAudioManager.saveAudio()`, update Room `localAudioPath`
- Show a foreground notification with progress (X of Y files)
- Network constraint via WorkManager
- Handle errors per-file (skip failed downloads, log, continue)

### B6. Update playback for on-demand download

File: [RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt)

- Inject `NavPreferenceRepository`, `StorageRepository`, `LocalAudioManager`
- In `playAudio()` (lines 157-197), update the fallback path:
  - Current: if no local path, stream from cloud URL
  - New: if no local path AND strategy == `"on_demand"`:
    1. Get download URL
    2. Download to `LocalAudioManager`
    3. Update Room `localAudioPath`
    4. Play from local file
  - If strategy == `"metadata_only"`: keep current streaming behavior (no local save)

### B7. Wire `DeviceSetupScreen` into app navigation

File: [MainActivity.kt](android/app/src/main/java/com/voicemind/MainActivity.kt)

- After `isSignedIn` check, add a `isDeviceSetupComplete` check:
  - Read `navPreferenceRepository.isDeviceSetupComplete`
  - If user is signed in but setup not complete AND Room is empty (check via `recordingDao`): show `DeviceSetupScreen`
  - On setup complete: proceed to `AppNavHost` (existing flow)
- For existing users upgrading: `isInitialSyncComplete == true` means Room has data, so the Room-empty check ensures the setup dialog is not shown to them

### B8. Add route for DeviceSetupScreen

File: [Routes.kt](android/app/src/main/java/com/voicemind/ui/navigation/Routes.kt) (no change needed -- DeviceSetupScreen is shown as a full-screen composable in `MainActivity` directly, not as a nav destination)

---

## File Summary

**Modified files:**

- `SharedRecordingDetailViewModel.kt` -- local-first shared audio
- `SharedItemsViewModel.kt` -- cleanup shared audio on dismiss
- `StorageRepository.kt` -- add URL download method
- `NavPreferenceRepository.kt` -- new DataStore keys
- `RecordingsViewModel.kt` -- on-demand download during playback
- `MainActivity.kt` -- device setup gate

**New files:**

- `ui/setup/DeviceSetupScreen.kt` -- setup screen composable
- `ui/setup/DeviceSetupViewModel.kt` -- setup screen logic
- `data/sync/BulkDownloadWorker.kt` -- full-download worker

