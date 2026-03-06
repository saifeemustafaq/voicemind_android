---
name: Revamp UI Guide M3
overview: Completely rewrite the `uioverhaul v2 material.md` guide to replace the iOS-inspired design language with strict Material 3 Expressive guidelines, auditing the current codebase for accurate facts and establishing M3 Expressive as the universal design system for all current and future development.
todos:
  - id: read-all-ui-files
    content: Read all current UI component, screen, and navigation files to audit their exact current state for the guide's 'Current Implementation Audit' section
    status: completed
  - id: write-guide-sections-1-7
    content: "Write guide sections 1-7: M3 Expressive principles, theme setup, color system, typography, shape system, elevation, motion -- all with exact M3 specs, code samples, and token tables"
    status: completed
  - id: write-guide-section-8
    content: "Write guide section 8: Component standards -- mapping every custom component to its M3 replacement with code examples"
    status: completed
  - id: write-guide-section-9
    content: "Write guide section 9: Current implementation audit -- accurate file-by-file audit based on actual code read in step 1"
    status: completed
  - id: write-guide-sections-10-11
    content: "Write guide sections 10-11: Gap analysis and phased action plan for migrating from current state to full M3 Expressive compliance"
    status: completed
isProject: false
---

# Revamp UI Overhaul Guide to M3 Expressive

## Context

The current guide ([uioverhaul v2 material.md](uioverhaul v2 material.md)) is built around an iOS-inspired minimalist design language (iOS hex colors, Inter font, custom `GlassCard`, zero-elevation cards, superelliptical corners, custom 3-tier shape scale). The app has since evolved: it already uses `MaterialTheme`, `lightColorScheme`/`darkColorScheme`, a `Dimens.kt` token file, and dark mode support. The guide's "current implementation audit" section is stale.

