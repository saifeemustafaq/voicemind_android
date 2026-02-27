# VoiceMind AI — Style Guide (Kotlin / Jetpack Compose)

This guide translates the VoiceMind AI design system (see project root `Style_Guide.md`) into Kotlin and Jetpack Compose. Use it for the Android app so the experience matches the web app: **glassomorphism-first**, calm, trustworthy, mobile-first.

---

## 1. Design philosophy

- **Calm & focused** -- Minimal UI; recording and review without distraction.
- **Trustworthy & private** -- Soft colors; clear recording and processing states.
- **Modern & premium** -- Frosted glass surfaces, depth, subtle motion.
- **Inclusive** -- Readable contrast, 48dp minimum touch targets (Android guideline), support for TalkBack and font scaling.

**Glassomorphism** is the primary treatment: translucent surfaces, blur, soft gradients, rounded corners. Use `Modifier.blur()`, `Modifier.alpha()`, and custom `Brush` gradients to achieve the glass effect in Compose.

---

## 2. Color palette

Define these in your theme as `Color` values. Use the same hex values as the web app.

| Name | Hex | Compose use |
|------|-----|-------------|
| **Soft Periwinkle Mist** | `#E5E9FF` | Background tint, empty states, light glass base |
| **Light Lavender** | `#DCBFFE` | List rows, cards, secondary glass |
| **Pastel Violet** | `#E1C2FE` | Accent glass, highlights, selected |
| **Blush Pink** | `#EEA5C4` | Recording state, primary CTA, alerts |
| **Cool Sky Blue** | `#A9D8FF` | Links, secondary actions, info |
| **Deep Violet** | `#917BE5` | Primary buttons, focus, hierarchy |

**Text colors:**
- Primary: `#1A1825` or `#2D2A3A`.
- Secondary: same with ~0.7 alpha.
- Links / interactive: Cool Sky Blue or Deep Violet.
- "From recording" link: `#1E5A9E` (darker blue).

**Accessibility:** 4.5:1 contrast for body text, 3:1 for large text and controls. Test with TalkBack and font scaling.

### Example: Color definitions

```kotlin
package com.voicemind.ui.theme

import androidx.compose.ui.graphics.Color

val VmSoftPeriwinkleMist = Color(0xFFE5E9FF)
val VmLightLavender = Color(0xFFDCBFFE)
val VmPastelViolet = Color(0xFFE1C2FE)
val VmBlushPink = Color(0xFFEEA5C4)
val VmCoolSkyBlue = Color(0xFFA9D8FF)
val VmDeepViolet = Color(0xFF917BE5)
val VmTextPrimary = Color(0xFF1A1825)
val VmTextSecondary = Color(0xFF2D2A3A).copy(alpha = 0.7f)
val VmLinkBlue = Color(0xFF1E5A9E)
```

Wire these into your `MaterialTheme` color scheme. Use `VmDeepViolet` as `primary`, `VmBlushPink` as a custom accent/recording color, etc.

---

## 3. Glass and materials

Compose does not have a built-in `Material` glass like iOS. Achieve glassomorphism with:

- **Blur:** `Modifier.blur(radiusX = 12.dp, radiusY = 12.dp)` (API 31+; on lower APIs, use a semi-opaque overlay). For broad support, prefer the semi-opaque approach as the default and layer blur on top where available.
- **Translucency:** Overlay palette colors at **0.15--0.45** alpha on a light background. Example: `VmSoftPeriwinkleMist.copy(alpha = 0.35f)`.
- **Border:** Subtle edge: `BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))` or `VmLightLavender.copy(alpha = 0.4f)`.
- **Corners:** 12--20dp for cards and buttons; 20--24dp for sheets/modals. Use `RoundedCornerShape(...)`.
- **Shadow:** `Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), ambientColor = VmDeepViolet.copy(alpha = 0.08f))`.

**Stacked panels:** When stacking two glass panels vertically (e.g. To-do and Done on Checklist), use **no vertical spacing** so the app background does not show between them. Use `Column` with no `Arrangement.spacedBy()` or `Spacer` between the panels; panels should touch.

