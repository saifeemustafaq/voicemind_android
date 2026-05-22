---
name: Phase 1 Execution
overview: "Execute Phase 1 from codereviewphases.md: three isolated cosmetic fixes with zero cross-file impact."
todos:
  - id: 1a
    content: RecordingWidget.kt -- Extract PromptContent composable from SignedOutContent and MicPermissionContent
    status: completed
  - id: 1b
    content: SharedSummaryDetailScreen.kt -- Replace !! with safe null handling
    status: completed
  - id: 1c
    content: SummariesScreen.kt -- Remove sparkle emoji from step text
    status: completed
  - id: verify
    content: Check lints and update phase doc checkboxes
    status: completed
isProject: false
---

# Phase 1: Safe Cosmetic Fixes

Three isolated edits, each in a single file with no downstream impact.

## 1a. RecordingWidget.kt -- Extract duplicated prompt composable

Extract a shared `PromptContent(subtitle, buttonLabel, intent)` from the near-identical `SignedOutContent()` and `MicPermissionContent()`. Both become thin wrappers.

**File:** [android/app/src/main/java/com/voicemind/widget/RecordingWidget.kt](android/app/src/main/java/com/voicemind/widget/RecordingWidget.kt)

- Add `private fun PromptContent(subtitle: String, buttonLabel: String, intent: Intent)` containing the shared title/subtitle/button layout (lines 91-131 pattern)
- `SignedOutContent()` becomes: build intent, call `PromptContent("Sign in to record", "Sign In", intent)`
- `MicPermissionContent()` becomes: build intent with `EXTRA_REQUEST_MIC_PERMISSION`, call `PromptContent("Microphone access needed", "Open App", intent)`

## 1b. SharedSummaryDetailScreen.kt -- Remove `!!`

**File:** [android/app/src/main/java/com/voicemind/ui/sharing/SharedSummaryDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedSummaryDetailScreen.kt)

- Line 168-169: Replace `else -> { val summary = state.summary!!` with `else -> state.summary?.let { summary ->` and close with `}` instead of `}`
- All inner content (the `Column` with `RichText`, Sources, error) stays identical

## 1c. SummariesScreen.kt -- Remove emoji

**File:** [android/app/src/main/java/com/voicemind/ui/summaries/SummariesScreen.kt](android/app/src/main/java/com/voicemind/ui/summaries/SummariesScreen.kt)

- Line 319: Change `"Tap the \u2728 Summarize button in the toolbar."` to `"Tap the Summarize button in the toolbar."`

## Verification

- Check lints on all three modified files
- Update phase doc checkboxes
