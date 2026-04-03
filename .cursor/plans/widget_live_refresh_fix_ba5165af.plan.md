---
name: Widget Live Refresh Fix
overview: Add the missing `ChecklistWidget().update()` call in ToggleItemAction so the widget re-renders immediately after toggling an item.
todos:
  - id: add-update-call
    content: Add ChecklistWidget().update(context, glanceId) at the end of ToggleItemAction.onAction()
    status: pending
  - id: build-verify
    content: Build, install, and verify items disappear immediately on tap
    status: pending
isProject: false
---

# Widget Live Refresh Fix

## Root Cause

`SwitchTabAction` (tab switching) works because it calls `ChecklistWidget().update(context, glanceId)` after writing to DataStore (line 26). This triggers a re-render.

`ToggleItemAction` updates both the database AND the DataStore with the new item state, but **never calls `ChecklistWidget().update()`**. Without that call, Glance has no signal to re-render the widget. The UI stays stale until the next external event (like switching tabs) triggers a re-render.

## Fix

One-line addition in `[ToggleItemAction.kt](android/app/src/main/java/com/voicemind/widget/checklist/ToggleItemAction.kt)`:

Add `ChecklistWidget().update(context, glanceId)` after the `updateAppWidgetState` block (after line 40). This mirrors exactly what `SwitchTabAction` does on its line 26.