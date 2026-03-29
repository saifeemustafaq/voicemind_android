# Shared Items Feature — Product Requirements Document (PRD)

## 1. Overview

The **Shared Items** feature enables VoiceMind users to share recordings, tasks, and summaries with other users. It introduces cross-user data access while preserving ownership boundaries and ensuring clarity between original and duplicated content.

This feature includes:

* A dedicated **Shared Items folder (UI layer)**
* A **sharing flow** using email lookup
* **Access control and revocation**
* Clear rules for **data mutability (read-only vs editable)**
* Defined behavior for **live updates vs duplication**
* Support for **multi-user sharing**
* **Notifications** and **privacy controls**

---

## 2. Core Principles

1. **Ownership is preserved** — Only the original owner can modify original content
2. **Shared content is read-only** (except tasks, which become independent copies)
3. **Live linkage exists until duplication**
4. **Duplication creates full independence**
5. **Sharing is explicit and revocable**

---

## 3. Shared Items Folder (UI Layer)

### 3.1 Placement

* A permanent folder named **"Shared Items"**
* Always pinned at the **top of the folders list**
* Appears above all system and user folders
* Cannot be:

  * Renamed
  * Deleted
  * Reordered

### 3.2 Empty State

### 3.2 Pill Tab Navigation

Three `FilterChip` pill tabs are **always visible** at the top of the screen:

* **Recordings** — recordings shared with the user
* **Tasks** — tasks shared with the user (Phase 9)
* **Summaries** — shared multi-recording summaries

Rules:

* Default selected tab is **Recordings**
* Tapping a pill shows only items of that type
* Each tab has its own per-tab empty state when no items of that type exist (e.g., "No shared recordings yet")
* All three tabs are always visible, even when no items are shared
* Items update dynamically as new items are shared or dismissed

---

## 4. Sharing Behavior

### 4.1 Supported Share Types

#### Recordings

Sharing a recording includes:

* Audio
* Transcription
* Generated tasks
* Summary

#### Tasks

* Individual tasks can be shared independently
* Upon sharing, tasks become **independent copies** in recipient's account

#### Summaries

* Only **manual or multi-recording summaries** are shareable
* Individual recording summaries are NOT directly shareable
* To share those, the entire recording must be shared

---

## 5. Data Behavior Model

### 5.1 Recordings & Summaries (Shared)

* **Read-only for recipient**

* Recipient CANNOT:

  * Edit title
  * Edit transcription
  * Modify summary
  * Delete original

* Recipient CAN:

  * View
  * Play audio
  * Generate tasks (if not already generated)

### 5.2 Tasks (Shared)

* Tasks become **fully independent copies**
* Behavior:

  * Editing status (complete/incomplete) affects only recipient
  * No sync with original owner

---

## 6. Live vs Snapshot Behavior

### 6.1 Shared State (Before Duplication)

* Shared recordings are **live references** to owner data
* If owner updates:

  * Title
  * Summary
  * Transcription

→ Changes are reflected in recipient's Shared Items view

### 6.2 Duplication Behavior

Recipient can **duplicate** a shared recording

When duplicated:

* A full copy is created under recipient’s account
* User selects destination folder
* The copy becomes:

  * Fully editable
  * Fully owned by recipient
  * Completely independent

Post-duplication:

* No sync with original
* Changes do not propagate in either direction

---

## 7. Sharing Flow (Sender Side)

### 7.1 Steps

1. User taps **"Share"** on an item
2. Enters recipient email
3. Taps **"Find"**

### 7.2 User Lookup

* System checks if email belongs to a VoiceMind user

#### If user is found AND discoverability is enabled:

* Show:

  * Name
  * Email
* User confirms sharing

#### If not found OR discoverability disabled:

* Show: "No user found"

### 7.3 Completion

* Item is added to recipient’s Shared Items folder

---

## 8. Access Management (Revocation)

* Owner can view list of users an item is shared with
* Owner can revoke access per user

Upon revocation:

* Item is removed from recipient’s Shared Items folder
* No effect on any duplicated copies

---

## 9. Multi-User Sharing

* A single item can be shared with **multiple users**
* A user can receive items from **multiple different users**

---

## 10. Re-sharing Rules

* Recipient CANNOT re-share original shared item
* Recipient CAN share only if:

  * They duplicated the item
  * The duplicate is now their owned content

---

