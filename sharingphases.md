# Shared Items Feature — Implementation Phases

Each phase is self-contained: once complete, it does not need to be revisited. Phases are ordered by dependency. A developer should complete every checklist item in a phase before moving on.

---

## Phase 1: User Profile Infrastructure & Discoverability

**Goal:** Every user gets a Firestore profile document with `displayName`, `email`, `photoUrl`, and `discoverable`. Existing users are backfilled on next sign-in. A privacy toggle is available in Settings.

### Backend

- [x] Create `onUserCreated` Auth `onCreate` trigger in `functions/src/userProfile.ts`
  - Reads `displayName`, `email`, `photoURL` from the `UserRecord`
  - Writes to `users/{uid}` with `merge: true`
  - Sets `discoverable: true`
- [x] Add `tasksTokens/{uid}` deny rule to `firestore.rules` (currently missing — only `calendarTokens` is denied)

### Android — Data Layer

- [x] Update `AuthRepository.kt`: after every successful sign-in (email/password and Google), write `displayName` and `email` to `users/{uid}` with `merge: true` using Firestore
- [x] Update `UserSettingsRepository.kt`: add `observeDiscoverable(): Flow<Boolean>` (snapshot listener on `users/{uid}`, reads `discoverable` field, defaults to `true` when absent)
- [x] Update `UserSettingsRepository.kt`: add `setDiscoverable(enabled: Boolean)` (writes `discoverable` field)

### Android — UI

- [x] Update `SettingsScreen.kt`: add a **"PRIVACY"** section between Account and Integrations
  - Label: "Allow others to find me by email"
  - Description: "When enabled, other VoiceMind users can find you by your email address to share recordings with you"
  - `Switch` bound to `discoverable` state from `UserSettingsRepository` (local state for now — will be wired in Phase 1 backend)
- [x] Update `SettingsViewModel` (or create if needed) to expose discoverable state and toggle action

### Verification

- [ ] New user sign-up creates profile document with all four fields
- [ ] Existing user sign-in backfills `displayName` and `email`
- [ ] Toggling discoverability writes to Firestore and reflects in UI immediately
- [ ] `tasksTokens` collection is denied to clients in rules

---

## Phase 2: Sharing Cloud Functions & Security Rules

**Goal:** All server-side sharing operations are deployed and testable. Security rules allow cross-user reads for shared documents. No Android UI changes in this phase.

### Security Rules

- [x] Update `firestore.rules` — add cross-user read rule for recordings:
  ```
  match /users/{uid}/recordings/{recordingId} {
    allow read: if request.auth != null
      && request.auth.uid in resource.data.sharedWith;
  }
  ```
- [x] Update `firestore.rules` — add cross-user read rule for collective summaries:
  ```
  match /users/{uid}/collectiveSummaries/{summaryId} {
    allow read: if request.auth != null
      && request.auth.uid in resource.data.sharedWith;
  }
  ```
- [x] Update `firestore.rules` — add cross-user read rule for action items (needed by Phase 3/6 `observeActionItemsForRecording`):
  ```
  match /users/{uid}/actionItems/{actionItemId} {
    allow read: if request.auth != null
      && request.auth.uid in resource.data.sharedWith;
  }
  ```
- [x] Keep the existing `users/{uid}/{document=**}` owner rule unchanged (it still grants full access to the owner)
- [ ] Deploy updated rules and verify owner access is unaffected

### Cloud Functions

- [x] `findUserByEmail` (callable)
  - Input: `{ email: string }`
  - Uses Firebase Auth Admin `getUserByEmail(email)`
  - If not found → `{ found: false }`
  - If found, reads `users/{uid}` and checks `discoverable` (default `true` when absent)
  - If not discoverable → `{ found: false }`
  - If discoverable → `{ found: true, uid, displayName, email }`
  - Rate limiting: max 10 lookups per minute per caller (Firestore-based, cross-instance)

