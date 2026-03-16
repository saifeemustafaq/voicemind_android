# VoiceMind AI — Design & Style Guide

> **North star for all UI development.** Every screen, component, and token must follow this guide. When in doubt, reference this document before writing a single line of UI code.

---

## 1. Foundation

### Framework & APIs

| Concern | Value |
|---|---|
| UI toolkit | Jetpack Compose |
| Design system | **Material 3** (`MaterialTheme`) |
| Compose BOM | `2025.12.00` (material3 1.4.0 stable) |
| Theme entry point | `VoiceMindAITheme` in `ui/theme/Theme.kt` |

> **Architecture decision:** `VoiceMindAITheme` uses standard `MaterialTheme` — this is the **permanent** choice, not a workaround. `MaterialExpressiveTheme` was removed from the Material3 stable track in 1.4.0 and only exists in alpha builds (1.5.0-alpha+). It is not appropriate for production apps and has no ETA for stable graduation. Do not use `MaterialExpressiveTheme`, `MotionScheme`, or `ExperimentalMaterial3ExpressiveApi`.

### Theme Setup

Always wrap content in `VoiceMindAITheme`. Never call `MaterialTheme { }` directly.

```kotlin
VoiceMindAITheme(darkTheme = isSystemInDarkTheme()) {
    // all content here
}
```

Dynamic color is **on by default** on Android 12+ (API 31+). The user's wallpaper drives the entire palette. Brand-seeded fallback applies on older Android. Never override this behavior with hardcoded colors.

---

## 2. Color

### Rules

1. **Never use raw hex values** in any UI file. No `Color(0xFF...)`, no `Color.Red`, no `Color.White`.
2. **Never reference `Color.kt` constants directly** from UI code. Access color exclusively through `MaterialTheme.colorScheme.*`.
3. The only exceptions are the shimmer animation colors (`ShimmerBlue`, `ShimmerGold`, `ShimmerPurple`) — these are standalone animation values, not semantic roles.

### Color Role Reference

Use these semantic roles — the actual hex values adapt automatically to light/dark and dynamic color:

| Role | Usage |
|---|---|
| `colorScheme.primary` | Brand actions, active icons, key interactive elements |
| `colorScheme.onPrimary` | Content placed on top of `primary` |
| `colorScheme.primaryContainer` | Tinted container for selected/highlighted states |
| `colorScheme.onPrimaryContainer` | Content on `primaryContainer` |
| `colorScheme.secondary` | Supporting actions, secondary interactive elements |
| `colorScheme.onSecondary` | Content on `secondary` |
| `colorScheme.secondaryContainer` | Tinted container for secondary selections |
| `colorScheme.onSecondaryContainer` | Content on `secondaryContainer` |
| `colorScheme.tertiary` | **Success / positive / completed states** (e.g., checked task icons) |
| `colorScheme.onTertiary` | Content on `tertiary` |
| `colorScheme.tertiaryContainer` | Container for success states |
| `colorScheme.onTertiaryContainer` | Content on `tertiaryContainer` |
| `colorScheme.error` | Destructive actions, overdue dates, validation errors |
| `colorScheme.onError` | Content on `error` |
| `colorScheme.errorContainer` | Container for destructive/error actions (e.g., delete button) |
| `colorScheme.onErrorContainer` | Content on `errorContainer` |
| `colorScheme.background` | App background — set automatically by `Scaffold`, never override |
| `colorScheme.onBackground` | Content directly on background |
| `colorScheme.surface` | Card and sheet backgrounds |
| `colorScheme.onSurface` | Primary content on surface |
| `colorScheme.surfaceContainerLow` | Slightly elevated surface (cards, list containers) |
| `colorScheme.surfaceVariant` | Alternative surface (inactive slider track, chip backgrounds) |
| `colorScheme.onSurfaceVariant` | Secondary/placeholder text, inactive icons |
| `colorScheme.outline` | Borders, dividers that need contrast |
| `colorScheme.outlineVariant` | Subtle dividers (e.g., `HorizontalDivider`, section separators) |
| `colorScheme.inverseSurface` | Snackbar background |
| `colorScheme.inverseOnSurface` | Snackbar text/icons |
| `colorScheme.inversePrimary` | Snackbar action button color |

### Semantic Mapping Cheat Sheet