## 11. Deletion Behavior

### 11.1 Recipient Deletes Shared Item

* Action = **"Remove from my Shared Items"**
* Does NOT affect owner
* Owner can re-share later

### 11.2 Owner Deletes Original

* Item disappears from all recipients
* Does NOT affect any duplicated copies

---

## 12. Audio File Access (Recommended Architecture)

### Decision: Use **secure, rule-based shared access (NOT file duplication)**

Requirements:

* Recipient must stream audio
* Maintain single source of truth

Implementation:

* Store audio in owner’s storage path
* Maintain a **sharedAccess list** (user IDs) on recording metadata
* Update Firebase Storage rules to allow read access if:

  * requester UID == owner UID OR
  * requester UID is in sharedAccess list

Benefits:

* No duplication cost
* Real-time consistency
* Secure and scalable

---

## 13. Notifications

### 13.1 Push Notification

* Triggered when item is shared
* Content example:

  > "John shared a recording with you"

### 13.2 In-App Notification (One-time Toast)

* Shown on next app open
* Clicking redirects to Shared Items folder

---

## 14. User Discoverability & Privacy

### 14.1 Setting

* Toggle: **"Allow others to find me by email"**

### 14.2 Behavior

| Discoverability | Result                             |
| --------------- | ---------------------------------- |
| Enabled         | User can be found via email lookup |
| Disabled        | System returns "No user found"     |

### 14.3 Safeguards

* Exact email match required
* No partial search
* Recommend backend rate limiting

---

## 15. Folder Behavior & Duplication

* Shared items **cannot be moved directly** to other folders

To move:

1. User taps **Duplicate**
2. Selects destination folder
3. Copy is created in that folder

Result:

* Fully independent item
* No linkage to original

---

## 16. Data Model Implications (High-Level)

Introduce:

* `sharedWith: [uid]` on shareable entities
* `ownerId` field
* Separate collections OR references for shared indexing (optional optimization)

Key requirement:

* Cross-user read access via controlled rules

---

## 17. Edge Cases

* Owner updates content → reflected unless duplicated
* Owner deletes content → removed for all recipients
* Recipient duplicates before deletion → copy persists
* Multiple shares of same item → no duplication, single reference

---

## 18. Summary

| Item Type | Editable | Live Updates | Can Duplicate        | Can Re-share           |
| --------- | -------- | ------------ | -------------------- | ---------------------- |
| Recording | No       | Yes          | Yes                  | Only after duplication |
| Summary   | No       | Yes          | Yes                  | Only after duplication |
| Task      | Yes      | No           | N/A (already copied) | Yes (as own)           |

---

## 19. Final Notes

This feature introduces a hybrid model:

* **Reference-based sharing (recordings/summaries)**
* **Copy-based sharing (tasks)**

The system must clearly distinguish between:

* Shared (read-only, linked)
* Owned (editable, independent)

UI must reinforce this distinction at all times.

---

# Part 2 — Architecture & Implementation Recommendations

The following sections resolve every open question from the PRD review and define the exact technical strategy for implementing Shared Items. These recommendations are designed to be secure, scalable, and aligned with the existing VoiceMind architecture.

---

## 20. User Profile Document

### 20.1 Problem

The current `users/{uid}` document only stores `calendarConnected`, `tasksConnected`, and NTS settings. There is no `displayName`, `email`, or `photoUrl` stored in Firestore. Firebase Auth stores this data but it is not queryable by other clients — only by the server-side Admin API.

Sharing requires user lookup by email and displaying the sharer's name in the recipient's UI.

### 20.2 Solution

Create a user profile on every sign-up via a Firebase Auth `onCreate` trigger (Cloud Function). On the Android side, also write `displayName` and `email` on every sign-in (in case the user updated their Google profile) using `merge: true` so existing fields are preserved.

### 20.3 Profile Fields

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `displayName` | `String` | `""` | From Firebase Auth `UserRecord` |
| `email` | `String` | `""` | From Firebase Auth `UserRecord` |
| `photoUrl` | `String` | `""` | From Firebase Auth (Google sign-in) |
| `discoverable` | `Boolean` | `true` | Controls email lookup visibility |

These fields coexist with existing fields (`calendarConnected`, `tasksConnected`, NTS settings) via `merge: true` writes.

### 20.4 Cloud Function: `onUserCreated`

