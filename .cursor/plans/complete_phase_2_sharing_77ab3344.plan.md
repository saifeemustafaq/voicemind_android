---
name: Complete Phase 2 Sharing
overview: "Phase 2 (Sharing Cloud Functions and Security Rules) is already 95% implemented in code. All 5 callable functions and cross-user read rules exist. The remaining work is: adding one missing security rule for actionItems, deploying to Firebase, and performing verification testing."
todos:
  - id: add-actionitems-rule
    content: Add cross-user read security rule for actionItems (matching pattern used for recordings/collectiveSummaries)
    status: completed
  - id: deploy-rules
    content: Deploy Firestore security rules via `firebase deploy --only firestore:rules` (USER ACTION)
    status: in_progress
  - id: deploy-functions
    content: Deploy Cloud Functions via `firebase deploy --only functions` (USER ACTION)
    status: pending
  - id: verification-testing
    content: "Run verification tests: cross-user reads, owner access, rate limiting, duplicate share prevention (USER ACTION)"
    status: pending
isProject: false
---

# Complete Phase 2: Sharing Cloud Functions and Security Rules

## Current State Assessment

Phase 2 is **almost entirely complete** in code. Both modified files (`firestore.rules` and `functions/src/index.ts`) contain all the Phase 2 deliverables. Here is a breakdown:

### Already Implemented (Code Exists)

**Security Rules** in [firestore.rules](firestore.rules):

- Cross-user read rule for recordings (line 8-11): checks `request.auth.uid in resource.data.sharedWith`
- Cross-user read rule for collective summaries (line 13-16): same pattern
- Owner wildcard rule `users/{uid}/{document=**}` is unchanged (line 4-6)
- `tasksTokens` deny rule added (line 21-23) (Phase 1 item, done)

**Cloud Functions** in [functions/src/index.ts](functions/src/index.ts):

- `findUserByEmail` (callable) -- with rate limiting (10/min in-memory map), Admin SDK `getUserByEmail`, discoverable check
- `shareItem` (callable) -- ownership verification, duplicate check, batch write (sharedWith arrayUnion + inbox + outbox), actionItems sharedWith propagation for recordings
- `revokeShare` (callable) -- removes from sharedWith, deletes inbox/outbox entries, cleans up actionItems
- `dismissSharedItem` (callable) -- recipient-side removal, same cleanup pattern as revokeShare
- `getSharedAudioUrl` (callable) -- verifies sharedWith, generates 1-hour signed URL via Admin Storage SDK
- Helper functions: `checkRateLimit`, `getItemCollection`, `removeFromActionItemsSharedWith`

### Remaining Work

#### 1. Add Missing Security Rule for ActionItems (Code Change)

The `shareItem` function already propagates `sharedWith` to action items when sharing a recording (line ~1593-1603 of index.ts). However, there is **no corresponding security rule** that allows cross-user reads on action items. Without this, Phase 3/6 (where recipients view tasks on shared recordings) will fail at the Firestore rules layer.

Add to [firestore.rules](firestore.rules):

```
match /users/{uid}/actionItems/{itemId} {
  allow read: if request.auth != null
    && request.auth.uid in resource.data.sharedWith;
}
```

This follows the exact same pattern already used for recordings and collectiveSummaries.

#### 2. Deploy to Firebase (User Action Required)

Two deployments are needed:

- **Firestore Rules**: `firebase deploy --only firestore:rules`
- **Cloud Functions**: `firebase deploy --only functions`

These must be run from the project root with proper Firebase CLI authentication.

#### 3. Verification Testing (User Action Required)

Per the Phase 2 checklist, these tests should be performed after deployment:

- **Test each callable**: Use Firebase Console's "Functions" tab to test, or use `curl` with a Firebase Auth ID token
- **Cross-user read test**: Create a recording for User A, add User B's UID to `sharedWith` array manually in Firestore console, then verify User B can read it via the client (or a test query)
- **Cross-user read denial test**: Verify a user whose UID is NOT in `sharedWith` gets `permission-denied`
- **Owner access test**: Confirm the owner of a recording can still read/write/delete it normally (the wildcard rule still applies)
- **Rate limiting test**: Call `findUserByEmail` more than 10 times in 1 minute and verify it returns `resource-exhausted`
- **Duplicate share test**: Call `shareItem` twice for the same item+recipient and verify it returns `already-exists`
- **Self-share test**: Call `shareItem` with `recipientUid === callerUid` and verify it returns `invalid-argument`

## Action Items on Your End

1. **Firebase CLI Authentication**: Ensure you are authenticated with `firebase login` and the correct project is selected (`firebase use <project-id>`)
2. **Deploy Rules**: Run `firebase deploy --only firestore:rules` from the project root
3. **Deploy Functions**: Run `firebase deploy --only functions` from the project root
4. **Manual Testing**: Run through the verification checklist above using two separate test accounts
5. **Check Cloud Function Logs**: After testing, review `firebase functions:log` to ensure no unexpected errors

## Risk / Edge Case Notes

- The in-memory rate limiter (`rateLimitMap`) resets whenever the Cloud Function cold-starts. This is acceptable for rate limiting email lookups but should not be relied upon for hard security boundaries. For a production-grade solution, a Firestore-based counter could be considered later.
- The `dismissSharedItem` function calls `batch.update(itemRef, ...)` to remove the caller from `sharedWith`. If the owner has already deleted the item, this update will fail. This is an edge case that Phase 7 (`onRecordingDeleted` trigger) will handle by proactively cleaning up all sharing references on deletion.