The new guide will strictly follow [M3 Material Expressive](https://m3.material.io/) and establish it as the sole design system for every aspect of the app, now and in the future.

## Key Shifts from Current Guide

### 1. Theme Entry Point

- **Current**: `MaterialTheme(...)` with custom iOS color schemes
- **New**: `MaterialExpressiveTheme(...)` using `expressiveLightColorScheme()` / `darkColorScheme()`, `MotionScheme.expressive()`, and M3 `Shapes`
- File: [Theme.kt](android/app/src/main/java/com/voicemind/ui/theme/Theme.kt)

### 2. Color System

- **Current**: Hand-picked iOS hex values (`IosBackground #F2F2F7`, `IosAccent #007AFF`, `IosLabel #000000`, etc.) mapped to M3 `lightColorScheme` roles
- **New**: Use M3 dynamic color where supported (`dynamicLightColorScheme` / `dynamicDarkColorScheme`), fall back to `expressiveLightColorScheme()` with brand-customized seed color; reference colors exclusively via `MaterialTheme.colorScheme.`* roles (primary, onPrimary, surface, onSurface, etc.) instead of standalone `Ios`* constants
- M3 has 26+ color roles; the guide will map every UI element to the correct role
- File: [Color.kt](android/app/src/main/java/com/voicemind/ui/theme/Color.kt) -- will be drastically simplified

### 3. Typography

- **Current**: Inter font, custom sizes (28/22/18/15/15/14/12/11/10 sp), iOS-style naming
- **New**: M3 type scale (15 baseline + 15 emphasized styles, Display/Headline/Title/Body/Label x Large/Medium/Small). Default sizes: 57/45/36/32/28/24/22/16/14/16/14/12/14/12/11 sp. Brand typeface (Inter or other) replaces Roboto at the `brand` and `plain` token levels. Emphasized styles used for selected states, buttons, key interactions
- File: [Type.kt](android/app/src/main/java/com/voicemind/ui/theme/Type.kt) -- needs to populate all 15 slots

### 4. Shape System

- **Current**: Custom 3-tier scale (Small 10dp, Medium 14dp, Large 22dp) in `VmDimens`
- **New**: M3 shape scale with 8 tokens:
  - ExtraSmall: 4dp (text fields, menus, snackbars)
  - Small: 8dp (chips)
  - Medium: 12dp (cards, small FABs)
  - Large: 16dp (FABs, extended FABs, nav drawers)
  - LargeIncreased: ~20dp (Expressive)
  - ExtraLarge: 28dp (large FABs, bottom sheets)
  - ExtraLargeIncreased: ~32dp (Expressive)
  - ExtraExtraLarge: ~36dp (Expressive)
- Use `MaterialTheme.shapes.`* everywhere, no hardcoded `RoundedCornerShape(...)` calls
- File: [Dimens.kt](android/app/src/main/java/com/voicemind/ui/theme/Dimens.kt) -- remove custom radius tokens, keep spacing

### 5. Elevation

- **Current**: Zero-elevation philosophy, hairline borders for depth
- **New**: M3 tonal elevation system. Surface tint color provides depth without shadows. 5 elevation levels (0, 1, 3, 6, 8, 12 dp) with tonal overlay. Cards use `ElevatedCard` or `Card` with default M3 elevation. FABs use standard M3 FAB elevation.

### 6. Motion

- **Current**: Custom spring animations, 200-350ms micro-interactions
- **New**: `MotionScheme.expressive()` (springier, bouncier). Use `MaterialTheme.motionScheme` for all animations. Spatial, effects, fast, and slow motion specs from the scheme. Shape morphing for interactive components.

### 7. Components -- Replace Custom with M3 Standard

- **GlassCard** -> M3 `Card` / `ElevatedCard` / `OutlinedCard`
- **PrimaryButton** -> M3 `Button` (filled), `FilledTonalButton`, `OutlinedButton`, `ElevatedButton`
- **RecordFab** -> M3 `FloatingActionButton` / `LargeFloatingActionButton`
- **BottomNavBar** -> M3 `NavigationBar` with `NavigationBarItem`
- **SidebarDrawer** -> M3 `ModalNavigationDrawer` with `NavigationDrawerItem`
- **TopAppBar** -> M3 `TopAppBar` / `MediumTopAppBar` / `LargeTopAppBar`
- **BottomSheet** -> M3 `ModalBottomSheet`
- **Dialogs** -> M3 `AlertDialog`
- **Text Fields** -> M3 `OutlinedTextField` / `TextField`

### 8. Icons

- **Current**: Lucide icons
- **New**: M3 recommends Material Symbols. Guide will establish Material Symbols (outlined) as the icon system, replacing Lucide. `androidx.compose.material.icons.extended` already in dependencies.

### 9. Spacing

- Keep the 4dp grid system from `VmDimens` (spacing tokens are design-system-agnostic) but align naming with M3 conventions where applicable.

## Guide Structure (New)

1. **M3 Expressive Design Principles** -- philosophy, research foundation, what "expressive" means
2. **Theme Setup** -- `MaterialExpressiveTheme`, dynamic color, color scheme, shapes, typography, motion
3. **Color System** -- M3 color roles, dynamic color, custom seed, dark mode
4. **Typography** -- full M3 type scale, brand/plain typeface, emphasized styles
5. **Shape System** -- 8-token shape scale, component mappings
6. **Elevation & Surface** -- tonal elevation, surface tint
7. **Motion** -- expressive motion scheme, animation specs
8. **Component Standards** -- how every component must use M3 APIs
9. **Current Implementation Audit** -- accurate audit of ALL current files
10. **Gap Analysis** -- what needs to change
11. **Action Plan** -- phased migration tasks

## Files That Will Be Referenced (Accurate Audit)

Theme layer:

- [Theme.kt](android/app/src/main/java/com/voicemind/ui/theme/Theme.kt) -- currently `MaterialTheme`, needs `MaterialExpressiveTheme`
- [Color.kt](android/app/src/main/java/com/voicemind/ui/theme/Color.kt) -- iOS color constants, needs M3 color role migration
- [Type.kt](android/app/src/main/java/com/voicemind/ui/theme/Type.kt) -- Inter font, partial M3 scale, needs all 15 styles
- [Dimens.kt](android/app/src/main/java/com/voicemind/ui/theme/Dimens.kt) -- custom spacing/radius, radius tokens to be removed

Components:

- `ui/components/GlassCard.kt` -> replace with M3 Card
- `ui/components/PrimaryButton.kt` -> replace with M3 Button
- `ui/components/RecordFab.kt` -> replace with M3 FloatingActionButton
- `ui/components/EmptyStateCard.kt` -> uses GlassCard, will follow
- `ui/components/VoiceMindTopAppBar.kt` -> replace with M3 TopAppBar
- `ui/components/InlinePlayerControls.kt` -> align colors/shapes with M3
- `ui/components/VoiceMindTextFieldColors.kt` -> use M3 TextField defaults

Navigation:

- `ui/navigation/BottomNavBar.kt` -> M3 NavigationBar
- `ui/navigation/SidebarDrawer.kt` -> M3 ModalNavigationDrawer
- `ui/navigation/AppNavHost.kt` -- Scaffold colors

Screens (all need color/shape/typography token migration):

- `ui/home/HomeScreen.kt`
- `ui/recording/RecordingsScreen.kt`
- `ui/recording/RecordingBottomSheet.kt`
- `ui/recording/RecordingDialogs.kt`
- `ui/folders/FoldersScreen.kt`
- `ui/folders/FolderDetailScreen.kt`
- `ui/settings/SettingsScreen.kt`
- `ui/checklist/ChecklistScreen.kt`
- `ui/checklist/TaskDetailScreen.kt`
- `ui/summaries/SummariesScreen.kt`
- `ui/auth/SignInScreen.kt`

Widget:

- `widget/WidgetColors.kt` -- align with M3 via GlanceTheme

Build:

- [build.gradle.kts](android/app/build.gradle.kts) -- may need to update material3 dependency version for Expressive APIs