* **Type:** Auth `onCreate` trigger
* **Logic:** Reads `displayName`, `email`, `photoURL` from the newly created `UserRecord`. Writes to `users/{uid}` with `merge: true`. Sets `discoverable: true`.

### 20.5 Client-Side Sync

On every successful sign-in (email/password or Google), the Android app writes `displayName` and `email` to `users/{uid}` with `merge: true`. This ensures profiles stay current if the user changes their Google display name.

### 20.6 Existing Users

Existing users who signed up before this feature will not have profile fields. The client-side sync on next sign-in will backfill them. The `onUserCreated` trigger only fires for new accounts.

---

## 21. Dual-Index Sharing Architecture

### 21.1 Problem

The current data model scopes everything under `users/{uid}/...` with strict per-user security rules. For sharing to work, two problems must be solved:

1. **Discovery:** The recipient's app needs to know what has been shared with them (they can't scan all users' collections)
2. **Access:** The recipient needs read access to the owner's documents

### 21.2 Solution: sharedWith Array + Per-User Inbox/Outbox

A two-part system:

**Part A — `sharedWith` array on owner documents:**

Add `sharedWith: List<String>` (list of recipient UIDs) to `Recording` and `CollectiveSummary` documents. This enables Firestore security rules to grant cross-user read access. This array is managed exclusively by Cloud Functions — clients never write to it directly.

**Part B — Recipient inbox (`sharedWithMe`) and owner outbox (`myShares`):**

Two new per-user subcollections, kept in sync by Cloud Functions.

### 21.3 Collection: `users/{recipientUid}/sharedWithMe/{shareId}`

One document per shared item. The recipient's app listens to this collection to populate the Shared Items folder.

| Field | Type | Notes |
|-------|------|-------|
| `ownerUid` | `String` | UID of the user who shared the item |
| `ownerName` | `String` | Denormalized for display (avoids extra reads) |
| `ownerEmail` | `String` | Denormalized for display |
| `itemType` | `String` | `"recording"` or `"collectiveSummary"` |
| `itemId` | `String` | Document ID in owner's collection |
| `sharedAt` | `Timestamp` | Server timestamp |
| `isRead` | `Boolean` | Default `false`. Set to `true` when recipient opens the item |

### 21.4 Collection: `users/{ownerUid}/myShares/{shareId}`

Mirrors the inbox. Lets the owner see who they've shared with and revoke access.

| Field | Type | Notes |
|-------|------|-------|
| `recipientUid` | `String` | UID of the recipient |
| `recipientName` | `String` | Denormalized for display |
| `recipientEmail` | `String` | Denormalized for display |
| `itemType` | `String` | `"recording"` or `"collectiveSummary"` |
| `itemId` | `String` | Document ID in owner's collection |
| `sharedAt` | `Timestamp` | Server timestamp |

### 21.5 How It Works at Runtime

1. **Recipient's app** listens to `users/{myUid}/sharedWithMe` with a snapshot listener — this populates the Shared Items folder
2. For each inbox entry, the app reads the actual recording/summary from `users/{ownerUid}/recordings/{itemId}` — permitted because the recipient's UID is in the document's `sharedWith` array (see updated security rules)
3. Live updates work automatically because the recipient has a Firestore snapshot listener on the owner's document
4. The `ownerName` field in the inbox entry is used for "Shared by [Name]" display without extra reads

---

## 22. Firestore Security Rules (Updated)

### 22.1 Problem

Current rules only allow `request.auth.uid == uid`. Sharing requires cross-user reads for specific documents.