- [x] `shareItem` (callable)
  - Input: `{ itemId: string, itemType: "recording" | "collectiveSummary", recipientUid: string }`
  - Verifies caller owns the item (path check against `request.auth.uid`)
  - Verifies recipient exists (`users/{recipientUid}` doc)
  - Checks for duplicate share (prevents re-sharing same item to same user)
  - Adds `recipientUid` to document's `sharedWith` array via `arrayUnion`
  - Creates inbox entry: `users/{recipientUid}/sharedWithMe/{shareId}` with fields: `ownerUid`, `ownerName`, `ownerEmail`, `itemType`, `itemId`, `sharedAt` (server timestamp), `isRead` (false)
  - Creates outbox entry: `users/{callerUid}/myShares/{shareId}` with fields: `recipientUid`, `recipientName`, `recipientEmail`, `itemType`, `itemId`, `sharedAt`
  - Uses same auto-generated `shareId` for both entries
  - If `itemType == "recording"`: also adds `recipientUid` to `sharedWith` on all `actionItems` where `recordingId == itemId`
  - Returns `{ success: true, shareId }`

- [x] `revokeShare` (callable)
  - Input: `{ shareId: string, recipientUid: string }`
  - Reads `users/{callerUid}/myShares/{shareId}` to get `itemId`, `itemType`, and `recipientUid`
  - Validates that request `recipientUid` matches the stored value (security fix — prevents cross-recipient manipulation)
  - Removes `recipientUid` from document's `sharedWith` array via `arrayRemove`
  - Deletes `users/{recipientUid}/sharedWithMe/{shareId}`
  - Deletes `users/{callerUid}/myShares/{shareId}`
  - If recording: also removes from linked actionItems' `sharedWith`

- [x] `dismissSharedItem` (callable)
  - Input: `{ shareId: string }`
  - Reads `users/{callerUid}/sharedWithMe/{shareId}` to get `ownerUid`, `itemId`, `itemType`
  - Removes caller UID from document's `sharedWith` array via `arrayRemove`
  - Deletes `users/{callerUid}/sharedWithMe/{shareId}`
  - Deletes `users/{ownerUid}/myShares/{shareId}`
  - If recording: also removes from linked actionItems' `sharedWith`

- [x] `getSharedAudioUrl` (callable)
  - Input: `{ ownerUid: string, recordingId: string }`
  - Verifies `request.auth` exists
  - Reads `users/{ownerUid}/recordings/{recordingId}`
  - Verifies caller's UID is in `sharedWith` array
  - Generates signed URL (1-hour expiry) for the audio file via Admin Storage SDK
  - Returns `{ url: string }`
  - Error cases: not found → `not-found`; not in sharedWith → `permission-denied`

### Verification

- [ ] Test each callable via Firebase console or curl with auth token
- [ ] Confirm cross-user reads work only when UID is in `sharedWith`
- [ ] Confirm cross-user reads fail when UID is not in `sharedWith`
- [ ] Confirm owner access remains fully functional

---

## Phase 3: Android Sharing Data Layer

**Goal:** Android app has all data models and repository methods needed for sharing. No UI changes yet — this phase is pure data infrastructure.

### Data Models

- [x] Create `data/model/SharedItem.kt`
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

- [x] Create `data/model/MyShare.kt`
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

### Repository

- [x] Create `data/repository/SharingRepository.kt` with `@Singleton` and `@Inject constructor`
  - `observeSharedWithMe(): Flow<List<SharedItem>>` — snapshot listener on `users/{uid}/sharedWithMe`, ordered by `sharedAt` descending
  - `observeMyShares(itemId: String): Flow<List<MyShare>>` — snapshot listener on `users/{uid}/myShares` filtered by `itemId`
  - `findUserByEmail(email: String): Task<Map<String, Any>>` — calls `findUserByEmail` callable
  - `shareItem(itemId: String, itemType: String, recipientUid: String): Task<Map<String, Any>>` — calls `shareItem` callable; returns `shareId`
  - `revokeShare(shareId: String, recipientUid: String): Task<Map<String, Any>>` — calls `revokeShare` callable
  - `dismissSharedItem(shareId: String): Task<Map<String, Any>>` — calls `dismissSharedItem` callable
  - `getSharedAudioUrl(ownerUid: String, recordingId: String): Task<Map<String, Any>>` — calls `getSharedAudioUrl` callable
  - `markAsRead(shareId: String)` — updates `isRead` to `true` on `users/{uid}/sharedWithMe/{shareId}`
  - `getUnreadCount(): Flow<Int>` — snapshot listener filtered by `isRead == false`, emits count

- [x] Update `RecordingRepository.kt`: add `observeSharedRecording(ownerUid: String, recordingId: String): Flow<Recording?>` — snapshot listener on `users/{ownerUid}/recordings/{recordingId}` (cross-user read, permitted by updated security rules)

