---
name: Widget island redesign
overview: "Redesign the recording widget using the \"Voice Island\" design language: separate tonal islands for status, timer, and controls with pill-shaped containers and distinct button styling, across all 6 supported sizes."
todos:
  - id: colors
    content: Add SurfaceContainerLowest + SurfaceContainerHigh to BrandColors; add IslandSurface, ButtonNeutral, ButtonDark to WidgetColors
    status: done
  - id: island-button
    content: Create IslandButton composable helper in RecordingWidget.kt using Box + cornerRadius + tint
    status: done
  - id: island-helpers
    content: Create StatusIsland and TimerIsland composable helpers
    status: done
  - id: active-3x2
    content: Rewrite Default3x2 active layout with 3-layer island stack (status pill, timer island, button row)
    status: done
  - id: active-2x2
    content: Rewrite Compact2x2 active layout with tighter island stack
    status: done
  - id: active-4x1
    content: Rewrite Row4x1 with 3 horizontal islands (status pill, timer pill, controls cluster)
    status: done
  - id: active-3x1
    content: Rewrite Row3x1 with 2 horizontal islands (merged status+timer, controls)
    status: done
  - id: active-2x1
    content: Rewrite Minimal2x1 with single controls island pill
    status: done
  - id: idle-update
    content: Update IdleContent across all sizes with island-styled mic button and title pill
    status: done
  - id: prompt-update
    content: Adapt PromptContent in SharedWidgetContent.kt to island aesthetic
    status: done
isProject: false
---

# Recording Widget Island Redesign

## Design Concept

The "Voice Island" design breaks the widget into **separate visual containers** (islands) with different tonal backgrounds, replacing the current flat layout. Key principles:

- **Tonal layering**: Widget background (gray) -> island surfaces (white/near-white) -> buttons (colored)
- **Pill shapes**: Status indicators use `cornerRadius(full)`, timer islands use `cornerRadius(12-16dp)`
- **3 distinct button styles**: Delete (gray circle), Pause/Play (primary circle, largest), Stop (dark circle)
- **No blur/glass**: Glance has no `backdrop-filter`; we achieve the tonal effect with solid `BrandColors` values

## Files to Change

- [RecordingWidget.kt](android/app/src/main/java/com/voicemind/widget/recording/RecordingWidget.kt) -- complete layout rewrite
- [WidgetColors.kt](android/app/src/main/java/com/voicemind/widget/common/WidgetColors.kt) -- add island/button surface colors
- [Color.kt](android/app/src/main/java/com/voicemind/ui/theme/Color.kt) -- add missing surface-container tones to BrandColors
- [SharedWidgetContent.kt](android/app/src/main/java/com/voicemind/widget/common/SharedWidgetContent.kt) -- adapt PromptContent to island style

## 1. New Colors

