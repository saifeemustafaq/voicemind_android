---
name: Recent Recipients Quick Share
overview: Add a "Recent" section to the ShareDialog that shows up to 3 unique people the user has recently shared with, allowing one-tap selection to skip the email lookup step.
todos:
  - id: repo-method
    content: Add `getRecentRecipients()` one-shot query to SharingRepository.kt
    status: done
  - id: vm-state
    content: Add `recentRecipients` to ShareUiState and load/filter in ShareViewModel.setItem()
    status: done
  - id: vm-action
    content: Add `selectRecentRecipient(FoundUser)` action to ShareViewModel
    status: done
  - id: dialog-ui
    content: Add "Recent" section UI to ShareDialog between Find button and lookup result
    status: done
isProject: false
---

# Recent Recipients Quick Share

## Approach

Query the user's `myShares` collection (which already stores every outgoing share with `recipientUid`, `recipientName`, `recipientEmail`, `sharedAt`) to find the most recent unique recipients. Display them as tappable rows in the ShareDialog. Tapping a recent recipient sets the `lookupState` to `Found` with that person's info, skipping the email-type-and-find step entirely -- the user then just taps "Share".

No backend changes are needed. The data already exists in Firestore.

## Files to Change

### 1. [SharingRepository.kt](android/app/src/main/java/com/voicemind/data/repository/SharingRepository.kt) -- Add `getRecentRecipients()`

Add a new **one-shot** method:

```kotlin
suspend fun getRecentRecipients(excludeUids: Set<String> = emptySet()): List<FoundUser> {
    val snapshots = mySharesCollection()
        .whereEqualTo("isDeleted", false)
        .orderBy("sharedAt", Query.Direction.DESCENDING)
        .limit(30)
        .get().await()
    val shares = snapshots.toObjects(MyShare::class.java)
    return shares
        .distinctBy { it.recipientUid }
        .filter { it.recipientUid !in excludeUids }
        .take(3)
        .map { FoundUser(uid = it.recipientUid, displayName = it.recipientName, email = it.recipientEmail) }
}
```

- Fetches the 30 most recent shares, deduplicates by `recipientUid`, filters out any already shared with this item, returns up to 3.
- The `FoundUser` import comes from `com.voicemind.ui.sharing.FoundUser` (already defined in `ShareViewModel.kt`). Alternatively, we can move `FoundUser` to a shared location or create a `RecentRecipient` data class in the repository. Since the repository shouldn't depend on the UI layer, we should either: (a) define a simple `RecentRecipient` data class in the repository/model layer and map it to `FoundUser` in the ViewModel, or (b) just return `List<MyShare>` and let the ViewModel map. **Preferred: return `List<MyShare>` and map in ViewModel** to keep layer separation clean.

Revised signature:

```kotlin
suspend fun getRecentRecipients(): List<MyShare>
```

### 2. [ShareViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/ShareViewModel.kt) -- Load recent recipients, add selection action

- Add `recentRecipients: List<FoundUser>` to `ShareUiState` (default `emptyList()`)
- In `setItem()`, after setting up the `myShares` observer, launch a coroutine to load recent recipients via `sharingRepository.getRecentRecipients()`, map to `FoundUser`, and filter out recipients already in `myShares` for this item
- Add `selectRecentRecipient(user: FoundUser)` function: sets `lookupState = LookupState.Found(user)` -- this is the key shortcut that skips the email lookup entirely
- When `myShares` updates (new share added/revoked), re-filter `recentRecipients` to exclude anyone now in `myShares`

### 3. [ShareDialog.kt](android/app/src/main/java/com/voicemind/ui/sharing/ShareDialog.kt) -- Add recent recipients UI section

- Pass `onSelectRecent: (FoundUser) -> Unit` into `ShareDialogContent`
- Between the "Find" button and the lookup result `when` block, add a "Recent" section:
  - Only show when `state.recentRecipients` is not empty
  - Label: "Recent" in `labelMedium` style
  - Up to 3 compact rows, each showing recipient name (bodyMedium) and email (bodySmall) in a `Row` inside a tappable surface (e.g., `GlassCard` or outlined surface with `clickable`)
  - Tapping calls `onSelectRecent(recipient)`, which fills the email field and sets lookup state to Found
  - When a recipient is tapped, also update the `email` text field to show their email for visual feedback
- Recipients already shared with (present in `myShares`) should not appear (filtered in ViewModel)

### Layout sketch

```
"Share with User"

[Recipient email _______________]
[Find]

Recent
┌──────────────────────────────┐
│ John Doe                     │
│ john@example.com             │
├──────────────────────────────┤
│ Jane Smith                   │
│ jane@example.com             │
└──────────────────────────────┘

[Lookup result / Found card / etc.]

─────────────────────────────────
Shared with
  Alice (alice@...) [Revoke]
```