### 22.2 Updated Rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // User's own data: full read/write
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }

    // Cross-user read for shared recordings
    match /users/{uid}/recordings/{recordingId} {
      allow read: if request.auth != null
        && request.auth.uid in resource.data.sharedWith;
    }

    // Cross-user read for shared collective summaries
    match /users/{uid}/collectiveSummaries/{summaryId} {
      allow read: if request.auth != null
        && request.auth.uid in resource.data.sharedWith;
    }

    // Server-only collections
    match /calendarTokens/{uid} {
      allow read, write: if false;
    }
    match /tasksTokens/{uid} {
      allow read, write: if false;
    }
  }
}
```

### 22.3 How It Works

* The first rule (`users/{uid}/{document=**}`) grants full access to the owner — unchanged
* The additional rules add **read-only** cross-user access for specific subcollections when the reader's UID is in the `sharedWith` array
* No cross-user write access is granted anywhere — all writes go through Cloud Functions
* The `sharedWith` array is managed exclusively by Cloud Functions, so users cannot grant themselves access
* Storage rules remain unchanged — audio access uses signed URLs (see Section 23)

---

## 23. Audio Access via Signed URLs

### 23.1 Problem

Section 12 of the original PRD proposed checking a `sharedAccess` list in Firebase Storage rules. This is technically impossible — Firebase Storage rules cannot query Firestore documents. Storage rules only have access to `request.auth`, the storage object's own metadata, and `request.resource`.

### 23.2 Solution: Cloud Function with Signed URLs

A Cloud Function verifies the caller has access to the shared recording, then generates a time-limited signed URL for the audio file.

### 23.3 Cloud Function: `getSharedAudioUrl`

* **Type:** Callable (`onCall`)
* **Input:** `{ ownerUid: string, recordingId: string }`
* **Logic:**
  1. Verify `request.auth` exists
  2. Read `users/{ownerUid}/recordings/{recordingId}`
  3. Verify caller's UID is in `sharedWith` array
  4. Generate a signed URL for the audio file (1-hour expiry)
  5. Return `{ url: string }`
* **Error cases:** Recording not found → `not-found`. Caller not in `sharedWith` → `permission-denied`.

### 23.4 Storage Rules

**No changes needed.** Storage rules remain owner-only. The signed URL bypasses storage rules entirely (it's generated server-side with admin credentials).

### 23.5 Client Behavior

The recipient's app calls `getSharedAudioUrl` when the user taps play on a shared recording. The returned URL is passed to `MediaPlayer`. URLs expire after 1 hour — if playback session is longer, the app requests a new URL.

---

## 24. Cloud Functions (Complete Specification)

All sharing operations must be Cloud Functions. The client never writes to another user's data.

### 24.1 Function Index

| Function | Type | Purpose |
|----------|------|---------|
| `onUserCreated` | Auth trigger | Seeds user profile document |
| `findUserByEmail` | Callable | Looks up a user by exact email match |
| `shareItem` | Callable | Shares a recording or summary with another user |
| `revokeShare` | Callable | Owner revokes access for a specific recipient |
| `dismissSharedItem` | Callable | Recipient removes item from their Shared Items |
| `getSharedAudioUrl` | Callable | Returns signed URL for shared audio playback |
| `duplicateSharedRecording` | Callable | Deep-copies a shared recording into recipient's account |
| `onRecordingDeleted` | Firestore trigger | Cleans up shares when owner deletes a recording |
| `onCollectiveSummaryDeleted` | Firestore trigger | Cleans up shares when owner deletes a summary |
| `onUserDeleted` | Auth trigger | Cleans up all shares when a user account is deleted |

### 24.2 `findUserByEmail`

* **Input:** `{ email: string }`
* **Logic:**
  1. Use Firebase Auth Admin API: `auth.getUserByEmail(email)`
  2. If user not found → return `{ found: false }`
  3. Read `users/{uid}` to check `discoverable` field
  4. If `discoverable == false` → return `{ found: false }`
  5. Return `{ found: true, uid, displayName, email }`
* **Rate limiting:** Enforce max 10 lookups per minute per caller (use a counter in Firestore or in-memory)

### 24.3 `shareItem`

* **Input:** `{ itemId: string, itemType: "recording" | "collectiveSummary", recipientUid: string }`
* **Logic:**
  1. Verify caller owns the item (`request.auth.uid` matches the document's path)
  2. Verify recipient exists (read `users/{recipientUid}`)
  3. Check if already shared (prevent duplicate shares)
  4. Add `recipientUid` to the document's `sharedWith` array (`arrayUnion`)
  5. Create inbox entry in `users/{recipientUid}/sharedWithMe/{shareId}`
  6. Create outbox entry in `users/{callerUid}/myShares/{shareId}`
  7. Use the same auto-generated `shareId` for both inbox and outbox entries (enables easy cross-reference)
  8. If `itemType == "recording"`: also add `recipientUid` to `sharedWith` on all `actionItems` where `recordingId == itemId` (enables cross-user read of tasks within shared recording detail view)
* **Return:** `{ success: true, shareId }`

### 24.4 `revokeShare`

* **Input:** `{ shareId: string, recipientUid: string }`
* **Logic:**
  1. Read `users/{callerUid}/myShares/{shareId}` to get `itemId` and `itemType`
  2. Remove `recipientUid` from the document's `sharedWith` array (`arrayRemove`)
  3. Delete `users/{recipientUid}/sharedWithMe/{shareId}`
  4. Delete `users/{callerUid}/myShares/{shareId}`
  5. If `itemType == "recording"`: also remove `recipientUid` from `sharedWith` on linked `actionItems`

### 24.5 `dismissSharedItem`

* **Input:** `{ shareId: string }`
* **Logic:**
  1. Read `users/{callerUid}/sharedWithMe/{shareId}` to get `ownerUid`, `itemId`, `itemType`
  2. Remove caller's UID from the document's `sharedWith` array (`arrayRemove`)
  3. Delete `users/{callerUid}/sharedWithMe/{shareId}`
  4. Delete `users/{ownerUid}/myShares/{shareId}`
  5. If recording: also remove from linked `actionItems` `sharedWith`
* **Note:** The owner can re-share later. This removes the link but does not block future shares.

### 24.6 `duplicateSharedRecording`

* **Input:** `{ ownerUid: string, recordingId: string, destinationFolderId: string }`
* **Logic:**
  1. Verify the recording is shared with the caller (check `sharedWith` array)
  2. Generate new recording ID: `rec-{timestamp}-{random}`
  3. Read the owner's recording document
  4. Copy to `users/{callerUid}/recordings/{newId}` with:
     * All fields from original (title, transcription, summary, durationSeconds)
     * `folderId` set to `destinationFolderId`
     * `audioPath` set to new path: `users/{callerUid}/audio/{newId}.m4a`
     * `sharedWith` removed (recipient now owns it)
     * `createdAt` set to server timestamp
  5. Copy audio file in Cloud Storage from owner's path to recipient's path
  6. Query owner's `actionItems` where `recordingId == original recordingId`
  7. Copy each action item to `users/{callerUid}/actionItems` with new `recordingId` pointing to the new copy, `sharedWith` removed
  8. Return `{ success: true, newRecordingId: newId }`
* **Error handling:** If copy fails mid-operation, clean up partial writes and return error

### 24.7 `onRecordingDeleted` (Firestore Trigger)

* **Trigger path:** `users/{uid}/recordings/{recordingId}` — on delete
* **Logic:**
  1. Read the deleted document's data (available in `event.data.before`)
  2. If `sharedWith` array is empty or absent → return (nothing to clean up)
  3. Query `users/{uid}/myShares` where `itemId == recordingId && itemType == "recording"`
  4. For each share entry: delete the corresponding `sharedWithMe` entry in the recipient's inbox
  5. Delete all matched `myShares` entries

### 24.8 `onUserDeleted` (Auth Trigger)

* **Logic:**
  1. Query `users/{deletedUid}/myShares` (everything the user shared with others)
  2. For each entry: delete the recipient's `sharedWithMe` inbox entry
  3. Delete entire `users/{deletedUid}` document tree (recordings, folders, actionItems, sharedWithMe, myShares, etc.)

---

## 25. Task Sharing Model (Clarified)

### 25.1 Problem

The original PRD says tasks become "fully independent copies" but also shows them in the Shared Items folder. This creates ambiguity about where shared tasks live and how they're distinguished from user-created tasks.

### 25.2 Solution: Two Distinct Behaviors

#### When a recording is shared:

* Tasks associated with that recording are **NOT automatically copied** to the recipient
* The recipient sees the recording's tasks as a **read-only list** inside the shared recording's detail view (alongside transcript and summary)
* The `sharedWith` array on the owner's `actionItems` documents enables this cross-user read
* If the recipient wants to act on a task, they tap **"Add to my checklist"** — this copies that specific task to their `actionItems` collection with origin metadata

#### When a task is shared independently:

* The task is **immediately copied** to `users/{recipientUid}/actionItems` with origin metadata
* The copy is fully independent — editing, completing, deleting only affects the recipient's copy
* The copy includes: `sharedFromUid`, `sharedFromName` fields for attribution

### 25.3 Origin Metadata Fields (on ActionItem)

| Field | Type | Notes |
|-------|------|-------|
| `sharedFromUid` | `String?` | `null` for self-created tasks. UID of the sharer for shared tasks |
| `sharedFromName` | `String?` | Display name of the sharer (denormalized) |

### 25.4 Where Shared Tasks Appear

* **Regular Checklist:** Yes — they are normal `actionItems` in the recipient's collection. They appear in TO-DO / DONE sections like any other task.
* **Shared Items folder, Tasks pill tab:** Yes — filtered view of `actionItems` where `sharedFromUid != null`. Provides a way to see all tasks that originated from sharing.
* Both views show the same underlying data. The Tasks tab in Shared Items is purely a filtered query, not a separate data store.

---

## 26. Summaries Clarification

### 26.1 What "Summaries" Means in the Sharing Context

* **Per-recording summary** = the `summary` field on a `Recording` document. This is NOT independently shareable. It travels with the recording when the recording is shared.
* **Collective summary** = a standalone document in `users/{uid}/collectiveSummaries`. This IS independently shareable.

### 26.2 Shared Items Folder — Summaries Subsection

The Summaries pill tab in the Shared Items folder shows **only independently shared Collective Summaries**.

### 26.3 Shared Collective Summary Behavior

When a collective summary is shared:

* The recipient sees the summary text and the list of recording titles it was generated from
* The recipient does **NOT** automatically get access to the underlying recordings
* If the owner wants to share those recordings too, they must share each one separately
* The recipient can duplicate the collective summary into their own `collectiveSummaries` collection

---

## 27. Attribution UI

### 27.1 Display Pattern

Every item in the Shared Items folder displays:

* **"Shared by [Name]"** — shown below the item title
* **Relative time** — e.g., "2 hours ago", "Yesterday", "Mar 15"

### 27.2 Data Source

The sharer's name comes from the denormalized `ownerName` field in the `sharedWithMe` inbox document. No extra Firestore reads needed.

### 27.3 Sorting

Items are sorted by `sharedAt` descending (most recently shared first) within each tab. No grouping by sharer.

### 27.4 Denormalization Trade-off

If the owner changes their display name after sharing, existing `ownerName` values in inbox entries will be stale. This is an acceptable trade-off — it can be batch-updated later if needed, and names rarely change.

---

## 28. User Discoverability & Privacy (Refined)

### 28.1 Default Value

**Enabled (`discoverable: true`) by default.**

Rationale:

* Exact email match is already required — no partial search, no browsing
* Rate limiting on `findUserByEmail` prevents enumeration attacks
* A sharing feature with opt-in discoverability would have very low adoption
* Users who want privacy can toggle it off

### 28.2 Settings Placement

The discoverability toggle appears in the Settings screen under a new **"Privacy"** section, placed between Account and Integrations.

* **Label:** "Allow others to find me by email"
* **Description text:** "When enabled, other VoiceMind users can find you by your email address to share recordings with you"

### 28.3 Existing Users

Existing users without a `discoverable` field are treated as discoverable (`true`). The `findUserByEmail` Cloud Function defaults to `true` when the field is absent.

---

## 29. Duplication via Cloud Function

### 29.1 Why Server-Side

The client cannot read or copy files from another user's Cloud Storage path. The duplication operation spans multiple collections (recording, audio file, action items) and must be performed atomically by a Cloud Function.

### 29.2 See Section 24.6

Full specification of the `duplicateSharedRecording` Cloud Function is in Section 24.6.

### 29.3 Post-Duplication Behavior

After duplication:

* The copied recording appears in the recipient's chosen folder
* The original shared reference remains in Shared Items (recipient can dismiss it separately if desired)
* The copy has no `sharedWith` array — it is fully owned by the recipient
* The copy can be edited, re-titled, moved between folders, deleted, and shared with others

---

## 30. Notifications Strategy (Phased)

### 30.1 Problem

The app currently has no push notification infrastructure — no FCM dependency, no device token registration, no token storage, no messaging Cloud Function.

### 30.2 Phase 1: In-App Badge

For the initial release, use an in-app badge indicator:

* The Shared Items folder shows a **badge count** of unread items (where `isRead == false` in `sharedWithMe`)
* When the user opens the Shared Items folder, all items are marked `isRead = true`
* No push notifications, no FCM dependency

### 30.3 Phase 3: Push Notifications

Deferred to a later phase. Requires:

* FCM dependency in `build.gradle.kts`
* `FirebaseMessagingService` implementation
* Device token registration in `users/{uid}/deviceTokens/{tokenId}`
* `shareItem` Cloud Function sends a push notification after creating the share
* Android 13+ `POST_NOTIFICATIONS` permission handling
* Notification channels configuration

---

## 31. Owner Deletion Cascade

### 31.1 Recording Deletion

When an owner deletes a recording, the `onRecordingDeleted` Firestore trigger (Section 24.7):

1. Reads the `sharedWith` array from the deleted document
2. Finds all corresponding `myShares` and `sharedWithMe` entries
3. Deletes them all

The recipient's snapshot listener on `sharedWithMe` automatically removes the item from their Shared Items folder UI.

### 31.2 Account Deletion

When a user account is deleted, the `onUserDeleted` Auth trigger (Section 24.8):

1. Cleans up all outgoing shares (removes inbox entries from all recipients)
2. Deletes the user's entire document tree

Duplicated copies in other users' accounts are NOT affected — they are fully independent.

---

## 32. Shared Items Is a Virtual Folder

### 32.1 Not a Firestore Document

The Shared Items entry is a **UI construct only**. There is no document in `users/{uid}/folders` for it.

### 32.2 Implementation

* Define a constant `SHARED_ITEMS_ID = "shared_items"` (similar to `UNFILED_ID`)
* In `FoldersScreen.kt`, hardcode a "Shared Items" row at the top of the `LazyColumn`, before the real folders list
* Tapping navigates to a new `SharedItemsScreen` (not `FolderDetailScreen`)
* `SharedItemsScreen` queries `users/{myUid}/sharedWithMe` and groups results by `itemType` to render pill tab content
* The badge count for unread items is derived from `sharedWithMe` documents where `isRead == false`
* `FoldersViewModel` exposes a `sharedItemsCount: StateFlow<Int>` for the badge

---

## 33. Recipient "Remove from Shared Items" Behavior

### 33.1 Mechanism

Recipient taps "Remove from my Shared Items" → app calls `dismissSharedItem` Cloud Function.

### 33.2 What Happens

1. Recipient's `sharedWithMe/{shareId}` entry is deleted
2. Recipient's UID is removed from the owner's document's `sharedWith` array
3. Owner's `myShares/{shareId}` entry is deleted

### 33.3 Re-sharing

After dismissal, the owner CAN share the item again. This creates new inbox/outbox entries with a new `shareId`. The recipient will see it as a fresh share.

---

## 34. "Generate Tasks" on Shared Recordings

### 34.1 Decision: Deferred to Phase 2

Generating tasks from a shared recording requires a new Cloud Function that reads from the owner's recording and writes to the recipient's `actionItems`. This adds complexity and is not essential for the initial sharing experience.

### 34.2 Phase 1 Behavior

The recipient can view existing tasks on a shared recording (read-only) but cannot trigger new task generation.

### 34.3 Phase 2: `generateTasksFromSharedRecording`

* **Input:** `{ ownerUid: string, recordingId: string, timezone: string }`
* **Logic:** Reads transcription from owner's recording. Runs the same extraction pipeline as `processRecording`. Writes extracted tasks to the **recipient's** `actionItems` collection with `recordingId` pointing to a reference format like `shared:{ownerUid}:{recordingId}`.

---

## 35. Android Layer Changes (Overview)

### 35.1 New Data Models

| Model | Location | Purpose |
|-------|----------|---------|
| `SharedItem` | `data/model/SharedItem.kt` | Maps `sharedWithMe` inbox documents |
| `MyShare` | `data/model/MyShare.kt` | Maps `myShares` outbox documents |

### 35.2 New/Updated Repositories

| Repository | Changes |
|------------|---------|
| `SharingRepository` (new) | `observeSharedWithMe()`, `observeMyShares(itemId)`, `shareItem()`, `revokeShare()`, `dismissSharedItem()`, `duplicateSharedRecording()`, `getSharedAudioUrl()`, `findUserByEmail()` |
| `AuthRepository` (updated) | Write profile fields on sign-in |
| `UserSettingsRepository` (updated) | `observeDiscoverable()`, `setDiscoverable(Boolean)` |
| `RecordingRepository` (updated) | `observeRecording(ownerUid, recordingId)` — cross-user read for shared recordings |

### 35.3 New Screens & ViewModels

| Screen | ViewModel | Purpose |
|--------|-----------|---------|
| `SharedItemsScreen` | `SharedItemsViewModel` | Displays inbox grouped by itemType, badge count |
| `ShareDialog` | `ShareViewModel` | Email lookup, share confirmation, manage existing shares |
| `SharedRecordingDetailScreen` | `SharedRecordingDetailViewModel` | Read-only recording view with audio playback via signed URL |

### 35.4 Updated Screens

| Screen | Changes |
|--------|---------|
| `FoldersScreen` | Add pinned Shared Items row at top with badge |
| `RecordingsScreen` | Add "Share" action to recording context menu |
| `SettingsScreen` | Add Privacy section with discoverability toggle |

### 35.5 Navigation

| Route | Screen | Parameters |
|-------|--------|------------|
| `shared_items` | SharedItemsScreen | — |
| `shared_recording/{ownerUid}/{recordingId}` | SharedRecordingDetailScreen | `ownerUid`, `recordingId` |

---

## 36. Implementation Phases

### Phase 1 — Core Sharing (Recordings)

| # | Task |
|---|------|
| 1 | User profile document (Cloud Function trigger + client-side sync) |
| 2 | `findUserByEmail` Cloud Function |
| 3 | `shareItem` and `revokeShare` Cloud Functions |
| 4 | `dismissSharedItem` Cloud Function |
| 5 | Updated Firestore security rules |
| 6 | `getSharedAudioUrl` Cloud Function |
| 7 | `SharingRepository` on Android |
| 8 | Shared Items folder UI (virtual folder, `SharedItemsScreen`) |
| 9 | Share dialog UI (email lookup, confirm, manage shares) |
| 10 | Shared recording detail screen (read-only, signed URL playback) |
| 11 | `onRecordingDeleted` cascade trigger |
| 12 | Discoverability toggle in Settings |
| 13 | In-app badge for unread shared items |

### Phase 2 — Extended Sharing

| # | Task |
|---|------|
| 1 | Independent task sharing (copy-based) |
| 2 | Collective summary sharing |
| 3 | `duplicateSharedRecording` Cloud Function |
| 4 | Duplication UI (destination folder picker) |
| 5 | "Add to my checklist" from shared recording tasks |
| 6 | `generateTasksFromSharedRecording` Cloud Function |
| 7 | `onCollectiveSummaryDeleted` cascade trigger |

### Phase 3 — Notifications & Polish

| # | Task |
|---|------|
| 1 | FCM dependency and `FirebaseMessagingService` setup |
| 2 | Device token registration and storage |
| 3 | Push notification on share event |
| 4 | Notification channel configuration |
| 5 | Android 13+ `POST_NOTIFICATIONS` permission handling |
| 6 | `onUserDeleted` cleanup trigger |

---

## 37. Updated Edge Cases

| Scenario | Behavior |
|----------|----------|
| Owner updates recording title/summary/transcription | Reflected for all recipients via live Firestore listener |
| Owner deletes recording | Removed from all recipients via `onRecordingDeleted` trigger |
| Recipient duplicates before owner deletes | Duplicate persists as independent copy |
| Multiple shares of same item to same user | `shareItem` checks for existing share and rejects duplicates |
| Owner shares recording, then edits `sharedWith` directly | Not possible — `sharedWith` is protected by security rules (no client write to other user's docs) and managed by Cloud Functions |
| Signed audio URL expires during playback | Client requests a new URL from `getSharedAudioUrl` |
| Recipient removes shared item, owner re-shares | New inbox/outbox entries created with fresh `shareId` |
| User with `discoverable: false` receives a share | Works — discoverability only affects `findUserByEmail` lookup, not direct sharing if the sender already knows the UID |
| Existing user signs in after profile feature ships | Client-side sync on sign-in backfills `displayName`, `email`, `discoverable` |

---

## 38. Updated Summary Table

| Item Type | Editable by Recipient | Live Updates | Can Duplicate | Can Re-share | Phase |
|-----------|----------------------|--------------|---------------|--------------|-------|
| Recording | No (read-only) | Yes | Yes (Phase 2) | Only after duplication | 1 |
| Collective Summary | No (read-only) | Yes | Yes (Phase 2) | Only after duplication | 2 |
| Task (via recording) | No (read-only in detail view) | Yes | "Add to checklist" creates copy | Yes (as own) | 2 |
| Task (independent) | Yes (full copy) | No | N/A (already copied) | Yes (as own) | 2 |

---

**End of PRD**
