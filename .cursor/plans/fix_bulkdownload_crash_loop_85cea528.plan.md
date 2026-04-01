---
name: Fix BulkDownload Crash Loop
overview: The app is in a crash loop because WorkManager's internal SystemForegroundService does not declare foregroundServiceType="dataSync" in the manifest. When BulkDownloadWorker calls setForeground(), the system kills the process for the missing type. Since WorkManager persists and retries the work across restarts, the crash repeats on every launch.
todos:
  - id: manifest-service-type
    content: Add SystemForegroundService override with foregroundServiceType="dataSync" in AndroidManifest.xml
    status: pending
  - id: try-catch-setforeground
    content: Wrap setForeground() calls in BulkDownloadWorker.doWork() with try/catch to prevent process crash
    status: pending
  - id: deploy-and-recover
    content: Deploy from Android Studio to break the crash loop
    status: pending
isProject: false
---

# Fix BulkDownloadWorker Crash Loop

## Why the App Is Stuck in a Crash Loop

The previous fix added the `FOREGROUND_SERVICE_DATA_SYNC` permission and passed `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` to `ForegroundInfo`. But that is only **two of three** requirements on Android 14+ (API 34, your targetSdk=35).

The missing third requirement: **WorkManager's internal `SystemForegroundService` must declare `foregroundServiceType="dataSync"` in the manifest `<service>` element.**

WorkManager runs all foreground workers via its own service (`androidx.work.impl.foreground.SystemForegroundService`). The WorkManager 2.10.0 library declares this service in its own manifest, but does NOT include `dataSync` in the service type. When the `BulkDownloadWorker` calls `setForeground()`, WorkManager tells this service to call `Service.startForeground(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`. The Android system then checks the manifest, sees the service doesn't declare `dataSync`, and throws `MissingForegroundServiceTypeException` -- which kills the process.

Because WorkManager persists work requests in its own SQLite database, the `BulkDownloadWorker` is retried on every app restart, creating an infinite crash loop.

```mermaid
sequenceDiagram
    participant App as App Process
    participant WM as WorkManager
    participant SFS as SystemForegroundService
    participant OS as Android OS

    App->>WM: App launches
    WM->>WM: Sees pending BulkDownloadWorker
    WM->>SFS: Start foreground service
    SFS->>OS: "startForeground(id, notif, DATA_SYNC)"
    OS->>OS: Check manifest for service type
    Note over OS: Service does NOT declare<br/>foregroundServiceType="dataSync"
    OS->>App: MissingForegroundServiceTypeException
    Note over App: PROCESS KILLED
    Note over WM: Work still pending...<br/>Retry on next launch
```

## Fix (2 changes + 1 recovery step)

### 1. Declare the foreground service type on WorkManager's service

In [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml), add a `<service>` override that merges with WorkManager's declaration:

```xml
<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:foregroundServiceType="dataSync"
    tools:node="merge" />
```

This merges with WorkManager's own manifest and adds `dataSync` as an allowed foreground service type.

### 2. Wrap `setForeground()` in try/catch as a safety net

In [BulkDownloadWorker.kt](android/app/src/main/java/com/voicemind/data/sync/BulkDownloadWorker.kt), wrap the `setForeground()` calls in try/catch so that if the foreground service still can't start for any reason (e.g., app is in background on API 31+, missing notification permission, etc.), the worker falls back to running as a regular background job instead of crashing the process.

The initial `setForeground()` on line 33 and the progress updates on line 48 both need wrapping.

### 3. Break the current crash loop

After deploying the fix from Android Studio, the crash loop should break automatically because the new code handles the foreground service correctly. If the app still won't start, the user can go to **Android System Settings > Apps > VoiceMind > Storage > Clear Data** to clear WorkManager's persisted state.