- [x] Update `ActionItemRepository.kt`: add `observeActionItemsForRecording(ownerUid: String, recordingId: String): Flow<List<ActionItem>>` — snapshot listener on `users/{ownerUid}/actionItems` where `recordingId == recordingId` (cross-user read for shared recording tasks)

### Verification

- [ ] All repository methods compile and can be injected via Hilt
- [ ] Snapshot listeners work for `sharedWithMe` collection (test with manually created Firestore documents)
- [ ] Cross-user recording read works when `sharedWith` array contains the reader's UID

---

## Phase 4: Shared Items Folder UI

**Goal:** Users see a pinned "Shared Items" virtual folder at the top of the Folders screen. Tapping it opens a screen showing items shared with them, grouped by type. Unread badge is visible. Recipients can dismiss items.

### Navigation

- [x] Add route constant in `Routes.kt`: `const val SHARED_ITEMS_ROUTE = "shared_items"`
- [x] Add `composable(SHARED_ITEMS_ROUTE)` in `AppNavHost.kt` `detailRoutes`, navigating to `SharedItemsScreen`

### Folders Screen

- [x] Update `FoldersScreen.kt`: add a pinned "Shared Items" row at the very top of the `LazyColumn`, before the `items(state.folders)` block
  - Uses `Icons.Default.FolderShared` icon
  - Cannot be renamed, deleted, or reordered
  - Tapping navigates to `SHARED_ITEMS_ROUTE`
  - Badge count hardcoded hidden for now (will use `SharingRepository.getUnreadCount()` in Phase 4)
- [x] Update `FoldersViewModel.kt`: expose `sharedItemsUnreadCount: StateFlow<Int>` sourced from `SharingRepository.getUnreadCount()`

### Shared Items Screen

- [x] Create `ui/sharing/SharedItemsScreen.kt`
  - Top app bar: "Shared Items" title, `FolderShared` icon, back button
  - Three `FilterChip` pill tabs (Recordings / Tasks / Summaries) **always visible** at the top, pinned above a `LazyColumn`; tapping a pill shows only that type; each tab has its own per-tab empty state (e.g., "No shared recordings yet")
  - Default selected tab: Recordings

- [x] Create `ui/sharing/SharedItemsViewModel.kt`
  - Observes `SharingRepository.observeSharedWithMe()`
  - For each `SharedItem` of type "recording", fetches the recording title via cross-user read (parallel with `async/awaitAll`)
  - Exposes grouped UI state: `recordings`, `summaries`, `tasks` (placeholder, populated in Phase 9)
  - Exposes `selectedTab: SharedItemsTab` (Recordings/Tasks/Summaries enum) with `selectTab()` function
  - Handles dismiss action
  - Handles marking items as read on screen open

### Verification

- [ ] Shared Items row always appears first in Folders list
- [ ] Badge shows correct unread count and clears when folder is opened
- [ ] Empty state shows when no items are shared
- [ ] Recordings tab shows items when recordings are shared
- [ ] Dismiss removes item from list and cleans up backend via Cloud Function
- [ ] Real-time updates: new shares appear immediately via snapshot listener

---

## Phase 5: Share Flow UI (Sender Side)

**Goal:** Users can share recordings with other VoiceMind users via email lookup. They can see who a recording is shared with and revoke access.

### Recording Share Action

- [x] Update recording context menu / overflow menu (in `RecordingsScreen.kt` and `RecordingDetailScreen.kt`): add "Share with User" action with `PersonAdd` icon
- [x] Tapping "Share with User" opens `ShareDialog`

### Share Dialog

- [x] Create `ui/sharing/ShareDialog.kt`
  - Modal bottom sheet (`ModalBottomSheet`)
  - **Email lookup form**: `OutlinedTextField` with `voiceMindTextFieldColors()`, "Find" `PrimaryButton`
  - **Loading state**: `CircularProgressIndicator`
  - **User found**: `GlassCard` with placeholder name/email + "Share" button (no-op)
  - **User not found**: "No user found" text
  - **Already shared**: "Already shared with this user" text
  - **Shared with section**: placeholder recipient list with `PersonRemove` revoke buttons
  - All actions are no-ops — wired to `ShareViewModel` in Phase 5