### Example: GlassCard composable

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = VmLightLavender.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shadowElevation = 4.dp,
    ) {
        content()
    }
}
```

---

## 4. Typography

- **Font:** Use the system default (Roboto) or a friendly sans-serif. No emoji in UI strings; use Material Icons for status and actions.
- **Hierarchy (Material 3 type scale):**
  - Large title / H1: `headlineLarge` or `titleLarge` bold.
  - Section headers: `titleMedium` or `titleSmall` semibold (e.g. "To-do", "Done").
  - Body: `bodyLarge` or `bodyMedium`.
  - Secondary / metadata: `bodySmall` or `labelMedium` with secondary color.
- **Colors:** Primary text `VmTextPrimary`; secondary `VmTextSecondary`. Links: `VmCoolSkyBlue` or `VmDeepViolet`. "From recording" links: `VmLinkBlue`.
- **Recording / timers:** Monospaced or tabular font for elapsed time; semibold for "Recording" label.

Support **font scaling** -- use `sp` units and test at large text sizes.

---

## 5. Spacing and layout

- **Grid:** 4dp or 8dp base. Use 8, 16, 24, 32dp for padding and spacing.
- **Touch targets:** Minimum **48dp** height and width for tappable controls (Android Material guideline). Record, Stop, folder rows, checklist checkbox, list actions must all meet this.
- **Screen margins:** 16--24dp horizontal; 16--24dp vertical between sections.
- **Sheets / modals:** 20--24dp padding inside; drag handle (small rounded pill, gray or Soft Periwinkle) at top.
- **Phone-first:** Design for phone first; tablet can reuse the same layout or add side-by-side panes.

---

## 6. Components (Compose)

### Record button (FAB)
- Size: 72--80dp circle.
- Background: Blush Pink gradient (optional blend with Pastel Violet); soft shadow.
- Icon: Material Icon `Mic` in white.
- Recording state: Same style with a subtle pulse or glow (e.g. repeated `animateFloatAsState` on alpha or scale). No emoji.

### Buttons
- **Primary:** Deep Violet background, white text, 12--16dp corner radius. Min height 48dp; padding 12--16dp vertical, 20--24dp horizontal.
- **Secondary:** Cool Sky Blue or Light Lavender glass (low alpha), Deep Violet or dark text.
- **Destructive:** Red with glass treatment; use sparingly (e.g. Delete confirmation).

### Cards (folders, checklist sections, list containers)
- Background: Light Lavender or Pastel Violet at ~0.25--0.35 alpha; 12--16dp corner radius; 1dp light border; soft shadow.
- Padding: 16--20dp inside.

### Bottom sheets (recording UI, modals)
- Background: Pastel Violet / Light Lavender glass, 20--24dp corner radius (top), stronger alpha.
- Drag handle: small pill (e.g. 12x4dp) in gray or Soft Periwinkle.
- Title and primary actions in Deep Violet or Blush Pink.

### List rows (recordings, checklist items, folders)
- Row background: Light Lavender at ~0.2--0.25 alpha; 12dp corner radius if needed; subtle divider (Light Lavender at 0.2).
- Selected/highlighted: Pastel Violet tint.
- Min height 48dp.

### Text fields (title, folder name)
- Background: Soft Periwinkle Mist or Light Lavender at ~0.2--0.3 alpha, 12dp corner radius.
- Border: 1dp Light Lavender or Pastel Violet at ~0.4. Focused: Pastel Violet or Cool Sky Blue accent.
- Use `OutlinedTextField` or `TextField` with custom colors from palette.

### Chips / tags
- Pill shape: Light Lavender or Cool Sky Blue at ~0.3 alpha, full corner radius (pill). Selected: Pastel Violet or Deep Violet, slightly higher alpha.

---

## 7. Icons

- **No emoji** in UI or copy. Use **Material Icons** (filled or outlined, pick one style and stay consistent).
- **Key icons:** `Mic` (record), `PlayArrow` / `Pause` (playback), `Stop` (stop recording), `Delete` (trash), `Folder` (folder), `CheckCircle` / `CheckCircleOutline` (checklist), `MoreVert` (overflow menu), `Edit` (rename), `Share` (share), `ContentCopy` (copy), `List` (checklist nav).
- **Colors:** Deep Violet or VmTextPrimary for primary; Cool Sky Blue for secondary; Blush Pink for record and alerts.
- **Recording:** Mic icon; optional subtle pulse when recording.

---

## 8. Motion and feedback

- **Transitions:** 200--300ms ease-out for sheet present/dismiss, modal appear, list item changes. Use `AnimatedVisibility`, `animateContentSize`, Compose animation APIs.
- **Recording:** Subtle pulse or glow (Blush Pink) when recording; avoid distracting animation.
- **Loading:** Prefer `CircularProgressIndicator` (themed to Deep Violet or Blush Pink) or skeleton/shimmer (Soft Periwinkle / Light Lavender).
- **Success:** Brief highlight or checkmark; keep minimal.

---

## 9. States

- **Default:** Glass and colors as above.
- **Pressed / ripple:** Use Material ripple; default is fine. Optional slight scale (0.98) on press for FAB.
- **Disabled:** Alpha ~0.5; no emphasis.
- **Error:** Red tint with glass; avoid flat red blocks. Snackbar with error message.
- **Recording:** Blush Pink emphasis and optional pulse.

---

## 10. Code conventions

- **Naming:** Use clear, consistent names. Composables: PascalCase nouns (e.g. `RecordingRow`, `ChecklistSection`). State: camelCase with `by remember` or ViewModel `StateFlow`.
- **No emoji** in source code, strings, or comments. Use Material Icon names in comments if needed.
- **Reuse:** Extract repeated glass backgrounds and button styles into shared composables (e.g. `GlassCard`, `PrimaryButton`, `GlassSurface`) to keep consistency and DRY.
- **Accessibility:** Add `contentDescription` for icons and buttons; support TalkBack and font scaling.
- **Theme:** Wire palette colors into `MaterialTheme.colorScheme`; reference via `MaterialTheme.colorScheme.primary`, etc., or use named `Vm*` colors directly for custom treatments outside the standard Material slots.

---

## 11. Quick reference (Compose)

| Element | Treatment |
|---------|-----------|
| App background | Soft Periwinkle Mist tint over light gray/white |
| Cards, list rows | Light Lavender / Pastel Violet glass, optional blur, 12--16dp radius |
| Sheets / modals | Pastel Violet / Light Lavender glass, 20--24dp radius |
| Record button | Blush Pink glass, glow when recording; Material Icon Mic |
| Primary actions | Deep Violet, white text, 48dp min height |
| Secondary actions | Cool Sky Blue / Light Lavender glass |
| Links, "from recording" | VmLinkBlue (darker blue), tappable |
| Text fields | Soft Periwinkle / Light Lavender glass, Pastel Violet focus |
| Stacked sections | No vertical gap between panels |

---

*This style guide keeps the Android app aligned with the VoiceMind AI web app and product vision: frictionless capture, workflow-friendly outputs, calm and trustworthy glass-first UI.*
