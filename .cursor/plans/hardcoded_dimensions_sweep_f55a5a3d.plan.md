---
name: Hardcoded Dimensions Sweep
overview: Replace raw `dp` literals with `VmDimens` constants across 5 files, using the mapping reference from codereviewphases.md. Only values with exact semantic matches are replaced; intentional custom values (e.g., 20.dp list indent, 6.dp row padding, 10.dp corner radius) are left as-is.
todos:
  - id: checklist
    content: ChecklistScreen.kt -- 12 replacements (16.dp, 8.dp, 48.dp, 2.dp, 4.dp)
    status: completed
  - id: summaries
    content: SummariesScreen.kt -- 13 replacements (16.dp, 8.dp, 12.dp, 4.dp, 2.dp, 24.dp, 32.dp)
    status: completed
  - id: signin
    content: SignInScreen.kt -- 8 replacements (24.dp, 16.dp, 48.dp, 12.dp, 4.dp, 32.dp)
    status: completed
  - id: timezone
    content: TimeZonePickerDialog.kt -- 6 replacements + add VmDimens import (8.dp, 4.dp, 16.dp, 12.dp)
    status: completed
  - id: shareditems
    content: SharedItemsScreen.kt -- 3 replacements (16.dp, 12.dp)
    status: completed
  - id: lint-verify
    content: Run lints on all 5 files to verify zero errors
    status: completed
isProject: false
---

# Hardcoded Dimensions Sweep

Replace raw `dp` literals with semantically-correct `VmDimens` constants from [Dimens.kt](android/app/src/main/java/com/voicemind/ui/theme/Dimens.kt). All 5 files are UI composables with zero cross-file impact -- these are purely cosmetic, value-preserving substitutions.

## Mapping Reference (from VmDimens)

- `2.dp` -> `VmDimens.SpaceXxs`
- `4.dp` -> `VmDimens.SpaceXs`
- `8.dp` -> `VmDimens.SpaceSm`
- `12.dp` -> `VmDimens.SpaceMd`
- `16.dp` -> `VmDimens.SpaceLg` or `VmDimens.ScreenHorizontalPadding` (for screen-level horizontal padding)
- `24.dp` -> `VmDimens.SpaceXl`
- `32.dp` -> `VmDimens.SpaceXxl` or `VmDimens.IconLg` (for component sizes)
- `48.dp` -> `VmDimens.SpaceXxxl` or `VmDimens.TouchTarget` (for touch targets)

## Skipped Values (no exact VmDimens match or intentional custom sizing)

These raw values remain as-is because they have no semantic VmDimens equivalent or changing them would alter the UI:

- `0.dp` (zero padding), `3.dp` (step text), `6.dp` (row padding), `10.dp` (corner radius)
- `14.dp`, `20.dp` (list item padding in TimeZonePickerDialog/SharedItemsScreen)
- `50.dp` (Google button height), `64.dp` (logo icon), `80.dp` (top spacer on sign-in)
- Icon sizes like `12.dp` (small schedule/flag icons), `24.dp` (standard M3 icon size) -- no matching `VmDimens.Icon*` constant

---

## File 1: [ChecklistScreen.kt](android/app/src/main/java/com/voicemind/ui/checklist/ChecklistScreen.kt)

`VmDimens` already imported. 12 replacements:

- **L127** `.padding(horizontal = 16.dp)` -> `VmDimens.ScreenHorizontalPadding`
- **L141** `Modifier.padding(16.dp)` -> `VmDimens.SpaceLg`
- **L147** `.padding(vertical = 8.dp)` -> `VmDimens.SpaceSm`
- **L167** `Modifier.height(16.dp)` -> `VmDimens.SpaceLg`
- **L180** `Modifier.padding(16.dp)` -> `VmDimens.SpaceLg`
- **L186** `.padding(vertical = 8.dp)` -> `VmDimens.SpaceSm`
- **L287** `Modifier.size(48.dp)` -> `VmDimens.TouchTarget`
- **L295** `Modifier.size(48.dp)` -> `VmDimens.TouchTarget`
- **L364** `.padding(top = 2.dp)` -> `VmDimens.SpaceXxs`
- **L372** `Modifier.width(4.dp)` -> `VmDimens.SpaceXs`
- **L391** `.padding(top = 2.dp)` -> `VmDimens.SpaceXxs`
- **L399** `Modifier.width(4.dp)` -> `VmDimens.SpaceXs`

