---
name: Checklist Widget UX Polish
overview: Improve the checklist widget's visual density, click reliability, and responsiveness by increasing font size, adding item spacing, and replacing the Glance `CheckBox` composable with a custom clickable row for more reliable single-tap toggling.
todos:
  - id: drawables
    content: Create ic_circle_outline.xml and ic_check_circle.xml vector drawables in res/drawable
    status: completed
  - id: task-row
    content: Replace CheckBox-based TaskRow with custom Row (icon + text) with full-row clickable modifier and larger font
    status: completed
  - id: spacing
    content: Increase vertical padding on each TaskRow to 8-10dp for spacing between items
    status: completed
  - id: build-test
    content: Clean build, install on device, and verify via logcat
    status: completed
isProject: false
---

# Checklist Widget UX Polish

## Problem

The checklist widget has four UX issues:

1. Text is too small to read comfortably on the home screen
2. Clicking the checkbox requires multiple taps to register a completion
3. Completed items don't disappear from the list promptly
4. Items are packed too tightly together

## Root Cause for Multi-Click Issue

The Glance `CheckBox` maps to a `CompoundButton` in RemoteViews, which maintains its own internal `checked` toggle state. When tapped, the CompoundButton toggles visually *and* fires the `PendingIntent`. If the widget hasn't re-rendered yet and the user taps again, the CompoundButton toggles back visually, creating confusion. Additionally, the touch target on the CheckBox itself is small, leading to missed taps.

## Changes

All changes are in a single file: `[ChecklistWidget.kt](android/app/src/main/java/com/voicemind/widget/checklist/ChecklistWidget.kt)`

### 1. Replace `CheckBox` with a custom clickable row (lines 220-233)

Replace the `TaskRow` composable's `CheckBox` with a `Row` containing:

- A circle icon (empty circle for unchecked, filled checkmark for checked) using `Image(provider = ImageProvider(R.drawable....))`
- A `Text` composable for the task title

Make the **entire row** clickable via `GlanceModifier.clickable(actionRunCallback<ToggleItemAction>(...))`. This dramatically increases the tap target and eliminates the CompoundButton state race.

We need two simple vector drawables:

- `ic_circle_outline` (unchecked state)
- `ic_check_circle` (checked state)

### 2. Increase font size

Set the task title `Text` to `fontSize = 15.sp` (up from the system default ~14sp used by CheckBox).

### 3. Add item spacing (lines 211-218)

In `TaskList`, add a `Spacer(modifier = GlanceModifier.height(4.dp))` between items by switching from the bulk `items()` call to individual `item()` calls within a loop, or more simply, increase the vertical padding on each `TaskRow` from `2.dp` to `8.dp`.

### 4. Visual polish on TaskRow

- Add a subtle background with rounded corners to each row for a card-like feel
- Use `padding(horizontal = 8.dp, vertical = 10.dp)` for comfortable tap targets
- Apply `textDecoration = TextDecoration.LineThrough` and dimmed color for completed items so they look distinct before disappearing on the next refresh

