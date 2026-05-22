---
name: Recording widget resizing
overview: Make the recording widget resizable across 4 grid sizes (3x2, 3x1, 4x1, 2x2) using Glance's `SizeMode.Responsive` API, with tailored layouts for each size that preserve the design language and UX.
todos:
  - id: xml-config
    content: "Update recording_widget_info.xml: set resizeMode, minResize*, maxResize* attributes"
    status: pending
  - id: size-mode
    content: Add SizeMode.Responsive with 4 DpSize entries + WidgetSizeClass sealed interface to RecordingWidget.kt
    status: pending
  - id: idle-layouts
    content: Refactor IdleContent to handle all 4 size classes (vertical default/compact, horizontal row)
    status: pending
  - id: active-layouts
    content: Refactor ActiveContent to handle all 4 size classes (full vertical, compact vertical, single-row variants)
    status: pending
  - id: prompt-layouts
    content: Update PromptContent in SharedWidgetContent.kt with compact parameter for SignedOut/MicPermission states
    status: pending
  - id: initial-layout
    content: Simplify widget_initial.xml to work at smallest supported size
    status: pending
isProject: false
---

# Recording Widget Multi-Size Support

## Current State

The recording widget is fixed at 3x2 (`resizeMode="none"`) with a single vertical layout. It has 4 states: SignedOut, MicPermission, Idle, and Active (recording/paused). There is no usage of `SizeMode.Responsive` or `LocalSize` anywhere in the codebase.

**Files to change:**

- [RecordingWidget.kt](android/app/src/main/java/com/voicemind/widget/recording/RecordingWidget.kt) -- main layout logic
- [recording_widget_info.xml](android/app/src/main/res/xml/recording_widget_info.xml) -- sizing/resize config
- [SharedWidgetContent.kt](android/app/src/main/java/com/voicemind/widget/common/SharedWidgetContent.kt) -- make `PromptContent` size-adaptive
- [widget_initial.xml](android/app/src/main/res/layout/widget_initial.xml) -- placeholder for smallest size

---

## 1. XML Provider Config

Update `recording_widget_info.xml` to allow resizing:

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:initialLayout="@layout/widget_initial"
    android:minWidth="110dp"
    android:minHeight="40dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:maxResizeWidth="300dp"
    android:maxResizeHeight="180dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0" />
```

- `targetCellWidth/Height` stay at 3x2 (default placement size)
- `minResizeWidth/Height` set low enough for a 2x1 footprint (future-proof) but the declared responsive sizes drive the layout
- `resizeMode="horizontal|vertical"` unlocks drag-to-resize on both axes

---

## 2. Glance `SizeMode.Responsive`

Override `sizeMode` in `RecordingWidget` to declare the 4 supported size buckets. Glance will pre-render a RemoteViews for each and the launcher picks the best match at runtime.

```kotlin
class RecordingWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_2x2 = DpSize(130.dp, 110.dp)
        private val SIZE_3x1 = DpSize(200.dp, 56.dp)
        private val SIZE_3x2 = DpSize(200.dp, 110.dp)
        private val SIZE_4x1 = DpSize(270.dp, 56.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_2x2, SIZE_3x1, SIZE_3x2, SIZE_4x1)
    )
    // ...
}
```

Inside `provideContent`, read `LocalSize.current` to branch layout:

```kotlin
val size = LocalSize.current
val isCompactHeight = size.height < 80.dp
val isNarrowWidth = size.width < 180.dp
```

---

## 3. Layout Strategy Per Size

All 4 widget states (SignedOut, MicPermission, Idle, Active) need to adapt. The branching is based on two axes: **compact height** (1-row sizes) and **narrow width** (2x2).

### 3x2 (default, ~200x110dp) -- Keep Current Layout

No change. Vertical stack: Title, spacer, mic button (idle) or status + timer + controls row (active).

```
       VoiceMind
       [mic 56dp]

  dot Recording/Paused
      00:12:34
   [del] [pause] [stop]
