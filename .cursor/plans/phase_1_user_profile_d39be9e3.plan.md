---
name: Phase 1 User Profile
overview: Complete Phase 1 (User Profile Infrastructure & Discoverability) by implementing the backend auth trigger, firestore rules update, Android data layer changes (AuthRepository profile sync, UserSettingsRepository discoverable methods), and wiring the existing Settings UI to real state.
todos:
  - id: cloud-function-trigger
    content: Add onUserCreated Auth trigger to functions/src/index.ts (v1 import, write profile doc with merge)
    status: completed
  - id: firestore-rules
    content: Add tasksTokens deny rule to firestore.rules
    status: completed
  - id: auth-repo-profile-sync
    content: "Update AuthRepository.kt: inject Firestore, add syncProfileToFirestore, call from all sign-in methods"
    status: completed
  - id: user-settings-discoverable
    content: "Update UserSettingsRepository.kt: add observeDiscoverable() and setDiscoverable() methods"
    status: completed
  - id: viewmodel-discoverable
    content: "Update SettingsViewModel.kt: expose discoverable StateFlow and setDiscoverable action"
    status: completed
  - id: settings-ui-wire
    content: Wire SettingsScreen.kt Privacy toggle to ViewModel state instead of local remember
    status: completed
isProject: false
---

# Phase 1: User Profile Infrastructure & Discoverability

## Current State

The frontend visual shells from the prior plan are all complete -- the Privacy section in Settings exists with a local-only toggle, routes and navigation are in place, and all sharing UI shells are created. **Phase 1 completion is about wiring the backend and data layer.**

## What Needs to Be Built

### 1. Cloud Function: `onUserCreated` Auth Trigger

**File:** [functions/src/index.ts](functions/src/index.ts)

**Critical:** firebase-functions v7 requires auth triggers to be imported from `firebase-functions/v1` (auth triggers are Gen 1 only, Gen 2 does not support them). The rest of the codebase uses v2 imports -- both coexist fine in the same file.

```typescript
import * as functionsV1 from "firebase-functions/v1";

export const onUserCreated = functionsV1.auth.user().onCreate(async (user) => {
  const { uid, displayName, email, photoURL } = user;
  await db.collection("users").doc(uid).set(
    {
      displayName: displayName || "",
      email: email || "",
      photoUrl: photoURL || "",
      discoverable: true,
    },
    { merge: true }
  );
});
```

- Uses `set` with `merge: true` so existing fields (`calendarConnected`, `tasksConnected`, NTS settings) are preserved
- Sets `discoverable: true` by default (as specified in PRD Section 28.1)
- Only fires for **new** accounts; existing users get backfilled via client-side sync (task 3 below)

### 2. Firestore Rules: Add `tasksTokens` Deny Rule

**File:** [firestore.rules](firestore.rules)

Add the `tasksTokens` deny rule alongside the existing `calendarTokens` rule:

```
match /tasksTokens/{uid} {
  allow read, write: if false;
}
```

The current rules already deny `calendarTokens` -- this makes `tasksTokens` explicit as well (even though undeclared paths default to deny in rules_version 2, being explicit is better for clarity and auditability).

### 3. Android: Update `AuthRepository.kt` to Write Profile on Sign-In

**File:** [android/app/src/main/java/com/voicemind/data/repository/AuthRepository.kt](android/app/src/main/java/com/voicemind/data/repository/AuthRepository.kt)

- Inject `FirebaseFirestore` alongside the existing `FirebaseAuth` (already provided by `AppModule`)
- Create a private helper `syncProfileToFirestore(user: FirebaseUser)` that writes `displayName` and `email` to `users/{uid}` with `merge: true`
- Call this helper after every successful sign-in in all three methods: `signInWithEmail`, `signUpWithEmail`, `signInWithGoogleCredential`
- Use `set(data, SetOptions.merge())` to avoid overwriting existing fields
- This is a fire-and-forget write (errors logged via Timber, not surfaced to user -- profile sync failure should not block sign-in)

