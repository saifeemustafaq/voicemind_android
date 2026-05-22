---
name: Recording widget size fixes
overview: "Fix 6 issues observed during testing of the recording widget's multi-size support: missing stop button in row layouts, missing label in 2x2, broken 4x2 mapping, and cramped 2x1."
todos:
  - id: size-classes
    content: Add Minimal2x1 + SIZE_2x1/SIZE_4x2 to responsive set + fix toSizeClass()
    status: pending
  - id: fix-row-overflow
    content: "Fix 3x1/4x1 active layout: timer gets defaultWeight, smaller buttons, stop button fits"
    status: pending
  - id: fix-2x2-label
    content: Add 3-layer layout (label + timer + buttons) to Compact2x2 active state
    status: pending
  - id: add-minimal-2x1
    content: "Create Minimal2x1 layouts: buttons-only active, mic-only idle, button-only prompt"
    status: pending
  - id: prompt-minimal
    content: Add minimal param to PromptContent in SharedWidgetContent.kt
    status: pending
isProject: false
---

# Recording Widget Size Fixes

All changes are in [RecordingWidget.kt](android/app/src/main/java/com/voicemind/widget/recording/RecordingWidget.kt) and [SharedWidgetContent.kt](android/app/src/main/java/com/voicemind/widget/common/SharedWidgetContent.kt).

## Fix 1: Add missing sizes + new size class

- Add `Minimal2x1` to `WidgetSizeClass` sealed interface
- Add `SIZE_2x1 = DpSize(130.dp, 56.dp)` and `SIZE_4x2 = DpSize(270.dp, 110.dp)` to the responsive set
- Update `toSizeClass()`: check `height < 80 && width < 180` first to catch 2x1 as `Minimal2x1`

This fixes issue **4** (4x2 now has a matching declared size that maps to `Default3x2`) and sets up issue **6** (2x1 gets its own class).

## Fix 2: 3x1 / 4x1 -- stop button clipping (issues 2, 3)

Root cause: total fixed-width content exceeds row width; the stop button overflows off the right edge.

Fix: Give the timer `GlanceModifier.defaultWeight()` so it fills remaining space *after* buttons get their fixed sizes. Remove the explicit `Spacer(defaultWeight)` between timer and buttons. Reduce button sizes to 30dp/30dp/34dp (3x1) and 32dp/32dp/36dp (4x1) with 6dp gaps.

For 4x1: show label ("Recording"/"Paused") between dot and timer.
For 3x1: label omitted to save width; only dot + timer + 3 buttons.

## Fix 3: 2x2 -- add status label (issue 5)

Change `Compact2x2` active layout from 2-layer (dot+timer / buttons) to 3-layer vertical matching `Default3x2`:

- Top: dot + "Recording"/"Paused" label (centered)
- Middle: timer (20sp)
- Bottom: 3 buttons (36dp/36dp/40dp)

## Fix 4: 2x1 -- minimal buttons-only layout (issue 6)

New `Minimal2x1` active layout: just the 3 control buttons centered in a row (32dp/32dp/36dp, 8dp gaps). No timer, no label, no dot.

- Idle: just the mic button centered
- Prompt (signed-out / mic): just the action button centered (add `minimal` param to `PromptContent`)

## Fix 5: SharedWidgetContent

Add `minimal: Boolean = false` param to `PromptContent`. When true, renders only the button (no title, no subtitle). Backward-compatible -- checklist widget unaffected.