```

### 2x2 (compact width, ~130x110dp)

Same vertical stack but tighter:

- Idle: `WidgetTitle` + smaller mic button (48dp) + reduced spacing
- Active: Merge status dot + timer onto one line (drop "Recording" text label). Controls row with smaller 36dp buttons, reduced spacing between them
- SignedOut/Mic: Shorter subtitle text, smaller button padding

```
     VoiceMind          |   dot 00:12:34
     [mic 48dp]         |   [del][pse][stp]
```

### 3x1 (wide + short, ~200x56dp)

Everything in a single horizontal row:

- Idle: `Row { WidgetTitle ... [mic 40dp] }`
- Active: `Row { dot + timer ... [del][pse][stp] }`
- SignedOut/Mic: `Row { title ... [Sign In button] }`

```
VoiceMind        [mic]  |  dot 00:12:34   [del][pse][stp]
```

### 4x1 (widest + short, ~270x56dp)

Same single-row approach as 3x1 but with room for the full status label:

- Active: `Row { dot + "Recording" + 00:12:34 ... [del][pse][stp] }`
- SignedOut/Mic: Full subtitle text fits

```
VoiceMind              [mic]  |  dot Recording 00:12:34   [del] [pse] [stp]
```

### Implementation approach in composables

Create a `WidgetSizeClass` sealed interface to avoid scattered conditionals:

```kotlin
private sealed interface WidgetSizeClass {
    data object Compact2x2 : WidgetSizeClass
    data object Row3x1 : WidgetSizeClass
    data object Row4x1 : WidgetSizeClass
    data object Default3x2 : WidgetSizeClass
}

private fun DpSize.toSizeClass(): WidgetSizeClass = when {
    height < 80.dp && width >= 250.dp -> WidgetSizeClass.Row4x1
    height < 80.dp -> WidgetSizeClass.Row3x1
    width < 180.dp -> WidgetSizeClass.Compact2x2
    else -> WidgetSizeClass.Default3x2
}
```

Then each state composable (`IdleContent`, `ActiveContent`, `SignedOutContent`, `MicPermissionContent`) takes a `WidgetSizeClass` parameter and uses `when` to pick its layout. This keeps each state's logic self-contained and avoids one giant branching tree.

---

## 4. Adapt SharedWidgetContent

`PromptContent` in [SharedWidgetContent.kt](android/app/src/main/java/com/voicemind/widget/common/SharedWidgetContent.kt) is currently vertical-only. Add a `compact: Boolean = false` parameter:

- `compact = false` (default): current vertical layout -- no change for checklist widget
- `compact = true`: horizontal `Row` layout -- title left, button right, subtitle hidden

This respects the DeveloperGuide rule "Parameterize, don't duplicate."

---

## 5. Update Initial Layout XML

The `widget_initial.xml` placeholder must work at the smallest supported size. Simplify to just the "VoiceMind" title centered, removing the mic `ImageView` (which won't fit in a 1-row widget). The Glance composable takes over immediately after first render anyway.

---

## 6. Button Sizing Constants

Define size-aware button dimensions as constants in `RecordingWidget.kt`:

- Default (3x2): mic 56dp, controls 44dp / stop 48dp, spacing 20dp
- Compact (2x2): mic 48dp, controls 36dp / stop 40dp, spacing 12dp
- Row (3x1, 4x1): mic 40dp, controls 36dp / stop 40dp, spacing 8-12dp

These stay above the 48dp min-touch-target because the clickable modifier's hit area can be larger than the visible icon via padding.

---

## Files Unchanged

- [RecordingWidgetReceiver.kt](android/app/src/main/java/com/voicemind/widget/recording/RecordingWidgetReceiver.kt) -- no changes needed
- [RecordingWidgetStateKeys.kt](android/app/src/main/java/com/voicemind/widget/recording/RecordingWidgetStateKeys.kt) -- no new state keys needed (same states, different layouts)
- [WidgetStateManager.kt](android/app/src/main/java/com/voicemind/widget/common/WidgetStateManager.kt) -- no changes needed (state push is size-agnostic)
- [WidgetColors.kt](android/app/src/main/java/com/voicemind/widget/common/WidgetColors.kt) -- no changes needed
- All drawable resources -- reused at different sizes via `GlanceModifier.size()`