- [x] Create `ui/sharing/ShareViewModel.kt`
  - `findUser(email: String)` — calls `SharingRepository.findUserByEmail`; handles self-share, already-shared, and `FirebaseFunctionsException` error codes
  - `shareItem()` — calls `SharingRepository.shareItem`; catches `ALREADY_EXISTS` from Cloud Function
  - `revokeShare(shareId: String, recipientUid: String)` — calls `SharingRepository.revokeShare`; per-button loading via `isRevoking: Set<String>`
  - Exposes state: `LookupState` sealed interface (Idle/Loading/Found/NotFound/AlreadyShared/Error), `isSharing`, `shareSuccess`, `shareError`, `myShares`, `isRevoking`

### Manage Shares

- [x] In `ShareDialog`: show list of current recipients for this item
  - Sourced from `SharingRepository.observeMyShares(itemId)`
  - Each row shows recipient name, email, "Revoke" button with per-button `CircularProgressIndicator`
  - Revoking calls `SharingRepository.revokeShare(shareId, recipientUid)`

### Verification

- [ ] Email lookup finds discoverable users and returns "No user found" for non-discoverable or non-existent users
- [ ] Sharing creates inbox/outbox entries and updates `sharedWith` array
- [ ] Duplicate share attempt is rejected gracefully
- [ ] Revoke removes access and cleans up all entries
- [ ] Recipient's Shared Items updates in real time when share is created or revoked

---

## Phase 6: Shared Recording Detail Screen & Audio Playback

**Goal:** Recipients can view shared recordings in a read-only detail screen and play audio via signed URLs.

### Navigation

- [x] Add route constants in `Routes.kt`:
  ```kotlin
  const val SHARED_RECORDING_DETAIL_ROUTE = "shared_recording/{ownerUid}/{recordingId}"
  fun sharedRecordingDetailRoute(ownerUid: String, recordingId: String) =
      "shared_recording/$ownerUid/$recordingId"
  ```
- [x] Add `composable(SHARED_RECORDING_DETAIL_ROUTE)` in `AppNavHost.kt` `detailRoutes`
- [x] Wire navigation from `SharedItemsScreen`: tapping a recording item navigates to `sharedRecordingDetailRoute(ownerUid, recordingId)` (deferred to Phase 4 when real data exists)

### Shared Recording Detail Screen

- [x] Create `ui/sharing/SharedRecordingDetailScreen.kt`
  - Top app bar: recording title, back button (no edit/delete/move options)
  - **Attribution**: "Shared by [ownerName]" subtitle below the title
  - **Audio playback**: calls `SharingRepository.getSharedAudioUrl(ownerUid, recordingId)` to get a signed URL, passes to `MediaPlayer`
  - **Playback UI**: reuse existing playback components (time display, play/pause, seek) but source audio from signed URL instead of local Storage path
  - **Transcription tab/section**: displays transcription text (read-only, no edit)
  - **Summary tab/section**: displays summary text (read-only)
  - **Tasks section**: read-only list of tasks associated with this recording (from `ActionItemRepository.observeActionItemsForRecording`)
  - **No edit actions**: no rename, no delete, no move, no re-share
  - **URL expiry handling**: if playback exceeds 1 hour, request a new signed URL

- [x] Create `ui/sharing/SharedRecordingDetailViewModel.kt`
  - Takes `ownerUid` and `recordingId` from SavedStateHandle
  - Observes recording via `RecordingRepository.observeSharedRecording(ownerUid, recordingId)`
  - Fetches signed audio URL via `SharingRepository.getSharedAudioUrl`
  - Observes tasks via `ActionItemRepository.observeActionItemsForRecording(ownerUid, recordingId)`
  - Exposes read-only state: `recording`, `audioUrl`, `tasks`, `isLoading`

### Verification

- [ ] Shared recording detail screen displays all content correctly (title, transcription, summary, tasks)
- [ ] Audio plays from signed URL
- [ ] All content is read-only — no edit/delete options available
- [ ] Live updates: if owner edits recording title/summary/transcription, changes reflect for recipient
- [ ] Back navigation works correctly

---

## Phase 7: Recording Deletion Cascade

**Goal:** When an owner deletes a recording, all sharing references are automatically cleaned up across all recipients.

### Cloud Function

- [x] Create `onRecordingDeleted` Firestore trigger in `functions/src/sharing.ts`
  - Trigger path: `users/{uid}/recordings/{recordingId}` — on delete
  - Reads the deleted document's data from `event.data.data()` (v2 API)
  - If `sharedWith` array is empty or absent → return early
  - Queries `users/{uid}/myShares` where `itemId == recordingId && itemType == "recording"`
  - For each share entry (batched 250/batch = 500 ops max):
    - Deletes `users/{recipientUid}/sharedWithMe/{shareId}`
    - Deletes the `myShares` entry itself
  - Also removes `sharedWith` field from linked `actionItems` via `FieldValue.delete()` (batched 500/batch)

