---
name: Phase 3 Data Layer
overview: Implement the Android Sharing Data Layer (Phase 3) -- create SharedItem and MyShare data models, a full SharingRepository with snapshot listeners and callable wrappers, and add cross-user observation methods to RecordingRepository and ActionItemRepository.
todos:
  - id: create-shared-item-model
    content: Create `data/model/SharedItem.kt` data class with @DocumentId, @ServerTimestamp, matching sharedWithMe Firestore schema
    status: completed
  - id: create-my-share-model
    content: Create `data/model/MyShare.kt` data class with @DocumentId, @ServerTimestamp, matching myShares Firestore schema
    status: completed
  - id: create-sharing-repository
    content: "Create `data/repository/SharingRepository.kt` with 9 methods: observeSharedWithMe, observeMyShares, findUserByEmail, shareItem, revokeShare, dismissSharedItem, getSharedAudioUrl, markAsRead, getUnreadCount"
    status: completed
  - id: update-recording-repo
    content: Add `observeSharedRecording(ownerUid, recordingId)` to RecordingRepository.kt for cross-user recording reads
    status: completed
  - id: update-action-item-repo
    content: Add `observeActionItemsForRecording(ownerUid, recordingId)` to ActionItemRepository.kt for cross-user action item reads
    status: completed
  - id: verify-build
    content: Run gradle build to verify everything compiles and Hilt injection resolves correctly
    status: completed
isProject: false
---

# Phase 3: Android Sharing Data Layer

Phase 3 is pure data infrastructure -- no UI changes. It creates all the data models and repository methods that Phases 4-6 will consume. Every new file follows the existing `@Singleton` + `@Inject constructor` Hilt pattern already established in the codebase.

## Prerequisite Check

- Phase 1 (User Profile): Complete -- `users/{uid}` profiles exist, discoverable toggle works.
- Phase 2 (Cloud Functions & Rules): Complete -- all 5 callables deployed (`findUserByEmail`, `shareItem`, `revokeShare`, `dismissSharedItem`, `getSharedAudioUrl`), cross-user read rules for `recordings`, `collectiveSummaries`, `actionItems` are in `firestore.rules`.
- No action items on the user's end.

## Files to Create (3 new files)

### 1. `SharedItem.kt` -- Inbox model

**Path:** `android/app/src/main/java/com/voicemind/data/model/SharedItem.kt`

Maps to Firestore documents in `users/{uid}/sharedWithMe/{shareId}`. Follows the exact same `data class` + `@DocumentId` + `@ServerTimestamp` pattern as `Recording.kt` and `ActionItem.kt`.

```kotlin
data class SharedItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val itemType: String = "",       // "recording" or "collectiveSummary"
    val itemId: String = "",
    @ServerTimestamp val sharedAt: Timestamp? = null,
    val isRead: Boolean = false,
)
```

### 2. `MyShare.kt` -- Outbox model

**Path:** `android/app/src/main/java/com/voicemind/data/model/MyShare.kt`

Maps to `users/{uid}/myShares/{shareId}`.

```kotlin
data class MyShare(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val recipientEmail: String = "",
    val itemType: String = "",
    val itemId: String = "",
    @ServerTimestamp val sharedAt: Timestamp? = null,
)
```

### 3. `SharingRepository.kt` -- Central sharing data layer

**Path:** `android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt`

`@Singleton` class with `@Inject constructor(firestore, functions, authRepository)` -- mirrors the pattern in [RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt) and [ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt).

**Methods (9 total):**


| Method                                      | Implementation                                                                                                             |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `observeSharedWithMe()`                     | `callbackFlow` snapshot listener on `users/{uid}/sharedWithMe`, ordered by `sharedAt` DESC, emits `Flow<List<SharedItem>>` |
| `observeMyShares(itemId)`                   | `callbackFlow` snapshot listener on `users/{uid}/myShares` filtered by `itemId`, emits `Flow<List<MyShare>>`               |
| `findUserByEmail(email)`                    | Calls `findUserByEmail` callable, returns `Task<Map<String, Any>>`                                                         |
| `shareItem(itemId, itemType, recipientUid)` | Calls `shareItem` callable                                                                                                 |
| `revokeShare(shareId, recipientUid)`        | Calls `revokeShare` callable                                                                                               |
| `dismissSharedItem(shareId)`                | Calls `dismissSharedItem` callable                                                                                         |
| `getSharedAudioUrl(ownerUid, recordingId)`  | Calls `getSharedAudioUrl` callable                                                                                         |
| `markAsRead(shareId)`                       | Direct Firestore update: sets `isRead = true` on `users/{uid}/sharedWithMe/{shareId}`                                      |
| `getUnreadCount()`                          | `callbackFlow` snapshot listener on `sharedWithMe` where `isRead == false`, emits `Flow<Int>` (count)                      |