Add to `BrandColors` (M3 surface container hierarchy we're missing):

```kotlin
val SurfaceContainerLowest = Color(0xFFFFFFFF)  // white - island surface
val SurfaceContainerHigh   = Color(0xFFDDE3E8)  // light gray - delete button bg
```

Add to `WidgetColors`:

```kotlin
val IslandSurface  = ColorProvider(BrandColors.SurfaceContainerLowest)  // island bg
val ButtonNeutral  = ColorProvider(BrandColors.SurfaceContainerHigh)    // delete bg
val ButtonDark     = ColorProvider(BrandColors.OnSurface)               // stop bg
```

`WidgetColors.Accent` (Primary) is already available for the pause/play button bg. `WidgetColors.White` (OnPrimary) for icon tint on colored buttons. `WidgetColors.Label` (OnSurface) for icon tint on neutral buttons.

## 2. Button Construction Pattern

Replace the current self-contained `ic_*_widget_btn` layer-list drawables with Glance composables that wrap the **plain icon vectors** (`ic_delete_24`, `ic_pause_24`, etc.) in colored circles:

```kotlin
@Composable
private fun IslandButton(
    icon: Int,
    iconTint: ColorProvider,
    background: ColorProvider,
    size: Dp,
    iconSize: Dp,
    contentDescription: String?,
    action: Action,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(background)
            .cornerRadius(size / 2)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}
```

This uses `ColorFilter.tint()` to override the hardcoded fill colors in the vector drawables (e.g., `ic_delete_24` is red but we tint it dark for the gray circle).

Button specs per the design:

- **Delete**: `IslandButton(ic_delete_24, Label, ButtonNeutral, ...)`
- **Pause/Play**: `IslandButton(ic_pause_24/ic_play_24, White, Accent, ...)` -- largest button
- **Stop**: `IslandButton(ic_stop_24, White, ButtonDark, ...)`

## 3. Island Composable Helpers

Create reusable composable helpers for the status pill and timer island:

```kotlin
@Composable
private fun StatusIsland(isPaused: Boolean) // pill: dot + "RECORDING"/"PAUSED" text

@Composable
private fun TimerIsland(timerText: String, fontSize: TextUnit) // rounded rect: large timer
```

These wrap content in `Row`/`Column` with `background(IslandSurface).cornerRadius(...)`.

## 4. Active Content Layouts

### Default3x2 / 4x2 (tall sizes)

3-layer vertical island stack:

```
[  StatusIsland: dot + "RECORDING"   ] <- pill, cornerRadius(20dp)
                                         
[         TimerIsland: 04:12         ] <- rounded rect, cornerRadius(12dp)
[        "Current Session"           ]
                                         
[ (del)    (pause/play)     (stop)   ] <- 3 island buttons
```

- Status island: `fillMaxWidth`, pill, `IslandSurface` bg
- Timer island: `fillMaxWidth`, rounded rect, `IslandSurface` bg, 28sp (3x2) or 32sp (4x2) timer
- Controls: 3 `IslandButton`s. Pause/Play at 48dp, delete/stop at 40dp. Spacing 12-16dp.

### Compact2x2

Same 3-layer stack but tighter:

- Status island: smaller text (10sp), narrower padding
- Timer island: 22sp timer, no subtitle
- Controls: Pause/Play at 40dp, delete/stop at 34dp. Spacing 8dp.

### Row4x1

3 separate islands horizontally (matches the design's "label island + timer island + controls cluster"):

```
[ dot RECORDING ]  [ 12:45 ]  [ (del)(pause)(stop) ]
```

- Status island: pill, dot + label
- Timer island: pill, timer at 18sp
- Controls cluster: 3 buttons in a pill container
- All islands have `IslandSurface` bg and `cornerRadius(full)`

### Row3x1

2 islands (merged status+timer and controls):

```
[ dot PAUSED | 04:12 ]  [ (del)(play)(stop) ]
```

- Left island: pill, dot + label + divider + timer
- Right island: 3 smaller buttons in a pill
- Timer at 16sp

### Minimal2x1

Single controls island:

```
[ (del) (play) (stop) ]
```

- Pill container with `IslandSurface` bg wrapping 3 buttons (30dp each)

## 5. Idle Content Updates

Adapt to match the island aesthetic:

- 3x2/4x2: `WidgetTitle` in a status island pill + mic button as a large `IslandButton` with `Accent` bg
- 2x2: Same but tighter
- 4x1: Two islands horizontally -- title pill + mic button island
- 3x1: Same as 4x1 but merged
- 2x1: Just the mic `IslandButton`

## 6. PromptContent (SharedWidgetContent.kt)

Wrap the existing button in island styling -- give the "Sign In" / "Open App" button the `Accent` bg with `cornerRadius(full)` (already has this). Optionally wrap the title+subtitle in an `IslandSurface` container for the full effect. Keep backward compatibility for checklist widget (default params unchanged).

## 7. Widget Background

Keep `widget_background.xml` at `#F2F2F7` with `cornerRadius(16dp)`. The tonal contrast between this gray base and the white island surfaces creates the layering effect without needing blur.

## Glance-Specific Constraints

- `Box` is available in `androidx.glance.layout.Box` (Glance 1.1.x) -- use for centering icons in circular buttons
- `cornerRadius` works on RemoteViews level -- set to `size/2` for perfect circles
- No `backdrop-filter` / blur -- solid colors only
- `ColorFilter.tint()` overrides vector drawable fill colors -- no new icon drawables needed
- The existing plain icons (`ic_delete_24`, `ic_mic_24`, etc.) are reused; the `_btn` layer-list drawables become unused

## Unchanged Files

- `RecordingWidgetReceiver.kt` -- no changes
- `RecordingWidgetStateKeys.kt` -- no changes (same state, new visual)
- `WidgetStateManager.kt` -- no changes
- `recording_widget_info.xml` -- no changes (sizing stays the same)