### Verification

- [ ] Owner deletes a shared recording → recording disappears from all recipients' Shared Items in real time
- [ ] All `myShares` and `sharedWithMe` entries for that recording are deleted
- [ ] If recording was shared with multiple users, all are cleaned up
- [ ] Duplicated copies (from Phase 8) in other users' accounts are NOT affected

---

## Phase 8: Duplication Feature

**Goal:** Recipients can duplicate a shared recording into their own account, creating a fully independent, editable copy.

### Cloud Function

- [x] Create `duplicateSharedRecording` callable in `functions/src/sharing.ts`
  - Input: `{ ownerUid: string, recordingId: string, destinationFolderId: string }`
  - Verifies the recording is shared with the caller (checks `sharedWith` array)
  - Generates new recording ID: `rec-{timestamp}-{random}` (using `randomBytes`)
  - Reads the owner's recording document
  - Copies to `users/{callerUid}/recordings/{newId}` with:
    - All content fields (title, transcription, summary, durationSeconds)
    - `folderId` set to `destinationFolderId`
    - `audioPath` set to `users/{callerUid}/audio/{newId}.m4a`
    - `sharedWith` removed (recipient now owns it)
    - `createdAt` set to server timestamp
  - Copies audio file in Cloud Storage from owner's path to recipient's path
  - Queries owner's `actionItems` where `recordingId == originalRecordingId`
  - Copies each action item to `users/{callerUid}/actionItems` with new `recordingId`, `sharedWith`/`googleTaskId`/`calendarEventId` removed
  - Returns `{ success: true, newRecordingId: newId }`
  - Error handling: cleans up recording doc, audio file, and copied action items on any failure

### Android — Data Layer

- [x] Add `duplicateSharedRecording(ownerUid: String, recordingId: String, destinationFolderId: String)` to `SharingRepository`

### Android — UI

- [x] Add "Duplicate" action to `SharedRecordingDetailScreen` overflow menu (enabled, shows spinner when duplicating)
- [x] Reuse `MoveToFolderDialog` (with `title = "Duplicate to Folder"`) as folder picker — no new dialog created
- [x] On confirmation: calls `viewModel.duplicateToFolder(folderId)`, shows loading state in menu item
- [x] On success: shows Snackbar "Recording duplicated successfully" via `SnackbarHostState`
- [x] The original shared reference remains in Shared Items (user can dismiss separately)

### Verification