| Intent | Role to use |
|---|---|
| Primary CTA button | `primary` / `onPrimary` (M3 `Button` default — no override needed) |
| FAB | `primaryContainer` / `onPrimaryContainer` (M3 `FloatingActionButton` default) |
| Destructive / delete button | `errorContainer` / `onErrorContainer` |
| Success / complete / done | `tertiary` |
| Overdue / warning / error text | `error` |
| Divider line | `outlineVariant` |
| Text field border | `outline` (unfocused), `primary` (focused) |
| Play/pause button | `primaryContainer` / `onPrimaryContainer` |
| Skip/rewind buttons | `secondaryContainer` / `onSecondaryContainer` |
| Snackbar | `inverseSurface` / `inverseOnSurface` (M3 default — no override needed) |
| Section label text | `onSurfaceVariant` |
| Placeholder / hint text | `onSurfaceVariant` |

### Brand Seed & Fallback Palette

Brand seed: `#0061A4` (steel blue). Light scheme primary: `#0061A4`. Dark scheme primary: `#9ECAFF`. All values are sourced from Material Theme Builder with this seed.

---

## 3. Typography

All text must use `MaterialTheme.typography.*`. Never set `fontSize`, `fontFamily`, or `fontWeight` manually in UI code.

Font family: **Inter** (loaded via `InterFontFamily` in `Type.kt`).

### Full Type Scale

| Style | Size | Line Height | Weight | Typical Use |
|---|---|---|---|---|
| `displayLarge` | 57sp | 64sp | Normal | Hero / splash text |
| `displayMedium` | 45sp | 52sp | Normal | Large marketing text |
| `displaySmall` | 36sp | 44sp | Normal | Section hero |
| `headlineLarge` | 32sp | 40sp | SemiBold | Page titles |
| `headlineMedium` | 28sp | 36sp | SemiBold | Dialog titles |
| `headlineSmall` | 24sp | 32sp | SemiBold | Card headers |
| `titleLarge` | 22sp | 28sp | Bold | **Top app bar title** |
| `titleMedium` | 16sp | 24sp | SemiBold | Section headers, list item primary |
| `titleSmall` | 14sp | 20sp | Medium | Sub-section labels |
| `bodyLarge` | 16sp | 24sp | Normal | Primary body text |
| `bodyMedium` | 14sp | 20sp | Normal | Default body, list item text |
| `bodySmall` | 12sp | 16sp | Normal | Secondary body, section category labels (e.g., "TO-DO", "DONE") |
| `labelLarge` | 14sp | 20sp | Medium | Buttons, tabs |
| `labelMedium` | 12sp | 16sp | Medium | Chip text, badge text |
| `labelSmall` | 11sp | 16sp | Medium | Date/time metadata, supporting labels |

### Common Mappings

| UI Element | Style |
|---|---|
| Top app bar title | `titleLarge` |
| Section header (e.g., "TO-DO") | `bodySmall`, color `onSurfaceVariant` |
| Card primary text | `bodyMedium` |
| Date / time labels under list items | `labelSmall` |
| Button label | `labelLarge` (M3 Button default — no override needed) |
| Dialog title | `headlineMedium` |
| Empty state message | `bodyMedium`, color `onSurfaceVariant` |

---

## 4. Shape (Corner Radius)

**Never use `RoundedCornerShape(Xdp)` with hardcoded values.** Use `MaterialTheme.shapes.*` everywhere. All shape tokens use `RoundedCornerShape` internally.

| Token | Radius | Use |
|---|---|---|
| `MaterialTheme.shapes.extraSmall` | 4dp | Chips, text fields, menus, snackbars, tooltips |
| `MaterialTheme.shapes.small` | 8dp | Small chips, small cards |
| `MaterialTheme.shapes.medium` | 12dp | Cards (`GlassCard`), small FABs |
| `MaterialTheme.shapes.large` | 16dp | FABs, extended FABs, nav drawers |
| `MaterialTheme.shapes.extraLarge` | 28dp | Large FABs, modal bottom sheets |
| `CircleShape` | 50% | Round FABs, avatar containers |

---

## 5. Spacing & Dimensions

All spacing uses the **4dp grid**. Use `VmDimens.*` tokens — do not write raw `Xdp` values for layout spacing.

```kotlin
import com.voicemind.ui.theme.VmDimens
```

### Spacing Tokens

| Token | Value | Use |
|---|---|---|
| `VmDimens.SpaceXxs` | 2dp | Micro gaps (icon-to-text in inline labels) |
| `VmDimens.SpaceXs` | 4dp | Tight internal padding |
| `VmDimens.SpaceSm` | 8dp | Item internal padding, small gaps |
| `VmDimens.SpaceMd` | 12dp | Medium internal padding |
| `VmDimens.SpaceLg` | 16dp | Standard screen padding, card inner padding |
| `VmDimens.SpaceXl` | 24dp | Section spacing, FAB padding |
| `VmDimens.SpaceXxl` | 32dp | Large section gaps |
| `VmDimens.SpaceXxxl` | 48dp | Page-level gaps |

