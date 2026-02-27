# VoiceMind AI — Style Guide (Kotlin / Jetpack Compose)

This guide documents the VoiceMind AI design system for Android, following Apple's Human Interface Guidelines (HIG) to achieve a clean, minimalistic iOS-inspired look and feel using Jetpack Compose and Material 3.

---

## 1. Design philosophy

- **Clarity** -- Clean whitespace, content-first. UI elements are legible and purposeful.
- **Deference** -- The UI recedes; content takes center stage. Neutral backgrounds, no visual noise.
- **Depth** -- Subtle layering via white cards on a grouped gray background. No glassmorphism.
- **Restraint** -- Color is used sparingly and meaningfully. A single accent color (iOS blue) for interactive elements, red for destructive actions, green for success/completion.

---

## 2. Color palette

Defined in `Color.kt`. All colors mirror iOS system colors for a native feel.

### Backgrounds

| Name | Hex | Usage |
|------|-----|-------|
| **IosBackground** | `#F2F2F7` | App background (grouped table style) |
| **IosSecondaryBackground** | `#FFFFFF` | Card/surface white |
| **IosTertiaryBackground** | `#F2F2F7` | Secondary grouped background |

### Accent and Semantic

| Name | Hex | Usage |
|------|-----|-------|
| **IosAccent** | `#007AFF` | Primary actions, links, interactive elements |
| **IosDestructive** | `#FF3B30` | Delete, stop recording, destructive actions |
| **IosSuccess** | `#34C759` | Completion, toggle on state |
| **IosWarning** | `#FF9500` | Warning states |

### Text

| Name | Value | Usage |
|------|-------|-------|
| **IosLabel** | `#000000` | Primary text |
| **IosSecondaryLabel** | `#3C3C43` @ 60% | Secondary/metadata text |
| **IosTertiaryLabel** | `#3C3C43` @ 30% | Placeholder/disabled text |

### Separators and Fills

| Name | Value | Usage |
|------|-------|-------|
| **IosSeparator** | `#3C3C43` @ 12% | Thin dividers between items |
| **IosOpaqueSeparator** | `#C6C6C8` | Opaque borders (text fields, outlines) |
| **IosTertiaryFill** | `#787880` @ 12% | Subtle fills |

---

## 3. Typography

Uses the **Inter** font family (free, open-source alternative to Apple San Francisco). Font files are in `res/font/`.

Defined in `Type.kt` with `InterFontFamily`.

| Material Slot | iOS Equivalent | Size | Weight |
|---------------|----------------|------|--------|
| `headlineLarge` | Large Title | 34sp | Bold |
| `titleLarge` | Title 1 | 28sp | Bold |
| `titleMedium` | Title 2 | 22sp | SemiBold |
| `titleSmall` | Headline | 17sp | SemiBold |
| `bodyLarge` | Body | 17sp | Regular |
| `bodyMedium` | Callout | 16sp | Regular |
| `bodySmall` | Footnote | 13sp | Regular |
| `labelMedium` | Caption 1 | 12sp | Regular |
| `labelSmall` | Caption 2 | 11sp | Regular |

Text color is not baked into typography styles -- apply via the `color` parameter using `IosLabel`, `IosSecondaryLabel`, etc.

---

## 4. Shapes

Defined in `Theme.kt` via `IosShapes`:

| Shape | Radius | Usage |
|-------|--------|-------|
| `extraSmall` | 4dp | Small badges |
| `small` | 8dp | Buttons, chips |
| `medium` | 10dp | Cards, list containers |
| `large` | 12dp | Primary buttons, larger cards |
| `extraLarge` | 22dp | Bottom sheets, modals |

---

## 5. Components

### GlassCard (now iOS Card)

Opaque white surface with 10dp corner radius, no border, no shadow.

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth()) {
    // content
}
```

### PrimaryButton

iOS-accent blue, 12dp radius, 50dp height, white text.

```kotlin
PrimaryButton(text = "Sign In", onClick = { ... })
```

### GlassSurface (now iOS Surface)

Opaque white, 10dp corner radius, no decoration.

---

## 6. Spacing and layout

- **Grid:** 4dp base. Use 8, 16, 24, 32dp for padding and spacing.
- **Touch targets:** 48dp minimum for tappable controls.
- **Screen margins:** 16dp horizontal.
- **Section headers:** Use `bodySmall` in `IosSecondaryLabel`, uppercase, with 16dp start padding -- matches iOS grouped table section headers.

---

## 7. Navigation

### Bottom tab bar

- White background with 0.5dp top separator (`IosSeparator`)
- Selected icon/label: `IosAccent`
- Unselected: `IosSecondaryLabel`
- No indicator highlight (transparent)

### Top app bar

- Transparent background, no elevation
- Title: `titleSmall` weight

### Sidebar drawer

- White background
- Selected item: `IosAccent` text/icon with 8% accent background
- Unselected: `IosLabel` text, `IosSecondaryLabel` icon

---

## 8. Semantic color usage

| Context | Color |
|---------|-------|
| Interactive elements (buttons, links, play) | `IosAccent` (blue) |
| Stop recording, delete, destructive | `IosDestructive` (red) |
| Completed/toggle on | `IosSuccess` (green) |
| Warning | `IosWarning` (orange) |
| Folder/item icons | `IosAccent` |
| Section headers, secondary text | `IosSecondaryLabel` |
| Dividers | `IosSeparator` |

---

## 9. Recording UI

- **FAB:** `IosAccent` blue when idle
- **Recording status label:** `IosDestructive` red with pulse animation
- **Timer:** `IosLabel` black, monospace
- **Stop button:** `IosDestructive` red circle
- **Pause/Resume:** `IosAccent` blue circle
- **Discard:** `IosDestructive` red icon on light red background

---

## 10. Icons

Use **Material Icons** (filled style). No emoji.

Key icons: `Mic` (record), `PlayCircle`/`PauseCircle` (playback), `Stop` (stop), `Delete` (trash), `Folder` (folder), `CheckCircle`/`RadioButtonUnchecked` (checklist), `MoreVert` (overflow), `Edit` (rename), `Share` (share), `ChevronRight` (disclosure).

Icon colors: `IosAccent` for interactive, `IosDestructive` for destructive, `IosSecondaryLabel` for passive/disclosure.

---

## 11. Quick reference

| Element | Treatment |
|---------|-----------|
| App background | `IosBackground` (#F2F2F7) |
| Cards | White, 10dp radius, no border/shadow |
| Section headers | Uppercase, `bodySmall`, `IosSecondaryLabel` |
| Primary buttons | `IosAccent` blue, 12dp radius, 50dp height |
| FAB | `IosAccent` blue circle |
| Destructive actions | `IosDestructive` red |
| Switch on state | `IosSuccess` green track, white thumb |
| Separators | `IosSeparator` @ 0.5dp |
| Text fields | 10dp radius, `IosAccent` focus border, `IosOpaqueSeparator` unfocused |
| Bottom sheets | White, 22dp top radius |

---

*This style guide keeps the Android app aligned with iOS Human Interface Guidelines: clean, minimalistic, content-first, with purposeful use of color.*