- [ ] Duplicated recording appears in the chosen folder with all content
- [ ] Audio file is independently copied (plays from recipient's storage path)
- [ ] Action items are independently copied
- [ ] Duplicated recording is fully editable (rename, delete, move, re-share)
- [ ] Original shared reference remains in Shared Items
- [ ] Changes to duplicated recording do NOT affect the owner's original
- [ ] Changes to the owner's original do NOT affect the duplicate

---

## Phase 9: Task Sharing

**Goal:** Users can share individual tasks (copy-based). Recipients can "Add to my checklist" individual tasks from shared recording detail views. The Tasks pill tab is populated in the Shared Items folder.

### Data Model Update

- [x] Update `ActionItem.kt`: add two nullable fields — `sharedFromUid` and `sharedFromName`

### Independent Task Sharing — Backend

- [x] Create `shareTask` callable in `functions/src/sharing.ts` — copy-based, writes directly to recipient's `actionItems` with `sharedFromUid`/`sharedFromName`; no `sharedWithMe`/`myShares` entries

### Independent Task Sharing — Android

- [x] Add overflow menu ("Share with User") to `TaskDetailScreen` via `extraActions` slot of `VoiceMindTopAppBar`
- [x] Reuse `ShareDialog` with `itemType = "task"` — hides "Shared with" section, routes to `shareTask` callable
- [x] `ShareDialog` parameter renamed from `recordingId` to `itemId`; callers in `RecordingsScreen` and `RecordingDetailScreen` updated
- [x] `TaskDetailScreen` shows "Shared by [name]" attribution when `item.sharedFromName != null`

### "Add to My Checklist" from Shared Recordings

- [x] Each task row in `SharedRecordingDetailScreen` has an `AddTask` icon button
- [x] Tapping copies the task to `users/{myUid}/actionItems` via `ActionItemRepository.addSharedTask()` with deterministic doc ID `shared-{ownerUid}-{originalTaskId}`
- [x] Button shows `CheckCircle` and is disabled after task has been added (idempotent write)
- [x] `addedTaskIds` set checked on task list load via parallel `isSharedTaskAdded()` calls

### Tasks Subsection in Shared Items

- [x] `ActionItemRepository.observeSharedTasks()` queries `whereNotEqualTo("sharedFromUid", null)` ordered by `createdAt DESC`
- [x] `SharedItemsViewModel` injects `ActionItemRepository`, maps to `SharedTaskUiModel`, populates `tasks` in state
- [x] Tasks tab in `SharedItemsScreen` uses `SharedTaskItemRow` (no dismiss button), navigates to `TaskDetailScreen` via `onTaskClick`
- [x] `AppNavHost` passes `onTaskClick = { taskId -> navController.navigate(taskDetailRoute(taskId)) }` to `SharedItemsScreen`

### Verification

- [ ] Sharing a task creates an independent copy in recipient's checklist
- [ ] Shared task appears in recipient's normal TO-DO list and in Shared Items > Tasks tab
- [ ] Editing/completing the shared task only affects the recipient's copy
- [ ] "Add to my checklist" copies a single task from a shared recording's task list
- [ ] Cannot add the same task twice (button disabled after adding)

---

## Phase 10: Collective Summary Sharing

**Goal:** Users can share collective (multi-recording) summaries. Recipients see them in a Summaries pill tab in Shared Items. Deletion cascade is handled.

### Data Model Update

- [ ] Update `CollectiveSummary.kt`: add `sharedWith: List<String> = emptyList()` field (for security rule compatibility — field must exist for the cross-user read rule to work)

### Backend

- [ ] Verify `shareItem` in `functions/src/sharing.ts` already handles `itemType: "collectiveSummary"` (it was designed to in Phase 2 — confirm it works for this item type)
- [ ] Create `onCollectiveSummaryDeleted` Firestore trigger in `functions/src/sharing.ts`
  - Trigger path: `users/{uid}/collectiveSummaries/{summaryId}` — on delete
  - Same cleanup logic as `onRecordingDeleted`: reads `sharedWith`, deletes `myShares` and `sharedWithMe` entries

### Android — UI

- [ ] Add "Share" action to collective summary context menu in `SummariesScreen`
- [ ] Reuse `ShareDialog` from Phase 5
- [ ] Summaries pill tab in `SharedItemsScreen` now populates when shared summaries exist
- [ ] Create a read-only shared summary view (can be a simple screen or dialog showing summary text and the list of recording titles it was generated from)
- [ ] Add duplication support: "Duplicate" action copies the collective summary to `users/{myUid}/collectiveSummaries` as an independent document

### Verification

- [ ] Sharing a collective summary creates inbox/outbox entries
- [ ] Recipient sees summary in Shared Items > Summaries tab
- [ ] Summary is read-only for recipient
- [ ] Recipient does NOT automatically get access to underlying recordings
- [ ] Owner deleting summary removes it from all recipients
- [ ] Duplicated summary is fully independent

---

## Phase 11: Generate Tasks from Shared Recordings

**Goal:** Recipients can generate new tasks from a shared recording's transcription (deferred feature from Phase 1 core sharing).

### Cloud Function

- [ ] Create `generateTasksFromSharedRecording` callable in `functions/src/sharing.ts`
  - Input: `{ ownerUid: string, recordingId: string, timezone: string }`
  - Verifies the recording is shared with the caller (checks `sharedWith` array)
  - Reads transcription from `users/{ownerUid}/recordings/{recordingId}`
  - Runs the same `extractActionItems` pipeline already used by `processRecording`
  - Writes extracted tasks to the **recipient's** `users/{callerUid}/actionItems` collection
  - Each task gets `recordingId` set to a reference format: `shared:{ownerUid}:{recordingId}` (distinguishes from locally-generated tasks)
  - Each task gets `sharedFromUid` and `sharedFromName` set for attribution
  - Returns `{ success: true, count: number }`

### Android — Data Layer

- [ ] Add `generateTasksFromSharedRecording(ownerUid: String, recordingId: String, timezone: String)` to `SharingRepository`

### Android — UI

- [ ] In `SharedRecordingDetailScreen`, add a "Generate Tasks" button (visible only when the recording has a transcription and tasks haven't already been generated for this user)
- [ ] On tap: call the Cloud Function, show loading state, display count of generated tasks on completion
- [ ] After generation, the task list section updates to show the newly generated tasks (they are now in the recipient's own `actionItems`)

### Verification

- [ ] Tasks are generated from the shared recording's transcription and appear in recipient's checklist
- [ ] Generated tasks have proper attribution (`sharedFromUid`, `sharedFromName`)
- [ ] Tasks appear in both the normal checklist and the Shared Items > Tasks tab
- [ ] Cannot generate tasks twice for the same shared recording (guard against duplicates)

---

## Phase 12: Push Notifications

**Goal:** Recipients receive a push notification when an item is shared with them.

### Android — FCM Setup

- [ ] Add Firebase Cloud Messaging dependency to `build.gradle.kts`
- [ ] Create `VoiceMindMessagingService` extending `FirebaseMessagingService`
  - `onNewToken(token)`: writes token to `users/{uid}/deviceTokens/{tokenId}` in Firestore
  - `onMessageReceived(message)`: builds and shows notification, navigates to Shared Items on tap
- [ ] Register the service in `AndroidManifest.xml`
- [ ] On app start (after auth), register/refresh FCM token and write to Firestore
- [ ] Handle token refresh: update Firestore when token changes

### Android — Permissions

- [ ] For Android 13+ (API 33+): request `POST_NOTIFICATIONS` permission at appropriate time (e.g., after first sign-in or when first share is received)
- [ ] Handle permission denied gracefully (app still works, just no push notifications)
- [ ] Create notification channel: "Shared Items" with appropriate importance level

### Backend — Send Notification

- [ ] Update `shareItem` in `functions/src/sharing.ts`: after creating the share, query `users/{recipientUid}/deviceTokens` for all registered tokens
- [ ] Send FCM message to each token:
  - Title: "[ownerName] shared a recording with you" (or summary, or task)
  - Body: item title
  - Data payload: `{ type: "shared_item", shareId, itemType }`
- [ ] Handle invalid/expired tokens: remove from Firestore on `messaging/invalid-registration-token` or `messaging/registration-token-not-registered` errors

### Verification

- [ ] Push notification received when item is shared
- [ ] Tapping notification opens app and navigates to Shared Items
- [ ] Notification works on Android 13+ with permission granted
- [ ] App works normally when notification permission is denied
- [ ] Token refresh is handled correctly

---

## Phase 13: Account Deletion & Final Cleanup

**Goal:** When a user account is deleted, all sharing references are comprehensively cleaned up. No orphaned data remains.

### Cloud Function

- [ ] Create `onUserDeleted` Auth `onDelete` trigger in `functions/src/userProfile.ts`
  - **Outgoing shares cleanup** (items the deleted user shared with others):
    - Query `users/{deletedUid}/myShares`
    - For each entry: delete the corresponding `users/{recipientUid}/sharedWithMe/{shareId}` inbox entry
    - Remove `deletedUid` from `sharedWith` arrays on all shared recordings and summaries (not strictly necessary since the docs will be deleted, but prevents stale data if deletion is partial)
  - **Incoming shares cleanup** (items others shared with the deleted user):
    - Query `users/{deletedUid}/sharedWithMe`
    - For each entry: remove `deletedUid` from the owner's document's `sharedWith` array, delete the owner's `myShares/{shareId}` entry
  - **Document tree deletion**:
    - Delete all subcollections: `recordings`, `folders`, `actionItems`, `collectiveSummaries`, `sharedWithMe`, `myShares`, `ntsCounters`, `deviceTokens`
    - Delete the `users/{deletedUid}` document itself
    - Delete `tasksTokens/{deletedUid}` if it exists
  - **Storage cleanup**: delete all files under `users/{deletedUid}/` in Cloud Storage
  - Duplicated copies in other users' accounts are NOT affected (they are fully independent)

### Verification

- [ ] Deleting an account removes all data from Firestore and Storage
- [ ] All recipients' Shared Items are updated (shared items from deleted user disappear)
- [ ] All owners' share lists are updated (deleted user removed from recipient lists)
- [ ] Duplicated copies in other accounts remain intact
- [ ] No orphaned `sharedWithMe` or `myShares` entries remain

---

## Appendix: Phase Dependencies

```
Phase 1  ──→  Phase 2  ──→  Phase 3  ──→  Phase 4  ──→  Phase 5
                                              │              │
                                              ▼              ▼
                                          Phase 6  ──→  Phase 7
                                              │
                                              ▼
                                          Phase 8
                                              │
                                              ▼
                                          Phase 9
                                              │
                                              ▼
                                          Phase 10
                                              │
                                              ▼
                                          Phase 11

Phase 3  ──→  Phase 12  (can start after Phase 3, independent of 4-11)

Phase 2  ──→  Phase 13  (can be done any time after Phase 2)
```

- Phases 1–6 are strictly sequential (each depends on the previous)
- Phase 7 depends on Phase 6 (needs the detail screen to verify cascade behavior)
- Phases 8–11 are sequential (each extends the previous)
- Phase 12 (Push Notifications) can be done in parallel with Phases 4–11
- Phase 13 (Account Deletion) can be done any time after Phase 2

---

## Appendix: New Files Created Across All Phases

| Phase | File | Type |
|-------|------|------|
| 3 | `android/.../data/model/SharedItem.kt` | Data model |
| 3 | `android/.../data/model/MyShare.kt` | Data model |
| 3 | `android/.../data/repository/SharingRepository.kt` | Repository |
| 4 | `android/.../ui/sharing/SharedItemsScreen.kt` | Screen |
| 4 | `android/.../ui/sharing/SharedItemsViewModel.kt` | ViewModel |
| 5 | `android/.../ui/sharing/ShareDialog.kt` | Dialog |
| 5 | `android/.../ui/sharing/ShareViewModel.kt` | ViewModel |
| 6 | `android/.../ui/sharing/SharedRecordingDetailScreen.kt` | Screen |
| 6 | `android/.../ui/sharing/SharedRecordingDetailViewModel.kt` | ViewModel |
| 12 | `android/.../service/VoiceMindMessagingService.kt` | Service |

## Appendix: Modified Files Across All Phases

| Phase | File | Change Summary |
|-------|------|----------------|
| 1 | `functions/src/userProfile.ts` | Add `onUserCreated` trigger |
| 1 | `firestore.rules` | Add `tasksTokens` deny rule |
| 1 | `android/.../data/repository/AuthRepository.kt` | Write profile fields on sign-in |
| 1 | `android/.../data/repository/UserSettingsRepository.kt` | Add discoverable observe/set |
| 1 | `android/.../ui/settings/SettingsScreen.kt` | Add Privacy section |
| 2 | `functions/src/sharing.ts` | Add 5 sharing callables; fix `revokeShare` to validate recipientUid against stored value |
| 2 | `firestore.rules` | Add cross-user read rules for recordings, collectiveSummaries, and actionItems |
| 3 | `android/.../data/repository/RecordingRepository.kt` | Add cross-user observe |
| 3 | `android/.../data/repository/ActionItemRepository.kt` | Add cross-user task observe |
| 4 | `android/.../ui/folders/FoldersScreen.kt` | Add Shared Items row |
| 4 | `android/.../ui/folders/FoldersViewModel.kt` | Add unread count |
| 4 | `android/.../ui/navigation/Routes.kt` | Add shared_items route |
| 4 | `android/.../ui/navigation/AppNavHost.kt` | Add SharedItemsScreen composable |
| 5 | `android/.../ui/recording/RecordingsScreen.kt` | Add Share action |
| 6 | `android/.../ui/navigation/Routes.kt` | Add shared_recording route |
| 6 | `android/.../ui/navigation/AppNavHost.kt` | Add SharedRecordingDetailScreen composable |
| 7 | `functions/src/sharing.ts` | Add `onRecordingDeleted` trigger |
| 8 | `functions/src/sharing.ts` | Add `duplicateSharedRecording` callable |
| 9 | `android/.../data/model/ActionItem.kt` | Add sharedFrom fields |
| 9 | `functions/src/sharing.ts` | Extend shareItem for tasks |
| 10 | `android/.../data/model/CollectiveSummary.kt` | Add sharedWith field |
| 10 | `functions/src/sharing.ts` | Add `onCollectiveSummaryDeleted` trigger |
| 11 | `functions/src/sharing.ts` | Add `generateTasksFromSharedRecording` callable |
| 12 | `functions/src/sharing.ts` | Update `shareItem` to send FCM |
| 12 | `android/app/build.gradle.kts` | Add FCM dependency |
| 12 | `android/app/src/main/AndroidManifest.xml` | Register messaging service |
| 13 | `functions/src/userProfile.ts` | Add `onUserDeleted` trigger |
