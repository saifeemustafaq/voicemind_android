# VoiceMind AI — M3 Expressive Design System Guide

**Canonical reference for all current and future UI development.**

Every screen, component, color, typeface, shape, icon, spacing value, animation, and elevation
in VoiceMind — whether it exists today or is built tomorrow — MUST conform to Material 3
Expressive as documented at https://m3.material.io/.

---

## Table of Contents

1. [M3 Expressive Design Principles](#1-m3-expressive-design-principles)
2. [Theme Setup](#2-theme-setup)
3. [Color System](#3-color-system)
4. [Typography](#4-typography)
5. [Shape System](#5-shape-system)
6. [Elevation & Surface](#6-elevation--surface)
7. [Motion](#7-motion)
8. [Component Standards](#8-component-standards)
9. [Icon System](#9-icon-system)
10. [Spacing](#10-spacing)
11. [Current Implementation Audit](#11-current-implementation-audit)
12. [Gap Analysis](#12-gap-analysis)
13. [Action Plan](#13-action-plan-phased)

---

## 1. M3 Expressive Design Principles

Material 3 Expressive is Google's most rigorously researched design update, built from 46 global
studies with 18,000+ participants. Users identified key UI elements up to 4x faster in expressive
layouts and consistently rated them higher on playfulness, creativity, energy, and friendliness.

### Core tenets

1. **Color is personal.** Dynamic color adapts the entire UI to the user's wallpaper or a brand
   seed color. Every surface, icon, and text element derives its color from the M3 color scheme —
   never from standalone hex constants.
2. **Shape communicates purpose.** An 8-tier shape scale (ExtraSmall → ExtraExtraLarge) plus
   shape morphing gives each component a distinct identity. Shapes are always referenced through
   `MaterialTheme.shapes`, never hardcoded.
3. **Typography is expressive.** 30 type styles (15 baseline + 15 emphasized) provide nuanced
   hierarchy. Emphasized styles signal selection, importance, and action.
4. **Motion is springy.** The Expressive motion scheme uses spring physics for natural, bouncy
   animations. `MaterialTheme.motionScheme` provides all animation specs.
5. **Elevation is tonal.** Depth is communicated through surface tint color, not drop shadows.
   M3 components handle their own elevation — no manual `.shadow()` modifiers.
6. **Components are standard.** Use M3 components (`Card`, `Button`, `NavigationBar`,
   `FloatingActionButton`, `TopAppBar`, `ModalBottomSheet`, etc.) as-is. Do not create custom
   wrappers that duplicate what M3 already provides.

### What this means in practice

- **No iOS color tokens** (`IosAccent`, `IosBackground`, `IosLabel`, etc.). Use
  `MaterialTheme.colorScheme.primary`, `.surface`, `.onSurface`, etc.
- **No custom `GlassCard`**, `PrimaryButton`, or `RecordFab` wrappers. Use M3 `Card`, `Button`,
  `FloatingActionButton`.
- **No Lucide icons.** Use Material Symbols via `androidx.compose.material.icons`.
- **No `VmDimens.Radius*` tokens.** Use `MaterialTheme.shapes.*`.
- **No standalone animation specs.** Use `MaterialTheme.motionScheme.*`.

---

## 2. Theme Setup

### 2.1 Entry Point: `MaterialExpressiveTheme`

Replace `MaterialTheme` with `MaterialExpressiveTheme`. This is the M3 Expressive composable
that wires color, typography, shapes, and the expressive motion scheme together.

**Target state for `Theme.kt`:**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceMindAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = VmTypography,
        shapes = VmShapes,
        content = content,
    )
}
```

Key points:
- Dynamic color is the default on Android 12+ (API 31+). The UI automatically adapts to the
  user's wallpaper.
- On older devices, `expressiveLightColorScheme()` provides the M3 Expressive baseline light
  palette; `darkColorScheme()` provides the dark palette.
- `MaterialExpressiveTheme` automatically sets `MotionScheme.expressive()` — no need to pass it
  explicitly unless overriding.
- The `@ExperimentalMaterial3ExpressiveApi` opt-in is required until the API stabilizes.

### 2.2 Dependencies

Update `build.gradle.kts` to use the latest Material3 version that includes Expressive APIs:

```kotlin
// Compose BOM should be 2025.01.00 or later
implementation(platform("androidx.compose:compose-bom:2025.01.00"))
implementation("androidx.compose.material3:material3")
```

Remove the Lucide icons dependency:
```kotlin
// REMOVE: implementation("com.composables:icons-lucide-android:1.1.0")
```

---

## 3. Color System

### 3.1 M3 Color Roles

M3 defines 26+ color roles. Every UI element must reference a role, never a raw hex value.

| Role | Light Purpose | Dark Purpose |
|------|--------------|-------------|
| `primary` | Brand accent, key actions, links, active states | Same role, auto-toned |
| `onPrimary` | Content on primary surfaces | Auto |
| `primaryContainer` | Filled containers for primary actions | Auto |
| `onPrimaryContainer` | Content on primary containers | Auto |
| `secondary` | Secondary actions, less emphasis | Auto |
| `onSecondary` | Content on secondary surfaces | Auto |
| `secondaryContainer` | Chips, filter states, supporting containers | Auto |
| `onSecondaryContainer` | Content on secondary containers | Auto |
| `tertiary` | Tertiary accent, complementary highlights | Auto |
| `surface` | Cards, sheets, dialogs, nav drawers | Auto |
| `onSurface` | Primary text, icons on surfaces | Auto |
| `onSurfaceVariant` | Secondary text, metadata, placeholders | Auto |
| `surfaceContainerLowest` | Lowest-emphasis surface layer | Auto |
| `surfaceContainerLow` | Low-emphasis surface layer | Auto |
| `surfaceContainer` | Default surface container (e.g., nav bar) | Auto |
| `surfaceContainerHigh` | High-emphasis surface (e.g., search bar) | Auto |
| `surfaceContainerHighest` | Highest-emphasis surface | Auto |
| `background` | Screen canvas | Auto |
| `onBackground` | Text on background | Auto |
| `error` | Destructive actions, error states | Auto |
| `onError` | Content on error surfaces | Auto |
| `errorContainer` | Error container backgrounds | Auto |
| `onErrorContainer` | Content on error containers | Auto |
| `outline` | Borders, dividers | Auto |
| `outlineVariant` | Subtle borders, hairlines | Auto |
| `inverseSurface` | Snackbar backgrounds | Auto |
| `inverseOnSurface` | Content on inverse surface | Auto |
| `inversePrimary` | Primary on inverse surface | Auto |
| `surfaceTint` | Tint for tonal elevation | Auto |

### 3.2 Mapping Current iOS Tokens to M3 Roles

| iOS Token (to remove) | M3 Role (to use instead) |
|------------------------|--------------------------|
| `IosBackground` | `MaterialTheme.colorScheme.background` |
| `IosSecondaryBackground` / `IosWhite` | `MaterialTheme.colorScheme.surface` |
| `IosTertiaryBackground` | `MaterialTheme.colorScheme.surfaceContainerLow` |
| `IosAccent` | `MaterialTheme.colorScheme.primary` |
| `IosDestructive` | `MaterialTheme.colorScheme.error` |
| `IosSuccess` | Custom semantic token (see below) |
| `IosWarning` | Custom semantic token (see below) |
| `IosLabel` | `MaterialTheme.colorScheme.onSurface` |
| `IosSecondaryLabel` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| `IosTertiaryLabel` | `MaterialTheme.colorScheme.onSurfaceVariant` (lower alpha) |
| `IosSeparator` | `MaterialTheme.colorScheme.outlineVariant` |
| `IosOpaqueSeparator` | `MaterialTheme.colorScheme.outline` |
| `IosTertiaryFill` | `MaterialTheme.colorScheme.secondaryContainer` |
| `IosWhite` (on-primary) | `MaterialTheme.colorScheme.onPrimary` |
| `ShimmerBlue/Gold/Purple` | Keep as branded accent tokens in `Color.kt` |
| All `IosDark*` variants | Eliminated — M3 dark scheme handles this automatically |

### 3.3 Semantic Colors Not Covered by M3

M3 does not define `success` or `warning` roles. Define them as extension properties via
`CompositionLocal` or keep them as standalone constants in `Color.kt`, but access them through
a centralized object:

```kotlin
object VmSemanticColors {
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9500)
}
```

These are the ONLY non-M3 colors permitted in the codebase.

### 3.4 `Color.kt` Target State

```kotlin
package com.voicemind.ui.theme

import androidx.compose.ui.graphics.Color

// Branded shimmer colors (SummarizationPopup visual effect)
val ShimmerBlue = Color(0xFF5E9EFF)
val ShimmerGold = Color(0xFFFFD700)
val ShimmerPurple = Color(0xFFB47FFF)

// Semantic colors not in M3 color scheme
object VmSemanticColors {
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9500)
}
```

Everything else comes from `MaterialTheme.colorScheme.*`.

---

## 4. Typography

### 4.1 M3 Type Scale

M3 defines 15 baseline styles + 15 emphasized styles across 5 roles:

| Style | Default Size | Weight | Usage in VoiceMind |
|-------|-------------|--------|-------------------|
| `displayLarge` | 57sp | Regular 400 | Not used (reserved for tablets/large screens) |
| `displayMedium` | 45sp | Regular 400 | Not used |
| `displaySmall` | 36sp | Regular 400 | Not used |
| `headlineLarge` | 32sp | Regular 400 | Screen hero titles (SignIn "VoiceMind AI") |
| `headlineMedium` | 28sp | Regular 400 | Major section headers |
| `headlineSmall` | 24sp | Regular 400 | Task detail title |
| `titleLarge` | 22sp | Regular 400 | Sheet titles, drawer brand name |
| `titleMedium` | 16sp | Medium 500 | Dialog titles, summary sheet header |
| `titleSmall` | 14sp | Medium 500 | Top app bar title, button text |
| `bodyLarge` | 16sp | Regular 400 | Primary list text, recording titles |
| `bodyMedium` | 14sp | Regular 400 | Descriptions, secondary content |
| `bodySmall` | 12sp | Regular 400 | Metadata, dates, section labels |
| `labelLarge` | 14sp | Medium 500 | Button labels, chip labels |
| `labelMedium` | 12sp | Medium 500 | Chip text, small interactive labels |
| `labelSmall` | 11sp | Medium 500 | Nav bar labels, captions, timestamps |

### 4.2 Brand Typeface

M3 supports two typeface slots:
- **Brand typeface** — for larger styles (Display, Headline). Expression-focused.
- **Plain typeface** — for smaller styles (Body, Label). Readability-focused.

Inter can continue as the app's typeface. Apply it to both brand and plain slots:

**Target `Type.kt`:**

```kotlin
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val VmTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
```

### 4.3 Rules

- Always reference styles through `MaterialTheme.typography.*`.
- Never hardcode `fontSize`, `fontWeight`, or `lineHeight` inline.
- Use the emphasized variant (applied via `fontWeight = FontWeight.Bold` or
  `FontWeight.SemiBold` on the same token) for selected states, primary CTAs, and headlines
  where extra visual weight is needed.
- No more than 4-5 distinct type styles visible on any single screen.

---

## 5. Shape System

### 5.1 M3 Shape Scale (8 tokens)

M3 Expressive defines 8 shape tokens via `ShapeDefaults`:

| Token | Default Radius | M3 Usage |
|-------|---------------|----------|
| `extraSmall` | 4dp | Text fields, menus, snackbars |
| `small` | 8dp | Chips, small components |
| `medium` | 12dp | Cards, small FABs |
| `large` | 16dp | FABs, extended FABs, nav drawers |
| `largeIncreased` | ~20dp | Expressive variant of large |
| `extraLarge` | 28dp | Large FABs, bottom sheets |
| `extraLargeIncreased` | ~32dp | Expressive variant of extra large |
| `extraExtraLarge` | ~36dp | Full pill shapes, maximum roundedness |

Additionally, `CircleShape` (50%) is used for circular components (FABs, avatars, checkboxes).

### 5.2 `Shapes` Configuration

**Target `Theme.kt` shapes:**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val VmShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(36.dp),
)
```

### 5.3 Component → Shape Mapping

| Component | Shape Token |
|-----------|------------|
| `OutlinedTextField`, `TextField` | `extraSmall` (4dp) |
| `FilterChip`, `AssistChip` | `small` (8dp) |
| `Card`, `OutlinedCard`, `ElevatedCard` | `medium` (12dp) |
| `FloatingActionButton`, `ExtendedFAB` | `large` (16dp) |
| `LargeFloatingActionButton` | `extraLarge` (28dp) |
| `ModalBottomSheet` | `extraLarge` (28dp top corners) |
| `NavigationDrawer` | `large` (16dp) |
| `DropdownMenu` | `extraSmall` (4dp) |
| `Snackbar` | `extraSmall` (4dp) |
| `AlertDialog` | `extraLarge` (28dp) |
| `Button` | `CircleShape` (full) — M3 default |
| `IconButton` | `CircleShape` |

### 5.4 Rules

- Always use `MaterialTheme.shapes.*` when specifying shapes for custom composables.
- Never hardcode `RoundedCornerShape(Xdp)` inline.
- Remove `VmDimens.RadiusSmall`, `VmDimens.RadiusMedium`, `VmDimens.RadiusLarge` from
  `Dimens.kt`. These are replaced by the M3 shape scale.

---

## 6. Elevation & Surface

### 6.1 Tonal Elevation

M3 replaces drop shadows with **tonal elevation**: as a surface rises in z-order, its background
color gains a tint from `surfaceTint`, becoming subtly more colorful. This provides depth cues
without visual heaviness.

| Elevation Level | dp | Usage |
|----------------|-----|-------|
| Level 0 | 0dp | Flat surfaces, background |
| Level 1 | 1dp | Cards, switch tracks |
| Level 2 | 3dp | Elevated cards, FABs at rest |
| Level 3 | 6dp | FABs when pressed, snackbars |
| Level 4 | 8dp | Menus, navigation drawers |
| Level 5 | 12dp | Modal sheets, dialogs |

### 6.2 Rules

- Let M3 components manage their own elevation. Do not pass custom `shadowElevation` or
  `tonalElevation` unless there is a specific design reason.
- Do not add `.shadow()` modifiers to any component. If depth is needed, use an `ElevatedCard`
  or `ElevatedButton` which handles elevation correctly.
- Do not add hairline borders to cards to simulate depth — M3's tonal elevation does this.
- The only place `shadowElevation` is acceptable is on custom floating overlays (e.g., the
  SummarizationPopup) where M3 components are not available.

---

## 7. Motion

### 7.1 Expressive Motion Scheme

`MaterialExpressiveTheme` automatically applies `MotionScheme.expressive()`, which provides
springier, bouncier animations compared to the standard scheme.

Access motion specs through `MaterialTheme.motionScheme`:

| Spec | Purpose | Character |
|------|---------|-----------|
| `defaultSpatialSpec()` | Position/size changes | Medium spring |
| `fastSpatialSpec()` | Quick position changes | Fast spring |
| `slowSpatialSpec()` | Dramatic transitions | Slow spring |
| `defaultEffectsSpec()` | Opacity/color changes | Medium tween |
| `fastEffectsSpec()` | Quick fades | Fast tween |
| `slowEffectsSpec()` | Gradual fades | Slow tween |

### 7.2 Usage

```kotlin
val motionScheme = MaterialTheme.motionScheme

// For spatial animations (movement, size)
animateDpAsState(
    targetValue = if (expanded) 200.dp else 0.dp,
    animationSpec = motionScheme.defaultSpatialSpec(),
)

// For effects (opacity, color)
animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = motionScheme.fastEffectsSpec(),
)
```

### 7.3 Rules

- Use `MaterialTheme.motionScheme.*` for all animation specs.
- Do not hardcode `spring()`, `tween()`, or duration values inline — use the motion scheme.
- The exception is infinite looping animations (shimmer, pulse) which are decorative and not
  tied to state transitions.
- Shape morphing is built into M3 components like `ButtonGroup` and `LoadingIndicator` — use
  them where applicable.

---

## 8. Component Standards

Every custom component must be replaced with or refactored to use standard M3 components.

### 8.1 GlassCard → M3 Card

**Current:** Custom `GlassCard` composable with hardcoded border, zero elevation, `Surface`.

**Replace with:** M3 `Card`, `ElevatedCard`, or `OutlinedCard`.

```kotlin
// Instead of GlassCard
Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    ),
) {
    // content
}

// For cards that need a border (grouped list style)
OutlinedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
) {
    // content
}

// For cards that need emphasis
ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
) {
    // content
}
```

### 8.2 PrimaryButton → M3 Button

**Current:** Custom `PrimaryButton` with hardcoded `IosAccent`/`IosWhite` colors, custom height
and radius.

**Replace with:** M3 `Button` (default filled variant).

```kotlin
Button(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
) {
    Text(text)
}
```

M3 `Button` automatically uses:
- `primary` background, `onPrimary` text color
- Shape: full rounded (pill)
- Height: 40dp (M3 default) — if 48dp+ is needed, set `modifier.height(48.dp)`
- Disabled state colors handled automatically

For secondary actions: `FilledTonalButton`, `OutlinedButton`, `TextButton`, `ElevatedButton`.

### 8.3 RecordFab → M3 FloatingActionButton

**Current:** Custom `RecordFab` with hardcoded `IosAccent`/`IosWhite`, `CircleShape`, container
`Box`.

**Replace with:** M3 `FloatingActionButton` or `LargeFloatingActionButton`.

```kotlin
// Standard FAB
FloatingActionButton(
    onClick = onStartRecording,
    modifier = modifier,
) {
    Icon(Icons.Default.Mic, contentDescription = "Record")
}

// Large FAB (if bigger is needed)
LargeFloatingActionButton(
    onClick = onStartRecording,
    modifier = modifier,
) {
    Icon(Icons.Default.Mic, contentDescription = "Record", modifier = Modifier.size(36.dp))
}
```

M3 FAB automatically uses:
- `primaryContainer` background, `onPrimaryContainer` icon color
- `large` shape (16dp radius)
- Standard elevation (6dp default, 8dp pressed)
- No need for a container `Box` or explicit `size()` — M3 handles sizing.

### 8.4 BottomNavBar → M3 NavigationBar

**Current:** Custom `BottomNavBar` with manual `Row`, `Column`, `selectable`, hairline divider.

**Replace with:** M3 `NavigationBar` with `NavigationBarItem`.

```kotlin
NavigationBar {
    Routes.drawerItems.forEach { item ->
        NavigationBarItem(
            selected = currentRoute == item.route,
            onClick = { onNavigate(item) },
            icon = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                )
            },
            label = { Text(item.label) },
        )
    }
}
```

M3 `NavigationBar` automatically uses:
- `surfaceContainer` background
- Active indicator with `secondaryContainer` color
- Proper icon/label tinting for selected/unselected states
- Correct height (80dp) and touch targets

### 8.5 SidebarDrawer → M3 ModalNavigationDrawer

**Current:** Already uses `ModalDrawerSheet` and `NavigationDrawerItem`. Mostly M3-compliant.

**Fix:** Remove hardcoded `drawerContainerColor`, `width`, colors. Let M3 defaults work.

```kotlin
ModalDrawerSheet {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        "VoiceMind AI",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(modifier = Modifier.height(8.dp))
    Routes.drawerItems.forEach { item ->
        NavigationDrawerItem(
            icon = { Icon(item.icon, contentDescription = item.label) },
            label = { Text(item.label) },
            selected = currentRoute == item.route,
            onClick = { onNavigate(item) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}
```

### 8.6 VoiceMindTopAppBar → M3 TopAppBar

**Current:** Uses `TopAppBar` but with `Color.Transparent` container, Lucide icons, hardcoded
sizes.

**Fix:** Use M3 `TopAppBar` with default colors (or `CenterAlignedTopAppBar` for centered
titles). Replace Lucide icons with Material Symbols.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
TopAppBar(
    title = { Text(title) },
    navigationIcon = {
        when {
            onBack != null -> IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
            }
            onOpenDrawer != null -> IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }
    },
    actions = {
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    },
)
```

M3 `TopAppBar` automatically handles container color, text style, and icon tinting.

### 8.7 RecordingBottomSheet → M3 ModalBottomSheet

**Current:** Uses `ModalBottomSheet` but with custom drag handle, hardcoded colors
(`IosAccent`, `IosDestructive`, `IosWhite`, `IosOpaqueSeparator`), manual shape.

**Fix:** Use M3 `ModalBottomSheet` with default drag handle and M3 color roles.

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
) {
    // Content using MaterialTheme.colorScheme.* for all colors
}
```

M3 `ModalBottomSheet` automatically provides:
- Drag handle (built-in)
- `surfaceContainerLow` container color
- `extraLarge` shape (28dp top corners)
- Scrim behind the sheet

### 8.8 EmptyStateCard

**Current:** Wraps `GlassCard`.

**Replace:** Wrap M3 `Card` or `OutlinedCard` instead, following the same pattern as 8.1.

### 8.9 InlinePlayerControls

**Current:** Custom `TransportButton` with hardcoded dark colors (`#1C1C1E`, `#2C2C2E`,
`#D1D1D6`), Lucide icons.

**Fix:** Use M3 `FilledIconButton` or `FilledTonalIconButton` for transport buttons. Use
M3 `Slider` with default colors. Replace Lucide icons with Material Symbols.

```kotlin
FilledTonalIconButton(onClick = onPlayPause) {
    Icon(
        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
    )
}
```

### 8.10 VoiceMindTextFieldColors

**Current:** Custom `OutlinedTextFieldDefaults.colors()` with `IosAccent` and
`IosOpaqueSeparator`.

**Replace:** Use M3 `OutlinedTextField` with default colors. M3 handles focused/unfocused border
colors using `primary` and `outline` roles automatically.

```kotlin
OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    // No custom colors — M3 defaults are correct
)
```

### 8.11 Dialogs (AlertDialog)

**Current:** Already using M3 `AlertDialog`. Mostly compliant.

**Fix:** Ensure no hardcoded colors on dialog text, buttons, or containers.

### 8.12 DropdownMenu

**Current:** Uses `DropdownMenu` with custom `shape`, `containerColor`, `tonalElevation = 0`,
`shadowElevation = 3`, `border`.

**Fix:** Use M3 `DropdownMenu` with defaults. Remove custom border, shape, and elevation
overrides.

```kotlin
DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
) {
    DropdownMenuItem(
        text = { Text("Option") },
        onClick = onClick,
    )
}
```

### 8.13 Snackbar

**Current:** Custom `containerColor = IosDestructive`, manual text colors.

**Fix:** Use M3 `SnackbarHost` with `SnackbarHostState`. For error snackbars, use
`MaterialTheme.colorScheme.errorContainer` / `onErrorContainer`.

### 8.14 Switch

**Current:** Custom `checkedThumbColor = IosWhite`, `checkedTrackColor = IosSuccess`.

**Fix:** Use M3 `Switch` with default colors. M3 Switch uses `primary` for checked track.

### 8.15 FilterChip

**Current:** Custom colors with `IosAccent`/`IosWhite`/`IosTertiaryFill`, hardcoded
`RoundedCornerShape(20.dp)`.

**Fix:** Use M3 `FilterChip` with default colors and shape.

```kotlin
FilterChip(
    selected = isSelected,
    onClick = onClick,
    label = { Text(label) },
)
```

---

## 9. Icon System

### 9.1 Material Symbols

M3 recommends Material Symbols. The app already has `androidx.compose.material.icons.extended`
in its dependencies, which provides the full Material icon set.

**Replace all Lucide icons with Material equivalents:**

| Lucide Icon | Material Symbol |
|-------------|----------------|
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
| `Lucide.RotateCw` | `Icons.Default.Forward` (or `Replay` mirrored) |
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

### 9.2 Rules

- Remove the Lucide icons dependency from `build.gradle.kts`.
- Use `Icons.Default.*` for filled icons, `Icons.Outlined.*` for outlined variants.
- Update `Routes.kt` to use Material icons for `icon` and `outlinedIcon` properties.
- Icon sizes follow M3 defaults: 24dp for standard, 18dp for small, 36dp for large.

---

## 10. Spacing

### 10.1 Grid

Keep the 4dp spacing grid. This is design-system-agnostic and aligns well with M3.

### 10.2 `Dimens.kt` Target State

```kotlin
object VmDimens {
    // Spacing (4dp grid)
    val SpaceXxs = 2.dp
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp
    val SpaceXxxl = 48.dp
    val FabClearance = 80.dp

    // Borders
    val HairlineBorder = 0.5.dp
    val ThinBorder = 1.dp

    // Screen padding
    val ScreenHorizontalPadding = 16.dp
}
```

**Removed:** `RadiusSmall`, `RadiusMedium`, `RadiusLarge` (use `MaterialTheme.shapes`),
`ButtonHeight` (use M3 default), `FabSize`/`FabContainerSize` (use M3 FAB sizing),
`IconSm`/`IconMd`/`IconLg`/`IconXl` (use M3 24dp default), `NavBarItemSize`/`TouchTarget`
(M3 components handle their own touch targets).

---

## 11. Current Implementation Audit

### 11.1 Theme Layer

**`ui/theme/Theme.kt` (76 lines)**
- Uses `MaterialTheme` — needs `MaterialExpressiveTheme`.
- `IosLightColorScheme` maps iOS tokens to `lightColorScheme` roles manually.
- `IosDarkColorScheme` maps dark iOS tokens to `darkColorScheme` roles manually.
- Shapes defined as 3-tier (10/14/22dp) — needs M3 8-tier scale.
- No dynamic color support.
- No `MotionScheme`.

**`ui/theme/Color.kt` (47 lines)**
- 37 standalone color constants with iOS naming (`IosBackground`, `IosAccent`, `IosLabel`, etc.).
- Dark mode variants duplicated as separate constants (`IosDarkBackground`, `IosDarkAccent`, etc.).
- Shimmer colors (`ShimmerBlue`, `ShimmerGold`, `ShimmerPurple`) — can stay.
- All `Ios*` constants must be eliminated.

**`ui/theme/Type.kt` (82 lines)**
- Inter font family properly defined with 4 weights.
- Only 9 of 15 type scale slots populated. Missing: `displayLarge`, `displayMedium`,
  `displaySmall`, `headlineMedium`, `headlineSmall`, `labelLarge`.
- Custom sizes that diverge from M3 defaults: `headlineLarge` is 28sp (M3: 32sp),
  `titleLarge` is 22sp Bold (M3: 22sp Regular), `titleMedium` is 18sp SemiBold (M3: 16sp
  Medium), `titleSmall` is 15sp SemiBold (M3: 14sp Medium), `bodyLarge` is 15sp (M3: 16sp),
  `bodyMedium` is 14sp (correct), `bodySmall` is 12sp (correct), `labelMedium` is 11sp (M3:
  12sp), `labelSmall` is 10sp (M3: 11sp).

**`ui/theme/Dimens.kt` (39 lines)**
- Spacing grid is good (4dp based).
- Custom radius tokens (`RadiusSmall` 10dp, `RadiusMedium` 14dp, `RadiusLarge` 22dp) conflict
  with M3 shape scale.
- Component size tokens (`ButtonHeight` 50dp, `FabSize` 72dp, `FabContainerSize` 96dp,
  `IconSm`/`Md`/`Lg`/`Xl`, `NavBarItemSize`, `TouchTarget`) should be removed — M3 components
  manage their own sizes.

### 11.2 Components

**`ui/components/GlassCard.kt` (56 lines)**
- Custom `Surface` wrapper with `VmDimens.HairlineBorder`, `VmDimens.RadiusMedium` default,
  `MaterialTheme.colorScheme.surface` color, zero elevation.
- Supports `onClick` and `onLongClick` via `combinedClickable`.
- Should be replaced by M3 `Card` / `OutlinedCard`.

**`ui/components/PrimaryButton.kt` (43 lines)**
- Wraps M3 `Button` with hardcoded `IosAccent`/`IosWhite`, `VmDimens.RadiusMedium` shape,
  `VmDimens.ButtonHeight` (50dp).
- Should be removed — callers should use M3 `Button` directly.

**`ui/components/RecordFab.kt` (61 lines)**
- Uses M3 `FloatingActionButton` but with hardcoded `IosAccent`/`IosWhite`, custom `VmDimens.FabSize`
  (72dp), wrapped in `VmDimens.FabContainerSize` (96dp) `Box`.
- Should be simplified to a standard M3 `LargeFloatingActionButton` with no custom sizing.

**`ui/components/VoiceMindTopAppBar.kt` (81 lines)**
- Uses M3 `TopAppBar` with `Color.Transparent` container.
- Title row contains an icon + title text side by side — non-standard for M3 top app bars.
- Lucide icons for navigation and actions.
- Should use M3 `TopAppBar` with default colors and Material icons. Move the leading icon into
  `navigationIcon` or remove it.

**`ui/components/EmptyStateCard.kt` (44 lines)**
- Wraps `GlassCard`. Will follow `GlassCard` migration to M3 `Card`.
- Uses `MaterialTheme.colorScheme` for colors (good), hardcoded `48.dp` icon size and `12.dp`
  spacer.

**`ui/components/InlinePlayerControls.kt` (186 lines)**
- Custom `TransportButton` with hardcoded dark colors (`#1C1C1E`, `#2C2C2E`, `#D1D1D6`).
- Uses `CircleShape`, manual `clickable` with no ripple.
- `Slider` uses custom track colors.
- Lucide icons (`RotateCcw`, `RotateCw`, `Pause`, `Play`).
- Needs full M3 migration: `FilledTonalIconButton` for transport, default `Slider` colors,
  Material icons.

**`ui/components/VoiceMindTextFieldColors.kt` (17 lines)**
- Returns custom `OutlinedTextFieldDefaults.colors()` with `IosAccent` and `IosOpaqueSeparator`.
- Should be removed — use M3 `OutlinedTextField` defaults.

**`ui/components/CalendarSyncPromptDialog.kt` (72 lines)**
- Uses M3 `AlertDialog` properly. Uses `MaterialTheme.colorScheme` for colors.
- Hardcoded `6.dp` spacer (off 4dp grid — should be `8.dp`).
- Otherwise M3-compliant.

**`ui/components/PermissionRationaleDialog.kt` (86 lines)**
- Uses M3 `AlertDialog` properly. Uses `MaterialTheme.colorScheme` for colors.
- M3-compliant.

**`ui/components/RecordingDialogsHost.kt` (65 lines)**
- Orchestration composable, no UI elements. No changes needed.

### 11.3 Navigation

**`ui/navigation/BottomNavBar.kt` (72 lines)**
- Custom implementation using `Row`, `Column`, `selectable`, manual icon/label layout.
- Uses `MaterialTheme.colorScheme` for colors (good).
- Uses `VmDimens` for spacing.
- Hairline `HorizontalDivider` at top.
- Should be replaced with M3 `NavigationBar` + `NavigationBarItem`.

**`ui/navigation/SidebarDrawer.kt` (74 lines)**
- Uses `ModalDrawerSheet` and `NavigationDrawerItem` (M3 components).
- `drawerContainerColor = MaterialTheme.colorScheme.surface` — should let M3 handle.
- Hardcoded `width = 280.dp` — should let M3 handle.
- Selected state uses custom color logic — M3 `NavigationDrawerItem` handles this.
- Mostly compliant, needs minor cleanup.

**`ui/navigation/AppNavHost.kt` (185 lines)**
- Uses `Scaffold` with `containerColor = IosBackground` — should be
  `MaterialTheme.colorScheme.background` or let M3 default.
- Navigation structure is correct.
- References `IosBackground` directly.

**`ui/navigation/Routes.kt` (36 lines)**
- Defines routes with Lucide icons. Needs Material icons.

### 11.4 Screens

**`ui/home/HomeScreen.kt` (358 lines)**
- Uses `GlassCard`, `RecordFab`, `VoiceMindTopAppBar`, `RecordingDialogsHost`.
- References `IosSeparator` directly for divider color.
- `RoundedCornerShape(VmDimens.RadiusMedium)` hardcoded in list item clipping.
- Lucide icons throughout.

**`ui/recording/RecordingsScreen.kt` (1020 lines)**
- Uses `GlassCard`, `RecordFab`, `VoiceMindTopAppBar`, `EmptyStateCard`.
- SummarizationPopup has extensive custom styling: shimmer brushes using `ShimmerBlue/Gold/Purple`,
  custom shadow, border brush, iridescent background.
- CompletionToast uses `IosAccent` and `IosWhite`.
- RecordingRow uses `IosAccent`, `IosWhite` for multi-select checkbox.
- Surface with `BorderStroke` for date groups.
- `IosSeparator` for dividers.
- `RoundedCornerShape(VmDimens.RadiusSmall)` on dropdown menus.
- MultiSelectTopBar uses `Color.Transparent` container.
- Lucide icons throughout.

**`ui/recording/RecordingBottomSheet.kt` (230 lines)**
- Uses `ModalBottomSheet` (M3) with custom drag handle (`Box` with `IosOpaqueSeparator`).
- Many hardcoded iOS colors: `IosAccent`, `IosDestructive`, `IosWhite`, `IosLabel`,
  `IosSecondaryLabel`, `IosOpaqueSeparator`.
- `RoundedCornerShape(10.dp)` on text field.
- `voiceMindTextFieldColors()` for custom text field styling.
- Action buttons use `Surface` with manual colors instead of M3 `IconButton` variants.
- Lucide icons.

**`ui/recording/RecordingDialogs.kt` (356 lines)**
- TranscriptSheet uses `ModalBottomSheet` with hardcoded `RoundedCornerShape(22.dp)`.
- FilterChips use `IosAccent`/`IosWhite`/`IosTertiaryFill` colors, `RoundedCornerShape(20.dp)`.
- TaskDateLabels use `IosDestructive` for overdue.
- Task items use `IosSuccess` for completed.
- Lucide icons.

**`ui/folders/FoldersScreen.kt` (300 lines)**
- Uses `GlassCard`, `VoiceMindTopAppBar`.
- FAB uses `IosAccent`/`IosWhite`, `CircleShape`.
- Sort icons tinted with `IosAccent` for active state.
- DropdownMenu uses custom shape/border/elevation.
- Folder icon uses `IosAccent`.
- Spring animations on `animateItem` (good — but should use motion scheme specs).
- Lucide icons.

**`ui/folders/FolderDetailScreen.kt` (35 lines)**
- Thin wrapper around `RecordingsScreen`. Uses `VoiceMindTopAppBar`.
- Lucide icons.

**`ui/settings/SettingsScreen.kt` (233 lines)**
- Uses `GlassCard`, `PrimaryButton`, `VoiceMindTopAppBar`.
- Switch colors: `IosWhite` thumb, `IosSuccess` track.
- Snackbar: `IosDestructive` container, `IosWhite` text.
- Section headers reference `VmDimens.ScreenHorizontalPadding`.

**`ui/checklist/ChecklistScreen.kt` (302 lines)**
- Uses `GlassCard`, `VoiceMindTopAppBar`.
- FAB uses `IosAccent`/`IosWhite`, `CircleShape`.
- ActionItemRow uses `RoundedCornerShape(10.dp)`, `IosSuccess` for completed.
- DateLabels use `IosDestructive` for overdue.
- Lucide icons.

**`ui/checklist/TaskDetailScreen.kt` (418 lines)**
- Uses `GlassCard` with `cornerRadius = 12.dp` (hardcoded, off-grid from M3 `medium` 12dp — same
  value but not referenced via theme).
- Uses `IosDestructive`, `IosSuccess`.
- `BasicTextField` with inline `TextStyle` using `MaterialTheme.typography` font sizes (good).
- `DatePicker`, `TimePicker`, `DatePickerDialog` — M3 components (good).
- `RoundedCornerShape(12.dp)` hardcoded on clickable rows.
- Lucide icons.

**`ui/summaries/SummariesScreen.kt` (339 lines)**
- Uses `GlassCard`, `EmptyStateCard`, `VoiceMindTopAppBar`.
- SummaryDetailSheet uses `ModalBottomSheet` with default shape (good).
- "Got it" button uses `IosAccent` container color.
- Lucide icons.

**`ui/auth/SignInScreen.kt` (287 lines)**
- Uses `GlassCard`, `PrimaryButton`, `voiceMindTextFieldColors`.
- Snackbar: `IosDestructive` container, `IosWhite` text.
- OutlinedButton: `RoundedCornerShape(VmDimens.RadiusMedium)`, `IosOpaqueSeparator` border.
- Text fields: `RoundedCornerShape(10.dp)`, `voiceMindTextFieldColors()`.
- `IosSeparator` for divider.
- Lucide icons.

### 11.5 Widget

**`widget/WidgetColors.kt` (17 lines)**
- Standalone `ColorProvider` constants with iOS hex values.
- Not using `GlanceTheme` or M3 Glance tokens.
- Should migrate to `GlanceTheme` with dynamic colors where supported.

---

## 12. Gap Analysis

### What Works Well Today

1. **M3 `MaterialTheme` is already wired** — `lightColorScheme`/`darkColorScheme` with dark
   theme support via `isSystemInDarkTheme()`.
2. **M3 components are used for some things** — `AlertDialog`, `ModalBottomSheet`,
   `NavigationDrawerItem`, `TopAppBar`, `Scaffold`, `FloatingActionButton`, `FilterChip`,
   `Switch`, `Slider`, `DatePicker`, `TimePicker`.
3. **Spacing grid is solid** — 4dp grid in `VmDimens` is M3-compatible.
4. **`MaterialTheme.colorScheme.*` is used in many places** — time labels, metadata, text
   fields, some icon tinting.
5. **Spring animations on list items** — `FoldersScreen` already uses spring-based
   `animateItem`.

### What Needs to Change

| Gap | Impact | Effort | Files Affected |
|-----|--------|--------|----------------|
| Theme uses `MaterialTheme` instead of `MaterialExpressiveTheme` | High | Low | `Theme.kt` |
| No dynamic color support | High | Low | `Theme.kt` |
| 37 iOS color constants used across the codebase | High | Medium | `Color.kt` + all screens/components |
| Custom type scale (diverges from M3 sizes) | Medium | Low | `Type.kt` |
| Only 9/15 type scale slots populated | Medium | Low | `Type.kt` |
| 3-tier custom shape scale instead of M3 8-tier | High | Low | `Dimens.kt`, `Theme.kt` |
| Hardcoded `RoundedCornerShape(Xdp)` across ~20 locations | High | Medium | All screens |
| Custom `GlassCard` wrapper instead of M3 `Card` | High | Medium | 10+ files |
| Custom `PrimaryButton` instead of M3 `Button` | Medium | Low | 3 files |
| Custom `BottomNavBar` instead of M3 `NavigationBar` | High | Medium | `BottomNavBar.kt`, `AppNavHost.kt` |
| Lucide icons instead of Material Symbols | Medium | High | Every screen and component |
| Custom `InlinePlayerControls` with hardcoded colors | Medium | Medium | 1 file |
| Custom text field colors wrapper | Low | Low | 1 file + callers |
| No motion scheme integration | Medium | Medium | Animation callsites |
| SummarizationPopup custom styling | Low | Low | Keep (decorative exception) |
| Widget uses standalone iOS color constants | Low | Medium | `WidgetColors.kt` |
| Component size tokens override M3 defaults | Medium | Low | `Dimens.kt` |
| `Scaffold` uses `IosBackground` instead of M3 background | Medium | Low | `AppNavHost.kt` |

---

## 13. Action Plan (Phased)

### Phase 1: Theme Foundation (Critical — do first)

**Goal:** Switch to `MaterialExpressiveTheme`, add dynamic color, adopt M3 shape scale and
full type scale.

#### 1.1 Update `Theme.kt`

- Replace `MaterialTheme` with `MaterialExpressiveTheme`.
- Add dynamic color support (`dynamicLightColorScheme`/`dynamicDarkColorScheme`).
- Fall back to `expressiveLightColorScheme()` / `darkColorScheme()`.
- Replace 3-tier `VmShapes` with M3 8-tier scale.
- Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

#### 1.2 Update `Type.kt`

- Populate all 15 type scale slots with M3 default sizes.
- Keep Inter as the font family.
- Adjust `fontWeight` to match M3 conventions (Regular for display/headline/body, Medium for
  title/label).

#### 1.3 Update `Color.kt`

- Delete ALL `Ios*` constants and `IosDark*` constants.
- Keep only `ShimmerBlue/Gold/Purple` and `VmSemanticColors` (success, warning).
- Dynamic color + M3 defaults replace everything else.

#### 1.4 Update `Dimens.kt`

- Remove `RadiusSmall`, `RadiusMedium`, `RadiusLarge`.
- Remove `ButtonHeight`, `FabSize`, `FabContainerSize`, `IconSm`/`Md`/`Lg`/`Xl`,
  `NavBarItemSize`, `TouchTarget`.
- Keep spacing tokens and border tokens.

#### 1.5 Update `build.gradle.kts`

- Ensure Compose BOM is `2025.01.00` or later for Expressive APIs.
- Remove `com.composables:icons-lucide-android` dependency.

**Files to modify:** `Theme.kt`, `Color.kt`, `Type.kt`, `Dimens.kt`, `build.gradle.kts`

---

### Phase 2: Component Migration

**Goal:** Replace all custom components with M3 standard components.

#### 2.1 Delete `GlassCard.kt`

Replace all `GlassCard` callsites with M3 `Card`, `OutlinedCard`, or `ElevatedCard`:
- Grouped list containers (Home folders, recordings) → `OutlinedCard` or `Surface` with
  `MaterialTheme.shapes.medium`
- Settings sections → `Card`
- Empty states → `Card`

Affected files: `HomeScreen.kt`, `RecordingsScreen.kt`, `SettingsScreen.kt`,
`ChecklistScreen.kt`, `TaskDetailScreen.kt`, `SummariesScreen.kt`, `SignInScreen.kt`,
`EmptyStateCard.kt`

#### 2.2 Delete `PrimaryButton.kt`

Replace `PrimaryButton` calls with M3 `Button`:

```kotlin
// Before:
PrimaryButton(text = "Sign Out", onClick = onSignOut)

// After:
Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
    Text("Sign Out")
}
```

Affected files: `SettingsScreen.kt`, `SignInScreen.kt`

#### 2.3 Simplify `RecordFab.kt`

Replace with a thin wrapper around M3 `LargeFloatingActionButton` (no custom sizing, no
container Box, no hardcoded colors):

```kotlin
LargeFloatingActionButton(onClick = { /* permission check + record */ }) {
    Icon(Icons.Default.Mic, contentDescription = "Record")
}
```

Affected files: `RecordFab.kt`, `HomeScreen.kt`, `RecordingsScreen.kt`

#### 2.4 Replace `BottomNavBar.kt`

Rewrite using M3 `NavigationBar` + `NavigationBarItem`. Remove all custom layout logic.

Affected files: `BottomNavBar.kt`, `AppNavHost.kt`

#### 2.5 Simplify `VoiceMindTopAppBar.kt`

Remove custom icon-in-title pattern. Use M3 `TopAppBar` with default colors.

Affected files: `VoiceMindTopAppBar.kt`

#### 2.6 Delete `VoiceMindTextFieldColors.kt`

Remove and use M3 `OutlinedTextField` defaults at callsites.

Affected files: `VoiceMindTextFieldColors.kt`, `RecordingBottomSheet.kt`, `SignInScreen.kt`

#### 2.7 Refactor `InlinePlayerControls.kt`

Replace `TransportButton` with M3 `FilledTonalIconButton`. Use default `Slider` colors.

Affected files: `InlinePlayerControls.kt`

---

### Phase 3: Icon Migration

**Goal:** Replace all Lucide icons with Material Symbols.

#### 3.1 Update `Routes.kt`

Change `icon` and `outlinedIcon` from Lucide to `Icons.Default.*` / `Icons.Outlined.*`.

#### 3.2 Update every screen and component file

Systematic find-and-replace of all `Lucide.*` references. See the mapping table in Section 9.

#### 3.3 Remove Lucide dependency

Delete `implementation("com.composables:icons-lucide-android:1.1.0")` from `build.gradle.kts`.

**Files to modify:** Every `.kt` file in `ui/`

---

### Phase 4: Screen-by-Screen Color & Shape Cleanup

**Goal:** Remove all remaining hardcoded colors, shapes, and sizes.

#### 4.1 Replace all `Ios*` color references

Global search for `IosAccent`, `IosWhite`, `IosLabel`, `IosDestructive`, `IosSuccess`,
`IosSeparator`, `IosOpaqueSeparator`, `IosTertiaryFill`, `IosBackground`, and all `IosDark*`
variants. Replace with `MaterialTheme.colorScheme.*` roles or `VmSemanticColors.*`.

#### 4.2 Replace all hardcoded `RoundedCornerShape(...)` calls

Replace with `MaterialTheme.shapes.*` references:

| Hardcoded | Replace With |
|-----------|-------------|
| `RoundedCornerShape(4.dp)` | `MaterialTheme.shapes.extraSmall` |
| `RoundedCornerShape(8.dp)` | `MaterialTheme.shapes.small` |
| `RoundedCornerShape(10.dp)` | `MaterialTheme.shapes.medium` (12dp) |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` |
| `RoundedCornerShape(14.dp)` | `MaterialTheme.shapes.medium` |
| `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` |
| `RoundedCornerShape(20.dp)` | `MaterialTheme.shapes.largeIncreased` |
| `RoundedCornerShape(22.dp)` | `MaterialTheme.shapes.extraLarge` (28dp) |
| `RoundedCornerShape(28.dp)` | `MaterialTheme.shapes.extraLarge` |

#### 4.3 Remove hardcoded component sizes

Replace `Modifier.size(VmDimens.FabSize)`, `Modifier.height(VmDimens.ButtonHeight)`, etc.
with M3 component defaults.

#### 4.4 Fix off-grid spacers

Replace any spacing value not on the 4dp grid (e.g., `6.dp`, `10.dp`, `14.dp`, `20.dp`) with
the nearest `VmDimens.Space*` token.

#### 4.5 RecordingBottomSheet cleanup

- Remove custom drag handle — use M3 built-in.
- Replace all `IosAccent`, `IosDestructive`, `IosWhite` with `MaterialTheme.colorScheme.*`.
- Replace action button `Surface` composables with M3 `FilledTonalIconButton` (pause/resume)
  and `FilledIconButton` (stop/save).

#### 4.6 RecordingDialogs cleanup

- TranscriptSheet: remove hardcoded `RoundedCornerShape(22.dp)` — use M3 default.
- FilterChips: remove custom colors and shapes — use M3 defaults.

#### 4.7 AppNavHost cleanup

- Replace `containerColor = IosBackground` with `MaterialTheme.colorScheme.background` or
  remove (M3 `Scaffold` uses `background` by default with `MaterialExpressiveTheme`).

**Files to modify:** All screen files, all component files, `AppNavHost.kt`

---

### Phase 5: Motion & Widget

**Goal:** Integrate M3 motion scheme and update widget.

#### 5.1 Motion scheme adoption

Replace all inline `spring()`, `tween()` calls with `MaterialTheme.motionScheme.*` specs.
Exception: infinite animations (shimmer, pulse) keep their custom specs.

Affected files: `FoldersScreen.kt`, `RecordingsScreen.kt`, `RecordingBottomSheet.kt` (pulse
animation), `SummarizationPopup` (shimmer — keep custom)

#### 5.2 Widget migration

- Replace `WidgetColors` standalone constants with `GlanceTheme` support.
- Use `androidx.glance.material3` for M3 color tokens in Glance.

Affected files: `WidgetColors.kt`, any Glance widget composables

---

## Summary: Priority Order

| Phase | Effort | Impact | Do When |
|-------|--------|--------|---------|
| 1 — Theme Foundation | Low | Critical | Immediately |
| 2 — Component Migration | Medium | High | After Phase 1 |
| 3 — Icon Migration | Medium | Medium | After Phase 1 (parallel with 2) |
| 4 — Screen Cleanup | Medium-High | High | After Phases 2 & 3 |
| 5 — Motion & Widget | Low-Medium | Medium | After Phase 4 |

The single highest-impact change is **Phase 1** — switching to `MaterialExpressiveTheme` with
dynamic color and the M3 type/shape scale. This alone will transform how the app looks and
feels, as every M3 component will automatically pick up the new theme.