```kotlin
private fun syncProfileToFirestore(user: FirebaseUser) {
    val data = mapOf(
        "displayName" to (user.displayName ?: ""),
        "email" to (user.email ?: ""),
    )
    firestore.collection("users").document(user.uid)
        .set(data, SetOptions.merge())
        .addOnFailureListener { Timber.e(it, "Failed to sync profile") }
}
```

### 4. Android: Update `UserSettingsRepository.kt` with Discoverable Methods

**File:** [android/app/src/main/java/com/voicemind/data/repository/UserSettingsRepository.kt](android/app/src/main/java/com/voicemind/data/repository/UserSettingsRepository.kt)

Add two methods following the existing `observeNtsSettings` / `setNtsEnabled` pattern:

- `observeDiscoverable(): Flow<Boolean>` -- reuses the existing `userDoc()` snapshot listener pattern from `observeNtsSettings()`, reads the `discoverable` field, defaults to `true` when absent
- `setDiscoverable(enabled: Boolean)` -- calls `userDoc().update("discoverable", enabled).await()`

The snapshot listener can either be a **separate** listener or piggyback on the existing one. A separate listener is cleaner since it keeps concerns decoupled and the Firestore SDK deduplicates snapshot listeners on the same document path.

### 5. Android: Update `SettingsViewModel.kt` to Expose Discoverable State

**File:** [android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt)

Add:

- `val discoverable: StateFlow<Boolean>` -- collected from `userSettingsRepository.observeDiscoverable()` via `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)` (same pattern as `ntsSettings`, `tasksConnected`)
- `fun setDiscoverable(enabled: Boolean)` -- launches `viewModelScope.launch(Dispatchers.IO) { userSettingsRepository.setDiscoverable(enabled) }` (same pattern as `setNtsEnabled`)

### 6. Android: Wire `SettingsScreen.kt` Privacy Toggle to ViewModel

**File:** [android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt)

Replace the local state:

```kotlin
// REMOVE:
var discoverable by remember { mutableStateOf(true) }

// REPLACE WITH:
val discoverable by settingsViewModel.discoverable.collectAsStateWithLifecycle()
```

Update the switch binding:

```kotlin
// CHANGE:
onCheckedChange = { discoverable = it }

// TO:
onCheckedChange = { settingsViewModel.setDiscoverable(it) }
```

This ensures the toggle persists to Firestore and reflects real-time state across app restarts and devices.

---

## Action Items for You (Manual Steps)

After the code changes are implemented, you need to:

1. **Deploy Cloud Functions:**

```bash
   cd functions && npm run build && cd .. && firebase deploy --only functions
   

```

   This deploys the new `onUserCreated` trigger.

1. **Deploy Firestore Rules:**

```bash
   firebase deploy --only firestore:rules
   

```

   This deploys the `tasksTokens` deny rule.

1. **Verification Testing:**
  - Create a new user account -- check Firebase Console > Firestore > `users/{uid}` for `displayName`, `email`, `photoUrl`, `discoverable: true`
  - Sign in with an existing user -- verify `displayName` and `email` fields appear on the `users/{uid}` doc (backfill)
  - Toggle the Privacy switch in Settings -- verify `discoverable` field updates in Firestore
  - Toggle it off, close app, reopen -- verify the switch reflects the saved `false` state
  - Verify existing user settings (`calendarConnected`, `tasksConnected`, NTS) are NOT overwritten (merge: true preservation)

---

## Files Changed Summary

- **[functions/src/index.ts](functions/src/index.ts)** -- Add v1 auth import + `onUserCreated` trigger (~15 lines)
- **[firestore.rules](firestore.rules)** -- Add `tasksTokens` deny rule (~3 lines)
- **[AuthRepository.kt](android/app/src/main/java/com/voicemind/data/repository/AuthRepository.kt)** -- Inject Firestore, add `syncProfileToFirestore` helper, call from sign-in methods (~15 lines)
- **[UserSettingsRepository.kt](android/app/src/main/java/com/voicemind/data/repository/UserSettingsRepository.kt)** -- Add `observeDiscoverable()` and `setDiscoverable()` (~20 lines)
- **[SettingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt)** -- Add `discoverable` StateFlow and `setDiscoverable` method (~10 lines)
- **[SettingsScreen.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsScreen.kt)** -- Replace local state with ViewModel binding (~3 lines changed)

