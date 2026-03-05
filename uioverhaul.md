# VoiceMind AI — UI/UX Overhaul Strategy

## Table of Contents

1. [Design Research: Modern Minimalist Principles](#1-design-research-modern-minimalist-principles)
2. [Current Implementation Audit](#2-current-implementation-audit)
3. [Gap Analysis](#3-gap-analysis)
4. [Action Plan](#4-action-plan-phased)

---

## 1. Design Research: Modern Minimalist Principles

### 1.1 Corner Radius & Curves

iOS and modern minimalist design use **continuous (superelliptical) corners** rather than simple
circular arcs. The difference is subtle but critical — continuous corners have a smoother transition
into the straight edge, eliminating the tiny visual "kink" that circular corners produce. Apple calls
this concentricity: nested shapes share a common corner center so inner and outer radii feel
visually harmonious.

**Recommended radius scale (3 tiers):**

| Token    | Value  | Use Case                                     |
|----------|--------|----------------------------------------------|
| Small    | 10dp   | Text fields, chips, small cards, inline tags  |
| Medium   | 14dp   | Cards, buttons, list item containers          |
| Large    | 22dp   | Bottom sheets, modals, full-width panels      |

Using only three radius values across the entire app creates visual consistency. Avoid one-off
values like 2.5dp, 4dp, 8dp, 16dp, 20dp — they fragment the design language.

### 1.2 Color Palette

Modern minimalist apps follow a strict color discipline:

**Background layering (light mode):**

| Layer              | Hex       | Role                                |
|--------------------|-----------|-------------------------------------|
| Base background    | `#F2F2F7` | Screen canvas                       |
| Elevated surface   | `#FFFFFF` | Cards, sheets, modals               |
| Grouped background | `#F2F2F7` | Inset grouped sections              |

**Text hierarchy:**

| Token          | Value                    | Usage                    |
|----------------|--------------------------|--------------------------|
| Primary        | `#000000`                | Titles, body text        |
| Secondary      | `#3C3C43` at 60% alpha   | Subtitles, metadata      |
| Tertiary       | `#3C3C43` at 30% alpha   | Placeholders, hints      |

**Accent & semantic:**

| Token       | Hex       | Usage                              |
|-------------|-----------|-------------------------------------|
| Accent      | `#007AFF` | Primary actions, links, active tabs |
| Destructive | `#FF3B30` | Delete, discard, errors             |
| Success     | `#34C759` | Completed states, toggles           |
| Warning     | `#FF9500` | Alerts, deadlines                   |

Key principles:
- One accent color, used sparingly — only for interactive elements and active states.
- Semantic colors appear only in context (destructive on delete, success on completion).
- No gradients on standard UI elements. Flat, solid fills.
- Separators should be near-invisible (`#3C3C43` at 12% alpha, 0.5dp thickness).

**Dark mode** mirrors this structure with inverted luminance:

| Layer              | Hex       |
|--------------------|-----------|
| Base background    | `#000000` |
| Elevated surface   | `#1C1C1E` |
| Grouped background | `#2C2C2E` |
| Primary text       | `#FFFFFF` |
| Secondary text     | `#EBEBF5` at 60% alpha |

### 1.3 Typography

Minimalist typography rules:

- **One font family** — Inter is a strong choice. It was designed for screens, has excellent
  legibility, and pairs well with the iOS aesthetic.
- **Limited weight range** — Use only Regular (400) and SemiBold (600). Bold (700) reserved
  exclusively for the largest headlines. Medium (500) is too close to Regular to provide contrast.
- **Strict hierarchy** — No more than 4-5 distinct type sizes visible on any single screen.
- **Generous line height** — 1.3x to 1.5x the font size for body text.
- **Letter spacing** — Slightly looser tracking on small captions (0.5-1sp), tighter on large
  headlines (-0.5sp).

Current type scale is well-structured and follows iOS conventions. No changes needed to the scale
itself — the issue is inconsistent application.

### 1.4 Spacing System

Minimalist design demands a strict spacing grid. Every measurement should be a multiple of 4dp:

| Token       | Value | Usage                                          |
|-------------|-------|-------------------------------------------------|
| `xxs`       | 2dp   | Hairline gaps (icon-to-text tight)              |
| `xs`        | 4dp   | Minimum internal padding                        |
| `sm`        | 8dp   | List item vertical spacing, tight gaps          |
| `md`        | 12dp  | Standard internal padding, card content gaps    |
| `lg`        | 16dp  | Screen horizontal padding, section spacing      |
| `xl`        | 24dp  | Section breaks, modal padding                   |
| `xxl`       | 32dp  | Large vertical separations                      |
| `xxxl`      | 48dp  | Screen-level breathing room                     |
| `fabClear`  | 80dp  | Bottom spacer to clear FAB                      |

Consistent use of this grid makes the app feel intentional and calm.

### 1.5 Elevation & Depth

Modern minimalism avoids drop shadows almost entirely. Depth is communicated through:

- **Background layering** — Different background tints for different z-levels.
- **Subtle borders** — 0.5dp hairline borders at 8-12% alpha replace shadows on cards.
- **Translucency** — Frosted glass / blur effects for overlays (iOS Liquid Glass paradigm).
- **Zero elevation** — Cards, buttons, and FABs should have `0.dp` elevation. The only exception
  is temporary elevated surfaces (dialogs, dropdown menus, tooltips) which get 1-4dp.

Shadow-heavy UI (like the current BottomNavBar at 10-16dp elevation) feels dated and heavy.

### 1.6 Motion & Interaction

- **Spring-based curves** — Use `spring(dampingRatio = 0.8f, stiffness = 300f)` instead of
  linear/ease-in-out for list animations and sheet transitions.
- **Duration** — 200-350ms for micro-interactions, 300-500ms for sheet/dialog transitions.
- **Haptics** — Light haptic feedback on toggle, record start/stop, and destructive actions.
- **Reduce motion** — Respect `AccessibilityManager.isReduceMotionEnabled`.

---

## 2. Current Implementation Audit

### 2.1 Theme Layer

**Files:**
- `android/app/src/main/java/com/voicemind/ui/theme/Color.kt`
- `android/app/src/main/java/com/voicemind/ui/theme/Theme.kt`
- `android/app/src/main/java/com/voicemind/ui/theme/Type.kt`

**Strengths:**
- iOS-inspired color tokens (IosBackground, IosAccent, etc.) — well-named and semantically clear.
- Type scale follows iOS conventions with Inter font family.
- MaterialTheme color scheme is properly wired.

**Issues:**
- No dark mode color scheme — only `lightColorScheme` exists.
- Theme shapes define 5 tiers (extraSmall=4, small=8, medium=10, large=12, extraLarge=22) but
  most screens ignore them and hardcode radius values inline.
- No spacing/dimension tokens — every screen defines its own padding values.
- No centralized animation specifications.

### 2.2 Core Components

#### GlassCard (`ui/components/GlassCard.kt`)
- Default corner radius: 10dp (good, but some callers override to 12dp).
- Color: solid `IosWhite` — no glass/translucency effect despite the name.
- Zero elevation, zero tonal elevation — correct.
- Missing: subtle border to define card edges against white backgrounds.

#### PrimaryButton (`ui/components/PrimaryButton.kt`)
- Corner radius: 12dp (hardcoded, not from theme shapes).
- Height: 50dp (appropriate).
- Uses theme colors correctly.

#### RecordFab (`ui/components/RecordFab.kt`)
- 120dp outer box with radial gradient from `IosBackground` to transparent — visually heavy.
- Inner FAB: 72dp circle — appropriate size.
- The gradient creates a visible "halo" that clashes with clean backgrounds.

#### EmptyStateCard (`ui/components/EmptyStateCard.kt`)
- Wraps GlassCard — inherits its styling.
- Icon size: 48dp, secondary label color — appropriate.
- No illustration or visual differentiation — feels generic.

#### VoiceMindTopAppBar (`ui/components/VoiceMindTopAppBar.kt`)
- Transparent container — correct for layered backgrounds.
- Icon + title pattern is clean.
- Info icon uses `.clickable` without proper touch target (18dp icon, no padding).

### 2.3 Navigation

#### BottomNavBar (`ui/navigation/BottomNavBar.kt`)
- Each tab is a 48dp circle with CircleShape, heavy shadow (10-16dp elevation), and 0.5dp border.
- Hardcoded colors: `Color.White`, `Color.Black`, `Color(0xFF3C3C43)` — not from theme.
- Shadow uses `ambientColor = Color.Black.copy(alpha = 0.5f)` — far too prominent.
- The circular floating tab style is visually distinctive but conflicts with minimalism. The shadows
  make the bottom bar the heaviest element on screen.

#### SidebarDrawer (`ui/navigation/SidebarDrawer.kt`)
- Clean implementation, uses theme colors correctly.
- Proper NavigationDrawerItem with subtle selected state.

### 2.4 Screen-by-Screen Issues

#### HomeScreen
- Section label padding: `(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)` — inconsistent
  with other screens.
- Divider start offset: `52.dp` (folder list) vs `16.dp` (recordings) — inconsistent.
- "View more" text uses `bodyMedium` + `IosAccent` — correct, but no chevron hint.

#### RecordingsScreen
- Date section headers: `labelSmall` + `(start = 4.dp, top = 12.dp, bottom = 2.dp)` — different
  padding from HomeScreen section headers.
- SummarizationPopup: hardcoded shimmer colors `#5E9EFF`, `#FFD700`, `#B47FFF` — not in theme.
- SummarizationPopup border: `Color(0x33888888)` — hardcoded.
- CompletionToast: uses `Color.White` instead of `IosWhite`.
- RecordingRow cards each get their own GlassCard — good separation but could use subtle
  animation on appear.

#### SettingsScreen
- Section labels have inconsistent padding: ACCOUNT uses `(start=16, top=8, bottom=4)`,
  NAVIGATION uses `(start=16, bottom=4)` — missing top padding.
- Error snackbar uses `containerColor = IosDestructive` but SignInScreen snackbar omits it.

#### ChecklistScreen
- ActionItemRow uses `clip(RoundedCornerShape(10.dp))` — correct.
- GlassCard explicitly passes `cornerRadius = 10.dp` — redundant (it is the default).
- FAB at bottom-end with `24.dp` padding — inconsistent with RecordFab positioning.

#### SummariesScreen
- SummaryRow vertical spacing: `6.dp` between text elements — off the 4dp grid (should be 8dp).
- SummaryDetailSheet horizontal padding: `20.dp` — should be 24dp to match other sheets.
- SummariesInfoSheet uses `24.dp` horizontal padding — correct.

#### SignInScreen
- GlassCard inner padding: `20.dp` — off-grid (should be 24dp or 16dp).
- Text field shape: `RoundedCornerShape(10.dp)` — correct.
- Google button: `RoundedCornerShape(12.dp)` — matches PrimaryButton. Good.
- Snackbar: no explicit `containerColor` — uses default Material color, inconsistent with
  SettingsScreen error snackbar.

#### RecordingBottomSheet
- Sheet shape: 22dp top corners — correct for large radius token.
- Drag handle: manual Box with 2.5dp radius — should use standard handle or at least 4dp.
- Text field: `RoundedCornerShape(10.dp)` — correct.
- Action button sizes: 52dp, 56dp, 72dp — three different sizes for the three controls. Could
  be simplified to two tiers (52dp secondary, 72dp primary).

### 2.5 Widget

- `WidgetColors.kt` defines its own color tokens that diverge from `Color.kt`:
  - Widget `SecondaryLabel = #8E8E93` vs app `IosSecondaryLabel = #3C3C43 at 60%`.
- Widget sign-in button: `cornerRadius(20.dp)` — off from app button radius (12dp).
- Widget uses standalone font sizes (14sp, 11sp, 12sp, 28sp) — not from Type.kt scale.

### 2.6 Hardcoded Values Summary

**Corner radii found in code (should be reduced to 3 tokens):**

| Value   | Occurrences | Should Become |
|---------|-------------|---------------|
| 2.5dp   | 1           | `xs` or remove (use standard drag handle) |
| 4dp     | Theme only  | Remove from app shape scale |
| 8dp     | Theme only  | Remove from app shape scale |
| 10dp    | ~8          | `RadiusSmall` (10dp) |
| 12dp    | ~4          | `RadiusMedium` (14dp) |
| 16dp    | ~2          | `RadiusMedium` (14dp) |
| 20dp    | ~3          | `RadiusLarge` (22dp) or keep for chips |
| 22dp    | ~3          | `RadiusLarge` (22dp) |

**Hardcoded colors (should be replaced with theme tokens):**

| Hardcoded Value       | Location                 | Replace With         |
|-----------------------|--------------------------|----------------------|
| `Color.White`         | BottomNavBar, CompletionToast, RecordingRow | `IosWhite` |
| `Color.Black`         | BottomNavBar             | `IosLabel`           |
| `Color(0xFF3C3C43)`   | BottomNavBar             | `IosSecondaryLabel`  |
| `Color(0xFF5E9EFF)`   | SummarizationPopup       | New `ShimmerBlue` token |
| `Color(0xFFFFD700)`   | SummarizationPopup       | New `ShimmerGold` token |
| `Color(0xFFB47FFF)`   | SummarizationPopup       | New `ShimmerPurple` token |
| `Color(0x33888888)`   | SummarizationPopup       | `IosSeparator` or new token |

---

## 3. Gap Analysis

### What the App Does Well

1. **Consistent component reuse** — GlassCard, VoiceMindTopAppBar, PrimaryButton, and
   EmptyStateCard are used across all screens.
2. **iOS-inspired color naming** — Semantic, clear token names.
3. **Clean type scale** — Well-defined hierarchy using Inter.
4. **Zero-elevation cards** — Already avoids heavy card shadows.
5. **Functional parity** — All screens work and the UX flow is logical.

### Where It Falls Short of "Rich Minimalism"

| Gap                          | Impact   | Effort |
|------------------------------|----------|--------|
| No design token file for spacing/radius | High | Low |
| 10+ different corner radii   | High     | Low    |
| Hardcoded colors outside theme | Medium | Low    |
| BottomNavBar heavy shadows   | High     | Medium |
| No dark mode                 | High     | High   |
| GlassCard is just plain white | Medium  | Medium |
| RecordFab radial gradient halo | Medium | Low    |
| Inconsistent section header padding | Medium | Low |
| Inconsistent modal padding   | Low      | Low    |
| No spring animations         | Low      | Medium |
| Widget color divergence       | Low      | Low    |
| No haptic feedback            | Low      | Low    |

---

## 4. Action Plan (Phased)

### Phase 1: Design Token Foundation

**Goal:** Centralize all design values so every screen and component draws from one source of truth.

**Priority: CRITICAL — do this first, everything else depends on it.**

#### 1.1 Create `Dimens.kt`

Create `android/app/src/main/java/com/voicemind/ui/theme/Dimens.kt` with:

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

    // Corner radius (3 tiers)
    val RadiusSmall = 10.dp   // inputs, chips, small cards
    val RadiusMedium = 14.dp  // cards, buttons
    val RadiusLarge = 22.dp   // sheets, modals

    // Component sizes
    val ButtonHeight = 50.dp
    val FabSize = 72.dp
    val FabContainerSize = 96.dp
    val IconSm = 16.dp
    val IconMd = 22.dp
    val IconLg = 32.dp
    val IconXl = 48.dp
    val NavBarItemSize = 44.dp
    val TouchTarget = 44.dp

    // Borders
    val HairlineBorder = 0.5.dp
    val ThinBorder = 1.dp

    // Screen padding
    val ScreenHorizontalPadding = 16.dp
}
```

#### 1.2 Update `Theme.kt` Shapes

Reduce shape tiers from 5 to 3 meaningful values:

```kotlin
private val VmShapes = Shapes(
    small = RoundedCornerShape(VmDimens.RadiusSmall),    // 10dp
    medium = RoundedCornerShape(VmDimens.RadiusMedium),  // 14dp
    large = RoundedCornerShape(VmDimens.RadiusLarge),    // 22dp
)
```

#### 1.3 Replace All Hardcoded Colors

Audit every file and replace:
- `Color.White` -> `IosWhite`
- `Color.Black` -> `IosLabel`
- `Color(0xFF3C3C43)` -> theme token
- Shimmer colors -> new named tokens in `Color.kt`

#### 1.4 Add Dark Mode Colors to `Color.kt`

Add dark-mode equivalents:

```kotlin
val IosDarkBackground = Color(0xFF000000)
val IosDarkSecondaryBackground = Color(0xFF1C1C1E)
val IosDarkTertiaryBackground = Color(0xFF2C2C2E)
val IosDarkLabel = Color(0xFFFFFFFF)
val IosDarkSecondaryLabel = Color(0x99EBEBF5)
val IosDarkSeparator = Color(0x52545458)
val IosDarkAccent = Color(0xFF0A84FF)
```

Update `Theme.kt` to accept `darkTheme: Boolean` parameter and switch between `lightColorScheme`
and `darkColorScheme`.

**Files to modify:**
- Create: `ui/theme/Dimens.kt`
- Modify: `ui/theme/Color.kt`, `ui/theme/Theme.kt`
- Modify: Every file that hardcodes `Color.White`, `Color.Black`, or inline color values

---

### Phase 2: Core Component Refinement

**Goal:** Make shared components feel premium and consistent.

#### 2.1 GlassCard Redesign

Add a subtle hairline border so cards have definition against the `#F2F2F7` background:

```kotlin
Surface(
    modifier = modifier.border(
        width = VmDimens.HairlineBorder,
        color = IosSeparator,
        shape = RoundedCornerShape(VmDimens.RadiusMedium),
    ),
    shape = RoundedCornerShape(VmDimens.RadiusMedium),  // 14dp instead of 10dp
    color = IosWhite,
    shadowElevation = 0.dp,
    tonalElevation = 0.dp,
)
```

Change default corner radius from 10dp to 14dp (medium tier).

#### 2.2 BottomNavBar Overhaul

Remove heavy shadows and circular floating style. Replace with a clean iOS-style tab bar:

- Background: `IosWhite` (theme token, not `Color.White`)
- Top border: `IosSeparator` at 0.5dp (keep current)
- Tab items: simple icon + label columns, no circle backgrounds, no shadows
- Selected state: icon tinted `IosAccent`, unselected `IosSecondaryLabel`
- Remove all `.shadow()` modifiers
- Remove `.border()` on individual items
- Remove `CircleShape` clip on items

#### 2.3 RecordFab Simplification

- Reduce container from 120dp to 96dp
- Remove `Brush.radialGradient` background — use transparent or a very subtle
  `IosBackground.copy(alpha = 0.9f)` scrim if overlap readability is needed
- Keep inner FAB at 72dp circle
- Add subtle `shadowElevation = 6.dp` (the one place elevation is acceptable — floating actions)

#### 2.4 PrimaryButton Standardization

- Change radius from hardcoded `12.dp` to `VmDimens.RadiusMedium` (14dp)
- Height already correct at 50dp

**Files to modify:**
- `ui/components/GlassCard.kt`
- `ui/components/PrimaryButton.kt`
- `ui/components/RecordFab.kt`
- `ui/navigation/BottomNavBar.kt`

---

### Phase 3: Screen-by-Screen Polish

**Goal:** Apply consistent spacing grid and fix per-screen inconsistencies.

#### 3.1 Standardize Section Headers

Create a reusable composable or establish a pattern:

```kotlin
Text(
    text = sectionTitle.uppercase(),
    style = MaterialTheme.typography.bodySmall,
    color = IosSecondaryLabel,
    modifier = Modifier.padding(
        start = VmDimens.ScreenHorizontalPadding,
        top = VmDimens.SpaceSm,   // 8dp
        bottom = VmDimens.SpaceXs, // 4dp
    )
)
```

Apply to: HomeScreen ("Folders", "Recent Files"), SettingsScreen ("ACCOUNT", "NAVIGATION",
"INTEGRATIONS"), ChecklistScreen ("TO-DO", "DONE").

#### 3.2 HomeScreen

- Unify divider start offsets — use consistent `52.dp` for all list items with leading icons.
- Standardize recording row padding to `horizontal = 16.dp, vertical = 12.dp`.
- Replace Spacer(80.dp) with `VmDimens.FabClearance`.

#### 3.3 RecordingsScreen

- SummaryRow: change `6.dp` spacer to `VmDimens.SpaceSm` (8dp).
- Replace SummarizationPopup hardcoded colors with theme tokens.
- Replace CompletionToast `Color.White` with `IosWhite`.
- Standardize date header padding to match section header pattern.

#### 3.4 SettingsScreen

- Add consistent top padding to all section headers (currently NAVIGATION and INTEGRATIONS
  headers are missing `top = 8.dp`).
- Standardize section gap from `Spacer(24.dp)` to `VmDimens.SpaceXl`.
- Use `IosDestructive` on error snackbar consistently (already done here, add to SignInScreen).

#### 3.5 ChecklistScreen

- Remove redundant `cornerRadius = 10.dp` parameter on GlassCard calls (it is the default, and
  after Phase 2 the default becomes 14dp — all cards should use the new default).
- Standardize FAB positioning to match RecordFab pattern.

#### 3.6 SummariesScreen

- SummaryDetailSheet: change horizontal padding from 20dp to `VmDimens.SpaceXl` (24dp).
- Align with SummariesInfoSheet which already uses 24dp.

#### 3.7 SignInScreen

- GlassCard innerPadding: change from 20dp to `VmDimens.SpaceXl` (24dp).
- Add `containerColor = IosDestructive` to error Snackbar to match SettingsScreen.
- Ensure consistent button radius through PrimaryButton component (no changes needed if
  PrimaryButton is updated in Phase 2).

#### 3.8 RecordingBottomSheet

- Drag handle: increase radius from 2.5dp to 4dp for consistency.
- Standardize action button sizes to two tiers: 52dp for secondary actions (discard, pause)
  and 72dp for primary action (stop/save).
- Replace hardcoded padding values with Dimens tokens.

**Files to modify:**
- `ui/home/HomeScreen.kt`
- `ui/recording/RecordingsScreen.kt`
- `ui/settings/SettingsScreen.kt`
- `ui/checklist/ChecklistScreen.kt`
- `ui/summaries/SummariesScreen.kt`
- `ui/auth/SignInScreen.kt`
- `ui/recording/RecordingBottomSheet.kt`

---

### Phase 4: Motion & Micro-interactions

**Goal:** Add subtle polish that makes the app feel alive without being distracting.

#### 4.1 List Item Animations

Add `animateItem()` modifier to LazyColumn items for smooth insert/remove/reorder:

```kotlin
items(recordings, key = { it.id }) { recording ->
    RecordingRow(
        modifier = Modifier.animateItem(
            fadeInSpec = tween(200),
            fadeOutSpec = tween(200),
        ),
        ...
    )
}
```

Apply to: RecordingsScreen, SummariesScreen, HomeScreen, ChecklistScreen.

#### 4.2 Spring-Based Sheet Animations

Configure ModalBottomSheet animations to use spring curves:

```kotlin
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { true },
)
```

Material3 ModalBottomSheet already uses spring animation internally. Ensure no overrides are
fighting this.

#### 4.3 Haptic Feedback

Add `HapticFeedbackType.LongPress` on:
- Record button tap
- Recording stop/save
- Multi-select toggle
- Destructive action confirmation

```kotlin
val haptic = LocalHapticFeedback.current
// On action:
haptic.performHapticFeedback(HapticFeedbackType.LongPress)
```

#### 4.4 FAB Scale Animation

Add a subtle scale animation when the RecordFab is pressed:

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1f,
    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
)
```

**Files to modify:**
- All screen files with LazyColumn lists
- `ui/components/RecordFab.kt`
- `ui/recording/RecordingBottomSheet.kt`
- `ui/recording/RecordingsScreen.kt` (multi-select haptics)

---

### Phase 5: Dark Mode

**Goal:** Full dark mode support following iOS dark color conventions.

#### 5.1 Theme.kt Changes

```kotlin
@Composable
fun VoiceMindAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VmTypography,
        shapes = VmShapes,
        content = content,
    )
}
```

#### 5.2 Component Updates

- GlassCard: use `MaterialTheme.colorScheme.surface` instead of hardcoded `IosWhite`.
- BottomNavBar: use `MaterialTheme.colorScheme.surface` for background.
- SidebarDrawer: use `MaterialTheme.colorScheme.surface` for container.
- RecordFab: scrim color should adapt to `MaterialTheme.colorScheme.background`.
- All text colors should reference `MaterialTheme.colorScheme.onSurface` or
  `MaterialTheme.colorScheme.onSurfaceVariant` instead of `IosLabel` / `IosSecondaryLabel`
  directly, OR the iOS color tokens themselves should be provided via CompositionLocal so they
  switch between light and dark values.

#### 5.3 Widget Dark Mode

Update `WidgetColors.kt` to detect system theme and switch colors accordingly using
`GlanceTheme` or `isNightMode` from Glance context.

#### 5.4 Testing Checklist

- [ ] SignInScreen in dark mode
- [ ] HomeScreen in dark mode
- [ ] RecordingsScreen in dark mode
- [ ] RecordingBottomSheet in dark mode
- [ ] SettingsScreen in dark mode
- [ ] ChecklistScreen in dark mode
- [ ] SummariesScreen in dark mode
- [ ] Widget in dark mode
- [ ] All dialogs and dropdown menus in dark mode
- [ ] Snackbars and toasts in dark mode

**Files to modify:**
- `ui/theme/Color.kt`
- `ui/theme/Theme.kt`
- `ui/components/GlassCard.kt`
- `ui/navigation/BottomNavBar.kt`
- `ui/navigation/SidebarDrawer.kt`
- `widget/WidgetColors.kt`
- Every screen that references `Ios*` color tokens directly

---

## Summary: Priority Order

| Phase | Effort   | Impact   | Do When          |
|-------|----------|----------|------------------|
| 1     | Low      | High     | Immediately      |
| 2     | Medium   | High     | After Phase 1    |
| 3     | Medium   | Medium   | After Phase 2    |
| 4     | Low-Med  | Low-Med  | After Phase 3    |
| 5     | High     | High     | After Phase 3    |

Phases 4 and 5 are independent of each other and can be done in parallel.

The single highest-impact change is **Phase 2.2 (BottomNavBar overhaul)** — the heavy shadows
on the circular tab items are the most visible departure from minimalist design. Combined with
Phase 1 (token foundation), these two changes will transform the perceived quality of the app.
