---
name: Simplify Storage Setup Fix Crash
overview: Simplify the DeviceSetupScreen to remove the 3-option strategy picker in favor of a single consent-and-go flow that always uses the "full" strategy, and fix the crash caused by a missing foreground service type declaration in BulkDownloadWorker.
todos:
  - id: fix-crash-manifest
    content: Add FOREGROUND_SERVICE_DATA_SYNC permission to AndroidManifest.xml
    status: pending
  - id: fix-crash-worker
    content: Pass FOREGROUND_SERVICE_TYPE_DATA_SYNC to ForegroundInfo in BulkDownloadWorker.buildForegroundInfo()
    status: pending
  - id: simplify-setup-ui
    content: Rewrite DeviceSetupScreen to show consent checkbox + Set Up button, remove StrategyOption cards
    status: pending
  - id: simplify-viewmodel
    content: Update DeviceSetupViewModel to remove strategy selection, always use 'full', add hasAgreed state
    status: pending
  - id: merge-consent
    content: Mark localStorageConsentShown=true in DeviceSetupViewModel.confirm() to skip redundant dialog
    status: pending
  - id: update-defaults
    content: Change NavPreferenceRepository.deviceSyncStrategy default from 'on_demand' to 'full'
    status: pending
isProject: false
---

# Simplify Local Storage Setup and Fix Crash

## Root Cause of the Crash

The app targets **SDK 35** (Android 15). When the user selects "Download Everything" and taps "Set Up", the `confirm()` method enqueues a `BulkDownloadWorker`. This worker calls `setForeground()` to run as a foreground service, but:

1. **Missing manifest permission**: `FOREGROUND_SERVICE_DATA_SYNC` is not declared in [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml) (only `FOREGROUND_SERVICE_MICROPHONE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` exist).
2. **Missing foreground service type in ForegroundInfo**: [BulkDownloadWorker.kt](android/app/src/main/java/com/voicemind/data/sync/BulkDownloadWorker.kt) constructs `ForegroundInfo(NOTIFICATION_ID, notification)` without passing `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`. On Android 14+ (API 34+), this throws `MissingForegroundServiceTypeException` and kills the process.

This is why the app "closes" shortly after the user taps "Set Up" -- the WorkManager job starts, tries to create a foreground service without proper declaration, and the OS terminates the process.

## Changes

### 1. Fix the BulkDownloadWorker crash

**[AndroidManifest.xml](android/app/src/main/AndroidManifest.xml)**

- Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`

**[BulkDownloadWorker.kt](android/app/src/main/java/com/voicemind/data/sync/BulkDownloadWorker.kt)**

- Pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` as third argument to `ForegroundInfo(NOTIFICATION_ID, notification, ...)` in `buildForegroundInfo()`

### 2. Simplify the setup screen UI

**[DeviceSetupScreen.kt](android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupScreen.kt)**

Replace the current 3-option strategy picker with a simple consent screen:

- **Title**: "Set Up Local Storage"
- **Description**: Explain that VoiceMind will store recordings and metadata locally for instant playback and offline access, data is always backed up to the cloud, and the user can manage storage in Settings.
- **Checkbox**: "I understand and agree" -- must be checked to enable the button
- **Button**: "Set Up" (disabled until checkbox is checked)
- **Remove**: All three `StrategyOption` composables and the `StrategyOption` function entirely
- **Remove**: The "This choice cannot be changed without reinstalling" text (irrelevant when there's no choice)

### 3. Simplify the setup ViewModel

**[DeviceSetupViewModel.kt](android/app/src/main/java/com/voicemind/ui/setup/DeviceSetupViewModel.kt)**

- Remove `selectStrategy()` method
- Remove `selectedStrategy` from `DeviceSetupUiState` (replace with `hasAgreed: Boolean = false`)
- Add `toggleAgreed()` method to flip the checkbox state
- In `confirm()`: always hard-code strategy as `"full"` -- no conditional on `enqueueBulkDownload()`, it always runs
- Add a `try/catch` around `enqueueBulkDownload()` so a WorkManager enqueue failure doesn't block setup completion

### 4. Merge the redundant consent dialog

**[MainActivity.kt](android/app/src/main/java/com/voicemind/MainActivity.kt)**

Currently, after the setup screen completes, a second "Local Storage Enabled" `AlertDialog` pops up (lines 261-285). This is redundant since the new setup screen already contains that consent messaging.

- In the `confirm()` method of `DeviceSetupViewModel`, also mark `navPreferenceRepository.setLocalStorageConsentShown(true)` so the user doesn't see the same message twice in a row.

### 5. Simplify playback strategy references (optional cleanup)

**[RecordingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingsViewModel.kt)** (lines 160-184)

Since the strategy is now always "full", the `playAudio` method's `when` block can be simplified. The `"on_demand"` branch can remain as a fallback (for recordings not yet bulk-downloaded), but the `"metadata_only"` streaming branch is effectively dead code. Keeping both branches is fine for safety; the key improvement is that default behavior is always "download and play locally."

**[NavPreferenceRepository.kt](android/app/src/main/java/com/voicemind/data/repository/NavPreferenceRepository.kt)**

- Change the default for `deviceSyncStrategy` from `"on_demand"` to `"full"` so even if the DataStore key is somehow missing, the app defaults to full offline mode.