### Component Tokens

| Token | Value | Use |
|---|---|---|
| `VmDimens.ScreenHorizontalPadding` | 16dp | Horizontal padding for all screen content |
| `VmDimens.FabClearance` | 80dp | Bottom spacer to prevent FAB from covering last list item |
| `VmDimens.ButtonHeight` | 40dp | Standard M3 button height |
| `VmDimens.TouchTarget` | 48dp | Minimum touch target (per M3 accessibility guidelines) |
| `VmDimens.IconSm` | 16dp | Small icons (inline date/metadata icons) |
| `VmDimens.IconMd` | 22dp | Standard icons |
| `VmDimens.IconLg` | 32dp | Large icons |
| `VmDimens.IconXl` | 48dp | Extra-large / avatar-size icons |
| `VmDimens.HairlineBorder` | 1dp | Divider / border stroke (use `outlineVariant` color) |

---

## 6. Elevation

M3 uses **tonal elevation** (surface tint at low opacity) for depth — not shadows or explicit border strokes.

| Level | Usage |
|---|---|
| `0.dp` | Flat surfaces that blend into background |
| `1.dp` | Cards (`GlassCard` default) — adds subtle `surfaceContainerLow` tint |
| `2.dp` | Elevated cards on complex backgrounds |
| `3.dp` | App bar when scrolled |
| `6.dp` | FAB resting state |
| `8.dp` | FAB pressed state |

**Never add a `BorderStroke` to a card to simulate elevation.** Use `tonalElevation` on `Surface` instead.

---

## 7. Core Components

### GlassCard

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    innerPadding = VmDimens.SpaceLg,  // default, override to 0.dp when laying out custom content
    onClick = { /* optional */ },
    onLongClick = { /* optional */ },
) {
    // content
}
```

- Uses `MaterialTheme.shapes.medium` (12dp corners)
- `color = surfaceContainerLow`, `tonalElevation = 1.dp`
- No `BorderStroke`, no `shadowElevation`, no `cornerRadius` parameter

### PrimaryButton

```kotlin
PrimaryButton(text = "Save", onClick = { ... }, enabled = true)
```

- Wraps M3 `Button` with no color overrides — inherits `primary`/`onPrimary` automatically
- Height: `VmDimens.ButtonHeight` (40dp)

### RecordFab

```kotlin
RecordFab(onClick = { ... })
```

- M3 `FloatingActionButton` with no color or shape overrides
- Inherits `primaryContainer`/`onPrimaryContainer` + `extraLarge` shape

### FloatingActionButton (general)

Always use M3 defaults. Do **not** pass `containerColor` or `contentColor` manually:

```kotlin
FloatingActionButton(
    onClick = { ... },
    shape = CircleShape,  // only override when intentionally round
) {
    Icon(Icons.Default.Add, contentDescription = "Add")
}
```

### VoiceMindTopAppBar

```kotlin
VoiceMindTopAppBar(
    title = "Screen Title",
    icon = Icons.Default.Mic,     // leading icon from Material Icons
    onOpenDrawer = { ... },       // shows hamburger menu
    onBack = { ... },             // shows back arrow (mutually exclusive with onOpenDrawer)
    onSettings = { ... },         // optional trailing settings icon
)
```

- Title uses `titleLarge` typography
- Uses M3 `TopAppBar` defaults — no manual `colors` override

### NavigationBar (Bottom Nav)

```kotlin
NavigationBar {
    NavigationBarItem(
        selected = currentRoute == item.route,
        onClick = { ... },
        icon = { Icon(if (selected) item.icon else item.outlinedIcon, item.label) },
        label = { Text(item.label) },
    )
}
```

- No manual colors — M3 `NavigationBarItem` handles `indicator`, selected/unselected icon tint automatically

### Modal Navigation Drawer

Use `ModalDrawerSheet` + `NavigationDrawerItem`. No custom width except the M3 standard 360dp.

### Switch

Use M3 `Switch` with **no `SwitchDefaults.colors(...)` override**. M3 defaults correctly use `primary`/`onPrimary`.

```kotlin
Switch(checked = state, onCheckedChange = { ... })
```

### Snackbar

Use `SnackbarHost` + `SnackbarHostState` with **no color overrides**. M3 defaults use `inverseSurface`/`inverseOnSurface` automatically. For error snackbars, adjust the message text only — do not change container color.

### Text Fields

```kotlin
OutlinedTextField(
    value = text,
    onValueChange = { text = it },
    shape = MaterialTheme.shapes.extraSmall,  // default for M3 outlined text fields
    colors = VoiceMindTextFieldColors(),       // correct focused/unfocused border colors
)
```

### HorizontalDivider

```kotlin
HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
```

---

## 8. Icons

Use **Material Icons** from `androidx.compose.material.icons` (already in `material-icons-extended`):

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic

Icon(Icons.Default.Mic, contentDescription = "...")
```

