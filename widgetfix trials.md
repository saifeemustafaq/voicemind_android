# Widget Fix Trials

## Background
Widget shows the mic button (auth works), but tapping the mic does nothing — no UI change, no recording, no feedback.

---

## Trial #1 — Switch actionSendBroadcast → actionStartService + mic permission handling
**Date:** 2026-03-04
**Status:** Partial fix — permission prompt appeared correctly, but widget didn't clear "Microphone access needed" after permission was granted

### Root causes identified

**Cause A — Wrong action dispatch path:**
Original flow: `actionSendBroadcast` → `RecordingWidgetReceiver.onReceive` → `ContextCompat.startForegroundService`. On Glance 1.1.1 + targetSdk 35, this indirect path has an extra failure surface: the exception thrown by `startForegroundService` is silently caught in both the receiver and `handleStart()`, so the user sees nothing. The original dev intended `actionStartService` per the git commit message but never completed the switch.

**Cause B — RECORD_AUDIO permission never requested via widget path:**
The permission is only ever requested from `RecordFab` inside the main app. A user who signed in but never tapped the in-app record button has no mic permission. The service starts, `audioRecorder.start()` throws `SecurityException`, caught in `handleStart()`, `resetAndStop()` fires → widget silently stays idle.

### Changes made

| File | Change |
|---|---|
| `RecordingWidget.kt` | Replaced all `actionSendBroadcast` with `actionStartService(..., isForegroundService = true)` |
| `RecordingWidget.kt` | Simplified `RecordingWidgetReceiver` — removed custom `onReceive` and companion |
| `RecordingWidget.kt` | Added `MicPermissionContent` composable |
| `RecordingWidgetStateKeys.kt` | Added `NEEDS_MIC_PERMISSION` key |
| `RecordingService.kt` | `handleStart()` checks `RECORD_AUDIO` before starting recorder |
| `RecordingService.kt` | `pushWidgetState()` always writes `NEEDS_MIC_PERMISSION` |
| `VoiceMindApp.kt` | `pushWidgetState()` also writes `NEEDS_MIC_PERMISSION` on auth change |

---

## Trial #2 — Real-time widget sync on app resume (permission state)
**Date:** 2026-03-04
**Status:** Deployed — awaiting test result

### Root cause identified

**Widget state not refreshed after permission grant:**
After the user taps "Open App" on the widget and grants mic permission in the system dialog, the widget still shows "Microphone access needed". The widget DataStore state is only updated on:
1. Auth state changes (`FirebaseAuth.AuthStateListener`) — does NOT fire on permission grants
2. `RecordingService.pushWidgetState()` — service is not running in idle state

There is no existing trigger that fires when the user returns from the permission dialog or from the app.

### Root cause analysis

Permission grants are OS-level events with no broadcast intent. The only reliable way to detect that permission state may have changed is to re-check it whenever the app becomes active again — specifically when `Activity.onResume()` fires, which happens:
- When the permission dialog dismisses (activity resumes)
- When the user navigates back from system settings
- When the app is opened from the widget or launcher
- On any foreground transition

### Fix

Registered `Application.ActivityLifecycleCallbacks` in `VoiceMindApp.observeAppForegroundForWidget()`. On every `onActivityResumed`, `pushWidgetState` is called with the current auth + live `checkSelfPermission` result.

| File | Change |
|---|---|
| `VoiceMindApp.kt` | Added `observeAppForegroundForWidget()` — registers `ActivityLifecycleCallbacks`; on `onActivityResumed` calls `pushWidgetState` with live permission + auth state |

### Why `onActivityResumed` and not something else

| Hook | Why not used |
|---|---|
| `ACTION_APPLICATION_SETTINGS_CHANGED` broadcast | Not emitted for permission grants |
| `AppOpsManager` listener | Requires API 30+, complex setup |
| `ProcessLifecycleOwner.onStart` | Same signal but requires `lifecycle-process` dep |
| Manual polling | Anti-pattern, wasteful |
| `onActivityResumed` ✓ | Fires exactly when the user returns to the app from any context, no extra deps |

### Expected result
- User taps "Open App" on widget → app opens → user grants mic → navigates back → `onActivityResumed` fires → `pushWidgetState` writes `NEEDS_MIC_PERMISSION = false` → widget re-renders showing mic button
- Works for permission granted via in-app dialog AND via system Settings
- Works for permission revoked (widget will revert to "Microphone access needed")

---

*Add new trials below if this does not resolve the issue.*