Key patterns to follow:

- Use `callbackFlow` + `awaitClose { registration.remove() }` for snapshot listeners (same as existing repos)
- Use `functions.getHttpsCallable("name").call(data)` for callable wrappers (same as `RecordingRepository.generateSummary`)
- `requireNotNull(authRepository.currentUser)` for UID access (same pattern as existing repos)
- Callable methods return `Task<Map<String, Any>>` rather than suspend functions, keeping the async model consistent with Firebase Tasks API. Alternatively, use `suspend` + `.await()` and return parsed results -- I'll follow the `suspend` + `.await()` pattern to stay consistent with the rest of the codebase (e.g., `RecordingRepository.generateSummary`)

## Files to Modify (2 existing files)

### 4. `RecordingRepository.kt` -- Add cross-user observation

**Path:** [android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/RecordingRepository.kt)

Add one new method:

```kotlin
fun observeSharedRecording(ownerUid: String, recordingId: String): Flow<Recording?> = callbackFlow {
    val registration = firestore.document("users/$ownerUid/recordings/$recordingId")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "observeSharedRecording")
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Recording::class.java))
        }
    awaitClose { registration.remove() }
}
```

This bypasses the `collection()` helper (which is scoped to the current user) and reads directly from `users/{ownerUid}/recordings/{recordingId}`. The cross-user read is permitted by the security rule added in Phase 2 (checks `sharedWith` array).

### 5. `ActionItemRepository.kt` -- Add cross-user task observation

**Path:** [android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt](android/app/src/main/java/com/voicemind/data/repository/ActionItemRepository.kt)

Add one new method:

```kotlin
fun observeActionItemsForRecording(ownerUid: String, recordingId: String): Flow<List<ActionItem>> = callbackFlow {
    val registration = firestore.collection("users/$ownerUid/actionItems")
        .whereEqualTo("recordingId", recordingId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "observeActionItemsForRecording")
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(ActionItem::class.java) ?: emptyList())
        }
    awaitClose { registration.remove() }
}
```

Same approach -- bypasses `collection()` to read from another user's `actionItems`. Permitted by the Phase 2 `actionItems` cross-user read rule.

## No DI Module Changes Needed

Since `SharingRepository` uses `@Singleton` + `@Inject constructor` with dependencies that are already provided by [AppModule.kt](android/app/src/main/java/com/voicemind/di/AppModule.kt) (`FirebaseFirestore`, `FirebaseFunctions`, plus `AuthRepository` which is also constructor-injected), Hilt resolves the dependency graph automatically. No changes to `AppModule.kt` are required.

## No UI Changes

Phase 3 is explicitly data-layer only. The existing UI files (`SharedItemsScreen.kt`, `ShareDialog.kt`, `SharedRecordingDetailScreen.kt`) remain as-is with their placeholder/hardcoded states until Phase 4-6.

## Verification After Implementation

1. **Build succeeds** -- all 3 new files compile, no errors
2. **Hilt injection works** -- `SharingRepository` can be injected into any `@HiltViewModel`
3. **Snapshot listeners compile** -- `observeSharedWithMe`, `observeMyShares`, `getUnreadCount` use proper `callbackFlow` pattern
4. **Cross-user reads compile** -- `observeSharedRecording` and `observeActionItemsForRecording` reference correct Firestore paths
5. **Callable wrappers compile** -- all 5 callable methods match the exact function names in `functions/src/sharing.ts`: `findUserByEmail`, `shareItem`, `revokeShare`, `dismissSharedItem`, `getSharedAudioUrl`