For icons that require RTL mirroring, use the `AutoMirrored` variant:

```kotlin
import androidx.compose.material.icons.automirrored.filled.ArrowBack

Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
```

For navigation items, use `Icons.Filled.*` for the selected state and `Icons.Outlined.*` for the unselected state (see `Routes.kt`).

Icon sizes:

| Use | Size |
|---|---|
| Inline metadata (date, flag) | `VmDimens.IconSm` (16dp) |
| Standard UI icons | `VmDimens.IconMd` (22dp) or omit `size` for M3 defaults |
| Large decorative icons | `VmDimens.IconLg` (32dp) |

---

## 9. Screen Layout Pattern

Every screen follows this structure:

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    VoiceMindTopAppBar(title = "...", ...)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VmDimens.ScreenHorizontalPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // content

        Spacer(modifier = Modifier.height(VmDimens.FabClearance)) // if screen has a FAB
    }
}

// FAB overlay (if applicable)
Box(modifier = Modifier.fillMaxSize()) {
    FloatingActionButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(VmDimens.SpaceXl),
        onClick = { ... },
    ) { ... }
}
```

### Scaffold Usage

`Scaffold` is used in `AppNavHost` only. Individual screens do **not** use `Scaffold`. Do not pass `containerColor` to `Scaffold` — the M3 default `background` applies automatically.

---

## 10. State & Empty States

Empty state pattern:

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = VmDimens.SpaceLg) {
    Text(
        text = "No items yet",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = VmDimens.SpaceSm),
    )
}
```

Loading state: Use `CircularProgressIndicator()` with no color override (inherits `primary`).

---

## 11. Glance Widgets

Glance 1.1.1 constraints:

- Use `ColorProvider(Color(...))` — single-argument only. The two-argument light/dark overload is **not supported**.
- Derive widget colors from `WidgetColors.*` object in `widget/WidgetColors.kt`.
- Widget colors are brand-seeded M3 values (light-mode only, hardcoded — Glance limitation).

---

## 12. Anti-Patterns (Never Do)

| ❌ Wrong | ✅ Correct |
|---|---|
| `Color(0xFF0061A4)` in UI | `MaterialTheme.colorScheme.primary` |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` |
| `FontFamily(...)` in composable | `MaterialTheme.typography.bodyMedium` |
| `fontSize = 14.sp` inline | `MaterialTheme.typography.bodyMedium` |
| `Scaffold(containerColor = ...)` | `Scaffold { ... }` with no color override |
| `ButtonDefaults.buttonColors(containerColor = ...)` | `Button { }` with no color override |
| `SwitchDefaults.colors(checkedThumbColor = ...)` | `Switch(...)` with no color override |
| `FloatingActionButton(containerColor = IosAccent)` | `FloatingActionButton { }` with no color override |
| `BorderStroke` on a card | `tonalElevation = 1.dp` on `Surface` |
| `IosSomething` reference | `MaterialTheme.colorScheme.*` semantic role |
| `MaterialTheme { }` directly | `VoiceMindAITheme { }` |
| Hardcoded `16.dp` padding in content | `VmDimens.ScreenHorizontalPadding` |

---

## 13. File & Package Conventions

| Area | Package |
|---|---|
| Theme (colors, type, dimens, theme) | `com.voicemind.ui.theme` |
| Shared components | `com.voicemind.ui.components` |
| Navigation | `com.voicemind.ui.navigation` |
| Screens | `com.voicemind.ui.<feature>` |
| Widget | `com.voicemind.widget` |

Key files:

- [Theme.kt](android/app/src/main/java/com/voicemind/ui/theme/Theme.kt) — `VoiceMindAITheme` entry point, dynamic color logic
- [Color.kt](android/app/src/main/java/com/voicemind/ui/theme/Color.kt) — M3 light/dark fallback palettes (brand seed `#0061A4`)
- [Type.kt](android/app/src/main/java/com/voicemind/ui/theme/Type.kt) — Full 15-style type scale with Inter
- [Dimens.kt](android/app/src/main/java/com/voicemind/ui/theme/Dimens.kt) — Spacing, component sizing tokens
- [GlassCard.kt](android/app/src/main/java/com/voicemind/ui/components/GlassCard.kt) — Primary card surface component
- [WidgetColors.kt](android/app/src/main/java/com/voicemind/widget/WidgetColors.kt) — Glance widget color constants
