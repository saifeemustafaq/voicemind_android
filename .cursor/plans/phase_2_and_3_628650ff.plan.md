---
name: Phase 2 and 3
overview: Execute Phase 2 (ViewModel internal refactors) and Phase 3 (cross-file refactors) from codereviewphases.md. Four changes across 5 files total.
todos:
  - id: p2a
    content: SharedRecordingDetailViewModel.kt -- Extract buildAndPreparePlayer helper
    status: completed
  - id: p2b
    content: SettingsViewModel.kt -- Extract executeReauthAndDelete helper
    status: completed
  - id: p3a
    content: AppNavHost.kt -- Hoist shared-items LaunchedEffect above if/else branch
    status: completed
  - id: p3b-vm
    content: AuthViewModel.kt -- Add signInWithGoogleCredential(activity) method
    status: completed
  - id: p3b-screen
    content: SignInScreen.kt -- Replace inline lambda, clean up imports
    status: completed
  - id: verify
    content: Check lints on all 5 modified files + update phase doc
    status: completed
isProject: false
---

# Phase 2 + 3 Execution

## Phase 2a: SharedRecordingDetailViewModel.kt -- Extract MediaPlayer helper

Extract a `private fun buildAndPreparePlayer(url, onError)` from the duplicated `MediaPlayer().apply { ... }` blocks in `playOrResume()` (lines 178-206) and `retryWithFreshUrl()` (lines 341-359).

**File:** [android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailViewModel.kt)

The shared helper encapsulates: `setDataSource`, `setOnPreparedListener` (start + update state + poll), `setOnCompletionListener` (cancel poll + reset state), the caller-supplied `onError` lambda, and `prepareAsync()`. The two callers differ only in:

- `playOrResume` error handler: retry logic with `urlRetryCount`
- `retryWithFreshUrl` error handler: just set error state, no retry

## Phase 2b: SettingsViewModel.kt -- Extract reauth-then-delete helper

Extract `private fun executeReauthAndDelete(reauthBlock: suspend () -> Result<Unit>)` from the duplicated bodies of `reauthAndDeleteWithGoogle()` (lines 362-377) and `reauthAndDelete()` (lines 379-394).

**File:** [android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt](android/app/src/main/java/com/voicemind/ui/settings/SettingsViewModel.kt)

Both become one-liners calling `executeReauthAndDelete { authRepository.reauthenticateWith...(args) }`.

## Phase 3a: AppNavHost.kt -- Hoist shared-items LaunchedEffect

Hoist the identical `LaunchedEffect(openSharedItemsOnStart)` from both sidebar (lines 108-113) and bottom-bar (lines 181-186) branches to above the `if (useSidebar)` split. The recordings deep-link stays duplicated (sidebar uses `navigateTo`, bottom-bar uses `pagerState.scrollToPage`).

**File:** [android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt](android/app/src/main/java/com/voicemind/ui/navigation/AppNavHost.kt)

## Phase 3b: SignInScreen.kt + AuthViewModel.kt -- Move Google credential flow

Add `fun signInWithGoogleCredential(activity: Activity)` to [AuthViewModel.kt](android/app/src/main/java/com/voicemind/ui/auth/AuthViewModel.kt) wrapping the CredentialManager flow. Then replace the 25-line inline lambda in [SignInScreen.kt](android/app/src/main/java/com/voicemind/ui/auth/SignInScreen.kt) (lines 212-235) with `viewModel.signInWithGoogleCredential(context as Activity)`. Clean up unused imports and `rememberCoroutineScope()` from SignInScreen.