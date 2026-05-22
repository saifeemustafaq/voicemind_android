Good, the file on disk already uses the correct local `val error = state.error` pattern, so the build error should be resolved. Rebuild with `./gradlew compileDebugKotlin` to confirm it passes.

Here is a comprehensive manual testing plan for Phase 6 and Phase 7:

---

## Phase 6: Shared Recording Detail Screen & Audio Playback

### Prerequisites
You need two accounts (Account A = owner, Account B = recipient). Account A should have a recording already shared with Account B (from Phase 5).

### Test 1: Navigation from Shared Items
1. Log in as **Account B** (recipient)
2. Open **Shared Items** folder
3. Tap a shared recording item
4. **Verify**: The shared recording detail screen opens with the correct title in the top bar

### Test 2: Content Display
1. On the shared recording detail screen, verify:
   - Title matches the owner's recording title
   - "Shared by [Owner Name]" attribution is displayed below the title
   - Transcription section shows the correct text
   - Summary section shows the correct text
   - Tasks section shows the action items associated with that recording (if any)

### Test 3: Audio Playback
1. Tap the **play** button
2. **Verify**: Audio starts playing from the signed URL
3. **Verify**: The waveform visualization appears and the position indicator moves
4. **Verify**: Time display updates in real time (current position / total duration)
5. Tap **pause** -- verify playback pauses and the button changes back to play
6. Tap **play** again -- verify playback resumes from where it paused

### Test 4: Playback Controls
1. While playing, tap the **skip forward** (+5s) button -- verify the position jumps forward
2. Tap the **skip backward** (-5s) button -- verify the position jumps backward
3. Drag the **seek slider/waveform** -- verify playback jumps to the new position
4. Tap the **speed bubble** to cycle through speeds (1x, 1.25x, 1.5x, 2x, 0.5x, 0.75x) -- verify audio speed changes
5. Long-press and drag on the speed bubble -- verify the speed slider appears and adjusts continuously

### Test 5: Read-Only Enforcement
1. On the detail screen, verify there are **no** edit, delete, move, or re-share options
2. There should be no overflow menu with destructive actions
3. The transcription and summary text should not be editable
4. Tasks should be displayed read-only (no checkboxes, no edit actions)

### Test 6: Live Updates
1. While Account B has the shared recording detail screen open:
2. On Account A, edit the recording's title, transcription, or summary
3. **Verify**: The changes appear in real time on Account B's screen (via Firestore snapshot listener)

### Test 7: Back Navigation
1. Press the back button from the shared recording detail screen
2. **Verify**: You return to the Shared Items list

### Test 8: Copy to Clipboard
1. On the transcription or summary section, tap the copy icon
2. **Verify**: The content is copied and a toast/snackbar confirms it

---

## Phase 7: Recording Deletion Cascade

### Prerequisites
- Deploy the Cloud Functions first: `cd functions && npm run deploy` (or `firebase deploy --only functions`)
- Have Account A share a recording with Account B (and optionally Account C for multi-recipient testing)

### Test 1: Single Recipient Cleanup
1. Log in as **Account B** -- confirm the shared recording appears in Shared Items
2. Log in as **Account A** -- delete the shared recording
3. Switch to **Account B** -- refresh/reopen Shared Items
4. **Verify**: The shared recording is gone from Account B's Shared Items
5. **Verify** in Firebase Console:
   - `users/{accountA_uid}/myShares` -- no entry for this recording
   - `users/{accountB_uid}/sharedWithMe` -- no entry for this recording

### Test 2: Multiple Recipient Cleanup
1. Share a recording from Account A with **both** Account B and Account C
2. Verify both see it in their Shared Items
3. Delete the recording on Account A
4. **Verify**: The recording disappears from **both** Account B and Account C's Shared Items
5. Check Firebase Console to confirm all `myShares` and `sharedWithMe` entries are cleaned up

### Test 3: Action Items sharedWith Cleanup
1. Share a recording that has associated action items
2. In Firebase Console, confirm the action items have a `sharedWith` array containing Account B's UID
3. Delete the recording on Account A
4. **Verify** in Firebase Console: the `sharedWith` field is removed from the associated action items (or the action items themselves are deleted if your existing app logic deletes them)

### Test 4: Non-Shared Recording Deletion (No-Op)
1. Delete a recording on Account A that was **never** shared
2. **Verify**: No errors in Cloud Functions logs (check `firebase functions:log` or Firebase Console > Functions > Logs)
3. The function should exit early since `sharedWith` is empty/absent

### Test 5: Real-Time Update
1. Account B has Shared Items screen open
2. Account A deletes the shared recording
3. **Verify**: The recording disappears from Account B's list in real time (without manual refresh), thanks to the Firestore snapshot listener

---

## Deployment Checklist

Before testing, make sure:

1. **Build passes**: Run `./gradlew compileDebugKotlin` in the `android/` directory -- the smart cast error should be resolved
2. **Cloud Functions deployed**: Run `firebase deploy --only functions` from the project root to deploy the `onRecordingDeleted` trigger
3. **Firestore Security Rules deployed**: Ensure the cross-user read rules for recordings and action items are deployed (`firebase deploy --only firestore:rules`)
4. **Firestore indexes**: If any queries in Phase 7 require composite indexes, the Firebase Console will show an error link in the Cloud Functions logs -- click it to create the needed index

If any test fails, check the Cloud Functions logs (`firebase functions:log --only onRecordingDeleted`) and the Android Logcat (filter by `SharedRecordingDetailVM` or `Timber`) for detailed error information.