## File 2: [SummariesScreen.kt](android/app/src/main/java/com/voicemind/ui/summaries/SummariesScreen.kt)

`VmDimens` already imported. 13 replacements:

- **L80** `.padding(horizontal = 16.dp)` -> `VmDimens.ScreenHorizontalPadding`
- **L96** `Arrangement.spacedBy(8.dp)` -> `VmDimens.SpaceSm`
- **L97** `Modifier.height(4.dp)` -> `VmDimens.SpaceXs`
- **L227** `Modifier.height(12.dp)` -> `VmDimens.SpaceMd`
- **L235** `Modifier.height(16.dp)` -> `VmDimens.SpaceLg`
- **L241** `Modifier.height(4.dp)` -> `VmDimens.SpaceXs`
- **L247** `.padding(vertical = 2.dp)` -> `VmDimens.SpaceXxs`
- **L253** `Modifier.height(12.dp)` -> `VmDimens.SpaceMd`
- **L299** `.padding(horizontal = 24.dp)` -> `VmDimens.SpaceXl`
- **L300** `.padding(bottom = 32.dp)` -> `VmDimens.SpaceXxl`
- **L309** `Modifier.size(8.dp)` -> `VmDimens.SpaceSm` (Spacer)
- **L313** `Modifier.height(16.dp)` -> `VmDimens.SpaceLg`
- **L330** `Modifier.height(12.dp)` -> `VmDimens.SpaceMd`

## File 3: [SignInScreen.kt](android/app/src/main/java/com/voicemind/ui/auth/SignInScreen.kt)

`VmDimens` already imported. 8 replacements:

- **L67** `.padding(24.dp)` -> `VmDimens.SpaceXl`
- **L85** `Modifier.height(16.dp)` -> `VmDimens.SpaceLg`
- **L100** `Modifier.height(48.dp)` -> `VmDimens.SpaceXxxl`
- **L106** `Arrangement.spacedBy(12.dp)` -> `VmDimens.SpaceMd`
- **L152** `Modifier.height(4.dp)` -> `VmDimens.SpaceXs`
- **L184** `Arrangement.spacedBy(12.dp)` -> `VmDimens.SpaceMd`
- **L218** `Modifier.height(32.dp)` -> `VmDimens.SpaceXxl`
- **L222** `Modifier.size(32.dp)` -> `VmDimens.IconLg` (progress indicator)

## File 4: [TimeZonePickerDialog.kt](android/app/src/main/java/com/voicemind/ui/settings/TimeZonePickerDialog.kt)

Needs `import com.voicemind.ui.theme.VmDimens` added. 6 replacements:

- **L112** `.padding(horizontal = 8.dp)` -> `VmDimens.SpaceSm`
- **L115** `Modifier.height(4.dp)` -> `VmDimens.SpaceXs`
- **L132** `Modifier.width(16.dp)` -> `VmDimens.SpaceLg`
- **L166** `top = 16.dp` -> `VmDimens.SpaceLg`
- **L167** `bottom = 4.dp` -> `VmDimens.SpaceXs`
- **L177** `vertical = 12.dp` -> `VmDimens.SpaceMd`

Note: `20.dp` and `14.dp` are used consistently as list-item insets throughout this dialog. No VmDimens match exists; left as-is.

## File 5: [SharedItemsScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedItemsScreen.kt)

`VmDimens` already imported. 3 replacements:

- **L146** `horizontal = 16.dp` -> `VmDimens.ScreenHorizontalPadding`
- **L173** `horizontal = 16.dp` -> `VmDimens.ScreenHorizontalPadding`
- **L183** `Modifier.width(12.dp)` -> `VmDimens.SpaceMd`

Note: `14.dp` vertical padding and `20.dp` icon size have no VmDimens match; left as-is.

---

**Total: 42 replacements across 5 files. 1 import addition (TimeZonePickerDialog.kt). Zero behavioral changes.**