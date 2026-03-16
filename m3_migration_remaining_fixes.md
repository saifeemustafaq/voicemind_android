# M3 Migration — Remaining Fixes

Everything below describes work that is still needed to reach full M3 Expressive
compliance. Each section states **what is there now**, **what it should be**, and
which files are affected.

---

## Table of Contents

1. [Lucide → Material Symbols Icon Migration](#1-lucide--material-symbols-icon-migration)
2. [Hardcoded RoundedCornerShape](#2-hardcoded-roundedcornershape)
3. [Hardcoded Animation Specs (tween / spring)](#3-hardcoded-animation-specs-tween--spring)
4. [Custom Components Still Wrapping M3](#4-custom-components-still-wrapping-m3)
5. [Inline fontWeight / fontSize Overrides](#5-inline-fontweight--fontsize-overrides)
6. [Color.Transparent on TopAppBar Containers](#6-colortransparent-on-topappbar-containers)
7. [RecordingBottomSheet Custom Drag Handle](#7-recordingbottomsheet-custom-drag-handle)
8. [SidebarDrawer Hardcoded Width](#8-sidebardrawer-hardcoded-width)
9. [InlinePlayerControls Custom TransportButton](#9-inlineplayercontrols-custom-transportbutton)
10. [build.gradle.kts Cleanup](#10-buildgradlekts-cleanup)
11. [Guide Document Sync](#11-guide-document-sync)

---

## 1. Lucide → Material Symbols Icon Migration

**Priority: HIGH** — This is the single largest remaining task.

### What is there now

42 unique Lucide icons are imported from `com.composables.icons.lucide` across
15 files. No `androidx.compose.material.icons` imports exist anywhere in the
codebase, even though `material-icons-extended` is already in the Gradle
dependencies.

### Files affected and their Lucide imports

| File | Lucide icons |
|------|-------------|
| `ui/navigation/Routes.kt` | House, Mic, ListChecks, Sparkles, Folder, Settings |
| `ui/components/RecordFab.kt` | Mic |
| `ui/components/VoiceMindTopAppBar.kt` | ArrowLeft, Info, Menu, Settings |
| `ui/components/InlinePlayerControls.kt` | Pause, Play, RotateCcw, RotateCw |
| `ui/recording/RecordingBottomSheet.kt` | Pause, Play, Square, Trash2 |
| `ui/recording/RecordingsScreen.kt` | Check, Circle, CircleDot, CirclePause, CirclePlay, Copy, EllipsisVertical, FileText, Folder, FolderInput, Mic, Pencil, Pointer, Share, Sparkles, Trash2, X |
| `ui/recording/RecordingDialogs.kt` | Circle, CircleCheckBig, Clock, Flag, Sparkles |
| `ui/home/HomeScreen.kt` | ChevronDown, ChevronRight, ChevronUp, CirclePause, CirclePlay, FileText, Folder, FolderInput, House, Mic, Pencil, Share, Trash2 |
| `ui/checklist/ChecklistScreen.kt` | Circle, CircleCheckBig, Clock, Flag, ListChecks, Plus |
| `ui/checklist/TaskDetailScreen.kt` | Calendar, Circle, CircleCheckBig, Clock, Flag, ListChecks, StickyNote, X |
| `ui/folders/FoldersScreen.kt` | ChevronRight, Clock, EllipsisVertical, Folder, ListOrdered, Pencil, Plus, Trash2 |
| `ui/folders/FolderDetailScreen.kt` | Folder |
| `ui/summaries/SummariesScreen.kt` | Copy, Share, Sparkles, Trash2 |
| `ui/settings/SettingsScreen.kt` | Settings |
| `ui/auth/SignInScreen.kt` | Eye, EyeOff, Lock, Mail, Mic |

### What it should be — full mapping table

Every `Lucide.*` reference must be replaced with its `Icons.*` equivalent:

| Lucide | Material Symbol |
|--------|----------------|
| `Lucide.House` | `Icons.Default.Home` |
| `Lucide.Mic` | `Icons.Default.Mic` |
| `Lucide.ListChecks` | `Icons.Default.Checklist` |
| `Lucide.Sparkles` | `Icons.Default.AutoAwesome` |
| `Lucide.Folder` | `Icons.Default.Folder` |
| `Lucide.Settings` | `Icons.Default.Settings` |
| `Lucide.ArrowLeft` | `Icons.AutoMirrored.Default.ArrowBack` |
| `Lucide.Menu` | `Icons.Default.Menu` |
| `Lucide.Info` | `Icons.Default.Info` |
| `Lucide.Plus` | `Icons.Default.Add` |
| `Lucide.Trash2` | `Icons.Default.Delete` |
| `Lucide.Pencil` | `Icons.Default.Edit` |
| `Lucide.Share` | `Icons.Default.Share` |
| `Lucide.Copy` | `Icons.Default.ContentCopy` |
| `Lucide.FileText` | `Icons.Default.Description` |
| `Lucide.FolderInput` | `Icons.Default.DriveFileMove` |
| `Lucide.ChevronRight` | `Icons.Default.ChevronRight` |
| `Lucide.ChevronUp` | `Icons.Default.ExpandLess` |
| `Lucide.ChevronDown` | `Icons.Default.ExpandMore` |
| `Lucide.CirclePlay` | `Icons.Default.PlayCircle` |
| `Lucide.CirclePause` | `Icons.Default.PauseCircle` |
| `Lucide.Play` | `Icons.Default.PlayArrow` |
| `Lucide.Pause` | `Icons.Default.Pause` |
| `Lucide.Square` | `Icons.Default.Stop` |
| `Lucide.RotateCcw` | `Icons.Default.Replay` |
| `Lucide.RotateCw` | `Icons.Default.Forward` |
| `Lucide.X` | `Icons.Default.Close` |
| `Lucide.Check` | `Icons.Default.Check` |
| `Lucide.Circle` | `Icons.Default.RadioButtonUnchecked` |
| `Lucide.CircleCheckBig` | `Icons.Default.CheckCircle` |
| `Lucide.CircleDot` | `Icons.Default.RadioButtonChecked` |
| `Lucide.EllipsisVertical` | `Icons.Default.MoreVert` |
| `Lucide.Eye` | `Icons.Default.Visibility` |
| `Lucide.EyeOff` | `Icons.Default.VisibilityOff` |
| `Lucide.Mail` | `Icons.Default.Email` |
| `Lucide.Lock` | `Icons.Default.Lock` |
| `Lucide.Clock` | `Icons.Default.Schedule` |
| `Lucide.Flag` | `Icons.Default.Flag` |
| `Lucide.Calendar` | `Icons.Default.CalendarToday` |
| `Lucide.StickyNote` | `Icons.Default.Note` |
| `Lucide.Pointer` | `Icons.Default.TouchApp` |
| `Lucide.ListOrdered` | `Icons.Default.Sort` |

### How to do it

1. In each file, replace every `import com.composables.icons.lucide.*` block
   with the corresponding `import androidx.compose.material.icons.Icons` and
   specific icon imports (e.g. `import androidx.compose.material.icons.filled.Home`).
2. In `Routes.kt`, the `icon` and `outlinedIcon` fields both use the same
   Lucide icon today. Replace `icon` with `Icons.Filled.*` and `outlinedIcon`
   with `Icons.Outlined.*` so the navigation bar can show filled-when-selected,
   outlined-when-unselected.
3. After all replacements, verify no `com.composables.icons.lucide` imports
   remain via a global search.
4. Remove the Lucide dependency from `build.gradle.kts` (see Section 10).

---

## 2. Hardcoded RoundedCornerShape

**Priority: MEDIUM**

### What is there now

| File | Line(s) | Current Code |
|------|---------|-------------|
| `HomeScreen.kt` | 147 | `val cardRadius = 12.dp` |
| `HomeScreen.kt` | 152–155 | `RoundedCornerShape(cardRadius)`, per-corner variants, `RoundedCornerShape(0.dp)` |
| `HomeScreen.kt` | 205 | `RoundedCornerShape(bottomStart = cardRadius, bottomEnd = cardRadius)` |
| `HomeScreen.kt` | 270 | `RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)` |
| `RecordingsScreen.kt` | 560 | `RoundedCornerShape(28.dp)` — SummarizationPopup |
| `ChecklistScreen.kt` | 183 | `RoundedCornerShape(10.dp)` |

### What it should be

Replace hardcoded dp values with `MaterialTheme.shapes.*` tokens:

| Current | Replacement |
|---------|-------------|
| `12.dp` corners | `MaterialTheme.shapes.medium` (12dp) |
| `10.dp` corners | `MaterialTheme.shapes.medium` (12dp — nearest M3 token) |
| `28.dp` corners | `MaterialTheme.shapes.extraLarge` (28dp) |
| `0.dp` corners | `RectangleShape` |

For per-corner shapes (e.g. first/last items in a grouped list), extract the
corner size from the theme shape:

```kotlin
val mediumCorner = (MaterialTheme.shapes.medium as RoundedCornerShape)
    .topStart  // returns CornerSize

// Then use:
RoundedCornerShape(topStart = mediumCorner, topEnd = mediumCorner)
```

**Exception:** The `RoundedCornerShape(28.dp)` in `SummarizationPopup`
(`RecordingsScreen.kt:560`) is a decorative glass effect. It can stay as-is or
be replaced with `MaterialTheme.shapes.extraLarge` for consistency.

---

## 3. Hardcoded Animation Specs (tween / spring)

**Priority: MEDIUM**

### What is there now

| File | Line(s) | Current Code | Context |
|------|---------|-------------|---------|
| `FoldersScreen.kt` | 115–120 | `tween(300)`, `spring(dampingRatio = ..., stiffness = ...)` | List item `animateItem` |
| `RecordingsScreen.kt` | 403–404 | `tween(250)`, `tween(200)` | Scrim fade in/out |
| `RecordingsScreen.kt` | 419–428 | `spring(...)`, `tween(150)`, `tween(180)` | SummarizationPopup enter/exit |
| `SummariesScreen.kt` | 99–100 | `tween(200)` | List item `animateItem` |
| `RecordingsScreen.kt` | 521, 623–650 | `tween(1800)`, `tween(1400)` | **Infinite** shimmer animations |
| `RecordingBottomSheet.kt` | 101 | `tween(800)` | **Infinite** pulse animation |

### What it should be

State-transition animations should use `MaterialTheme.motionScheme.*`:

```kotlin
val motionScheme = MaterialTheme.motionScheme

// Scrim / fade transitions
fadeIn(animationSpec = motionScheme.fastEffectsSpec())
fadeOut(animationSpec = motionScheme.fastEffectsSpec())

// Position / scale transitions
scaleIn(animationSpec = motionScheme.defaultSpatialSpec())

// List item animations
animateItem(
    fadeInSpec = motionScheme.fastEffectsSpec(),
    placementSpec = motionScheme.defaultSpatialSpec(),
    fadeOutSpec = motionScheme.fastEffectsSpec(),
)
```

**Exception:** The infinite shimmer (`RecordingsScreen.kt` lines 521, 623–650)
and pulse (`RecordingBottomSheet.kt` line 101) animations are decorative loops
and are explicitly exempted by the design guide. They should keep their
custom `tween()` specs.

---

## 4. Custom Components Still Wrapping M3

**Priority: LOW** — These are all functionally correct and use M3 tokens now.
Deleting them is a code-cleanliness decision.

### 4.1 GlassCard.kt

**Currently:** `Surface` with `MaterialTheme.shapes.medium`,
`MaterialTheme.colorScheme.surfaceContainerLow`, `tonalElevation = 1.dp`,
and `combinedClickable`. Functionally an M3 tonal-elevated card.

**Option A (preferred by guide):** Delete `GlassCard.kt`, replace every
callsite with M3 `Card` / `OutlinedCard` / `ElevatedCard`:

```kotlin
// Callsite replacement:
Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
) { content() }
```

**Option B:** Keep `GlassCard.kt` as-is since it already delegates to M3
tokens. Rename to something without "Glass" (e.g. `VmCard`) so the name
doesn't imply non-M3 styling.

**Callsites:** `HomeScreen.kt`, `RecordingsScreen.kt`, `SettingsScreen.kt`,
`ChecklistScreen.kt`, `TaskDetailScreen.kt`, `SummariesScreen.kt`,
`SignInScreen.kt`, `EmptyStateCard.kt`

### 4.2 PrimaryButton.kt

**Currently:** Wraps M3 `Button` with `fillMaxWidth()` and
`VmDimens.ButtonHeight`. No custom colors.

**To remove:** Delete the file, inline at callsites:

```kotlin
Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(VmDimens.ButtonHeight),
) { Text(text, style = MaterialTheme.typography.labelLarge) }
```

**Callsites:** `SettingsScreen.kt`, `SignInScreen.kt`

### 4.3 VoiceMindTextFieldColors.kt

**Currently:** Returns `OutlinedTextFieldDefaults.colors()` with
`focusedBorderColor = colorScheme.primary`, `unfocusedBorderColor = colorScheme.outline`,
`containerColor = Color.Transparent`. This is nearly identical to M3 defaults
except for the transparent container.

**To remove:** Delete the file. At callsites, either drop the `colors`
parameter entirely (M3 defaults) or inline the transparent container if needed:

```kotlin
OutlinedTextField(
    // ...
    colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    ),
)
```

**Callsites:** `RecordingBottomSheet.kt` (line 140)

### 4.4 RecordFab.kt

**Currently:** Wraps M3 `FloatingActionButton` with permission-check logic.
No custom colors or sizing.

**Verdict:** The permission-check logic is legitimate business logic that
belongs in a wrapper. Keep this file. The only change needed is replacing the
Lucide icon (covered in Section 1).

---

## 5. Inline fontWeight / fontSize Overrides

**Priority: LOW**

### What is there now

| File | Line | Current |
|------|------|---------|
| `PermissionRationaleDialog.kt` | 44 | `fontWeight = FontWeight.SemiBold` |
| `PermissionRationaleDialog.kt` | 59 | `fontWeight = FontWeight.SemiBold` |
| `CalendarSyncPromptDialog.kt` | 35 | `fontWeight = FontWeight.SemiBold` |
| `CalendarSyncPromptDialog.kt` | 49 | `fontWeight = FontWeight.SemiBold` |
| `RecordingBottomSheet.kt` | 115 | `fontWeight = FontWeight.SemiBold` |
| `RecordingBottomSheet.kt` | 125 | `fontSize = 40.sp` |
| `SignInScreen.kt` | 104 | `fontWeight = FontWeight.Bold` |

### What it should be

Use the M3 type scale styles directly instead of overriding weight/size inline.
For example:

- **SemiBold emphasis** → Use `MaterialTheme.typography.titleSmall` (which is
  already `FontWeight.Medium`) or pick an emphasized variant.
- **Bold heading** → Use `MaterialTheme.typography.titleLarge` (which is already
  `FontWeight.Bold` in our `Type.kt`).
- **40.sp timer display** → This is a valid special case (monospace timer).
  Acceptable to keep as a `.copy(fontSize = 40.sp, fontFamily = Monospace)` on
  `headlineLarge`.

For the dialog SemiBold overrides, check whether the existing M3 type style
already provides the right visual weight. If so, drop the override.

---

## 6. Color.Transparent on TopAppBar Containers

**Priority: LOW**

### What is there now

| File | Line | Current |
|------|------|---------|
| `VoiceMindTopAppBar.kt` | 79 | `containerColor = Color.Transparent` |
| `RecordingsScreen.kt` | 760 | `containerColor = Color.Transparent` (MultiSelectTopBar) |

### What it should be

M3 `TopAppBar` uses `colorScheme.surface` by default, which integrates with the
scrolling behavior (the container color changes on scroll via
`scrolledContainerColor`). Using `Color.Transparent` disables this behavior.

**Option A:** Remove the `colors` parameter entirely to let M3 handle it.
**Option B:** Keep transparent if the design intentionally wants the content to
show through the app bar (e.g. over a scrollable list). Document the reason.

---

## 7. RecordingBottomSheet Custom Drag Handle

**Priority: LOW**

### What is there now

`RecordingBottomSheet.kt` line 67: `dragHandle = null`

Then lines 77–83 manually draw a drag handle:

```kotlin
Box(
    modifier = Modifier
        .width(36.dp)
        .height(5.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.outlineVariant)
)
```

### What it should be

Remove `dragHandle = null` to use M3's built-in drag handle, which is already
styled correctly, supports accessibility, and matches the M3 spec (32×4dp,
`onSurfaceVariant` at 0.4 alpha).

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    // dragHandle defaults to M3's built-in handle — no need to specify
) { ... }
```

Then remove the custom drag handle `Box` and the 12dp spacer above it.

---

## 8. SidebarDrawer Hardcoded Width

**Priority: LOW**

### What is there now

`SidebarDrawer.kt` line 23: `Modifier.width(360.dp)`

### What it should be

M3's `ModalDrawerSheet` has a default max width of 360dp on large screens and
adapts to smaller screens. Removing the explicit width lets M3 handle
responsiveness:

```kotlin
ModalDrawerSheet {
    // content — no explicit width
}
```

If 360dp is specifically desired on all screen sizes, keep it but add a comment
explaining the design intent.

---

## 9. InlinePlayerControls Custom TransportButton

**Priority: MEDIUM**

### What is there now

`InlinePlayerControls.kt` lines 157–185 define a custom `TransportButton`
composable that manually creates a circular clickable box with background color,
ripple indication, and `CompositionLocalProvider` for content color.

### What it should be

Replace with M3 `FilledTonalIconButton` (for secondary actions like skip) and
`FilledIconButton` (for the primary play/pause action):

```kotlin
// Skip backward (secondary action)
FilledTonalIconButton(
    onClick = onSkipBackward,
    modifier = Modifier.size(40.dp),
) {
    Icon(Icons.Default.Replay, contentDescription = "Skip back 15 seconds")
}

// Play / Pause (primary action)
FilledIconButton(
    onClick = onPlayPause,
    modifier = Modifier.size(48.dp),
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
) {
    Icon(
        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
    )
}
```

This eliminates the manual `Box` + `clip` + `background` + `clickable` +
`CompositionLocalProvider` pattern and uses M3's built-in ripple, touch target,
and state layer handling.

Also replace the custom `Slider` colors (lines 127–131) with M3 defaults by
removing the `colors` parameter.

---

## 10. build.gradle.kts Cleanup

**Priority: HIGH** (do after icon migration)

### What is there now

`build.gradle.kts` lines 99–100:

```kotlin
// Lucide Icons (iOS-style outlined iconography)
implementation("com.composables:icons-lucide-android:1.1.0")
```

### What it should be

Delete these two lines entirely after completing the icon migration in
Section 1. The `material-icons-extended` dependency on line 96 already provides
all needed icons.

---

## 11. Guide Document Sync

**Priority: LOW** — housekeeping only.

The design guide (`uioverhaul v2 material.md`) Section 11 "Current
Implementation Audit" describes the **pre-migration** state of the codebase
(iOS tokens, old line counts, old patterns). Since the migration has been
partially completed, the audit section is now stale.

### What needs updating

1. **Line counts** — Many files changed size during migration. The guide lists
   old counts (e.g. "Theme.kt (76 lines)" → now 99, "BottomNavBar.kt (72
   lines)" → now 30).
2. **iOS token references** — The audit says files use `IosAccent`, `IosWhite`,
   etc. These no longer exist in the codebase.
3. **Color.kt description** — Says "37 standalone iOS color constants". Now
   contains M3 palette from Theme Builder seed `#0061A4`.
4. **BottomNavBar.kt** — Described as custom Row/Column. Now uses M3
   `NavigationBar`.
5. **VmSemanticColors** — Guide Section 3.3/3.4 specifies a `VmSemanticColors`
   object with `Success` and `Warning`. The implementation instead uses
   `colorScheme.tertiary` for success and `colorScheme.error` for destructive.
   Either approach works, but the guide should match whichever was chosen.
6. **Theme.kt** — Guide target uses `expressiveLightColorScheme()` as fallback.
   Implementation uses a manual `VmLightColorScheme` from Theme Builder. Guide
   should be updated to reflect this.

After finishing all the fixes in Sections 1–10, re-audit the codebase and
rewrite Section 11 to reflect the final state.

---

## Summary — Recommended Fix Order

| # | Task | Effort | Files |
|---|------|--------|-------|
| 1 | Lucide → Material Symbols | High | 15 files + `build.gradle.kts` |
| 2 | Hardcoded RoundedCornerShape | Low | 3 files |
| 3 | Hardcoded tween/spring → motionScheme | Medium | 4 files (non-infinite only) |
| 4 | InlinePlayerControls TransportButton → M3 | Medium | 1 file |
| 5 | Delete VoiceMindTextFieldColors.kt | Low | 1 file + 1 callsite |
| 6 | RecordingBottomSheet drag handle | Low | 1 file |
| 7 | Inline fontWeight/fontSize cleanup | Low | 4 files |
| 8 | TopAppBar Color.Transparent | Low | 2 files |
| 9 | SidebarDrawer width | Low | 1 file |
| 10 | build.gradle.kts Lucide removal | Low | 1 file |
| 11 | Guide document sync | Low | 1 file |

The icon migration (task 1) is the highest-effort and highest-impact item.
Everything else can be done incrementally.
