---
name: Phase 2 Satellite Screens
overview: Audit and fix three satellite screens (SharedRecordingDetailScreen, TaskDetailScreen, FoldersScreen) against DeveloperGuide, Patterns_Guide, and Android_Developer_Brief coding standards. Extract composables, replace hardcoded dp with VmDimens, and create shared NavigationRow.
todos:
  - id: phase-2a-fix-bang
    content: "Phase 2A: Fix !! on line 196 of SharedRecordingDetailScreen.kt by capturing state.recording as local val before when block"
    status: done
  - id: phase-2a-extract
    content: "Phase 2A: Extract SharedRecordingOverflowMenu and SharedRecordingContentTabs as private composables"
    status: done
  - id: phase-2a-vmdimens
    content: "Phase 2A: Replace hardcoded dp with VmDimens where constants exist (8.dp -> SpaceSm, etc.)"
    status: done
  - id: phase-2b-extract
    content: "Phase 2B: Extract DateDeadlineCard + PickerMode to ui/checklist/DateDeadlineCard.kt"
    status: done
  - id: phase-2b-vmdimens
    content: "Phase 2B: Add VmDimens import and replace hardcoded dp values in TaskDetailScreen and DateDeadlineCard"
    status: done
  - id: phase-2c-navrow
    content: "Phase 2C: Create NavigationRow in ui/components/NavigationRow.kt"
    status: done
  - id: phase-2c-refactor
    content: "Phase 2C: Refactor FolderRow, SharedByMeRow, SharedItemsRow to use NavigationRow"
    status: done
  - id: phase-2c-textfield
    content: "Phase 2C: Add voiceMindTextFieldColors() to FolderNameDialog and replace hardcoded dp with VmDimens"
    status: done
  - id: update-codephase
    content: Update codephase.md to mark Phase 2 items as complete
    status: done
isProject: false
---

# Phase 2: Satellite Screens — Audit & Remediation

## Phase 2A — SharedRecordingDetailScreen.kt (514 LOC)

**File:** [SharedRecordingDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedRecordingDetailScreen.kt)

### Violations Found

1. **Main composable ~397 lines** (lines 74-470) — exceeds 200-line guideline (DeveloperGuide S4)
2. `**!!` on line 196** — `val recording = state.recording!!` (DeveloperGuide S2)
3. **Hardcoded dp values** — `80.dp` (waveform), `28.dp` (skip icons x2), `8.dp` (chips), `18.dp` + `2.dp` (progress indicator) — inconsistent with VmDimens used elsewhere in same file (Patterns_Guide S10)

### Fix Plan

- **Fix `!!`:** Capture `state.recording` as a local `val recording` before the `when` block so Kotlin smart-casts to non-null in the `else` branch. No `!!` needed.

```kotlin
val recording = state.recording
when {
    state.isLoading && recording == null -> { ... }
    recording == null -> { ... }
    else -> {
        // recording is smart-cast to non-null here
```

- **Extract private `SharedRecordingOverflowMenu`:** Pull lines 140-167 (Box + IconButton + DropdownMenu) into a private composable taking `menuExpanded`, `onMenuToggle`, `isDuplicating`, `onDuplicate`.
- **Extract private `SharedRecordingContentTabs`:** Pull lines 328-451 (FilterChip tabs + Crossfade with transcript/summary/tasks) into a private composable taking `recording`, `state`, `selectedTab`, `onTabSelected`, `clipboardManager`, `viewModel`.
- **Replace hardcoded dp with VmDimens** where constants exist:
  - `8.dp` (line 330 chips spacer) -> `VmDimens.SpaceSm`
  - `18.dp` / `2.dp` (lines 440-441 progress indicator) -> keep as-is (component-specific, no VmDimens match)
  - `80.dp` (waveform height) -> keep as-is (component-specific)
  - `28.dp` (skip icon size) -> keep as-is (no VmDimens match)
- **Verify MoveToFolderDialog** (lines 459-469): Uses `title = "Duplicate to Folder"` with default `showFolderIcon = false` — works correctly after Phase 1A changes.
- **Verify SnackbarHost** (lines 85, 113-114): Already matches Patterns_Guide S8c.

### Post-fix target

`SharedRecordingDetailScreen.kt` main composable should be ~250 lines.

---

## Phase 2B — TaskDetailScreen.kt (618 LOC)

**File:** [TaskDetailScreen.kt](android/app/src/main/java/com/voicemind/ui/checklist/TaskDetailScreen.kt)

### Violations Found

1. `**DateDeadlineCard` is ~250 lines** (lines 369-618) with nested picker state management — heavy for an inline private composable (DeveloperGuide S4, S8)
2. **Zero VmDimens usage** — every dp value is hardcoded (Patterns_Guide S10)
3. **ShareDialog lacks connectivity guard** — shown directly from menu without checking `connectivityObserver.isCurrentlyOnline()` (Patterns_Guide S15)
4. **No delete confirmation dialog** — Delete button calls `viewModel.deleteItem()` directly without confirmation (Patterns_Guide S5 recommends confirmation for destructive actions)

### Fix Plan

- **Extract `DateDeadlineCard` to separate file:** Move `DateDeadlineCard` (lines 369-618) + `PickerMode` enum (line 366) to [ui/checklist/DateDeadlineCard.kt](android/app/src/main/java/com/voicemind/ui/checklist/DateDeadlineCard.kt). Change from `private` to `internal`.
- **Replace hardcoded dp with VmDimens** throughout both files:


| Hardcoded               | VmDimens Replacement                                  | Locations                                                                       |
| ----------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------------- |
| `8.dp` spacers          | `VmDimens.SpaceSm`                                    | lines 153, 277, 417                                                             |
| `12.dp` spacers         | `VmDimens.SpaceMd`                                    | lines 161, 201, 260, 319, 400, 460                                              |
| `16.dp` spacers/padding | `VmDimens.SpaceLg`                                    | lines 169, 179, 192, 210, 219, 308, 391, 443, 451, 504, 508, 515, 526, 530, 537 |
| `20.dp` screen padding  | `VmDimens.ScreenHorizontalPadding + VmDimens.SpaceXs` | line 150 — keep as `20.dp` since no exact constant                              |
| `24.dp` spacers         | `VmDimens.SpaceXl`                                    | lines 199, 241, 253, 320, 369                                                   |
| `32.dp` spacers         | `VmDimens.SpaceXxl`                                   | lines 131, 227, 431, 491                                                        |
| `22.dp` icon sizes      | `VmDimens.IconMd`                                     | lines 316, 398, 458                                                             |
| `16.dp` icon sizes      | `VmDimens.IconSm`                                     | lines 435, 437, 495, 497                                                        |
| `4.dp` spacers          | `VmDimens.SpaceXs`                                    | lines 130, 268, 326, 427                                                        |
| `2.dp` padding          | `VmDimens.SpaceXxs`                                   | lines 317, 446                                                                  |
| `48.dp` touch targets   | `VmDimens.TouchTarget`                                | lines 295, 345, 389                                                             |
| `0.dp` innerPadding     | Keep as-is                                            | lines 186, 303, 382                                                             |
| `14.dp` row padding     | Keep as-is (no constant)                              | multiple                                                                        |
| `10.dp` padding         | Keep as-is (no constant)                              | lines 337, 508, 530                                                             |
| `6.dp` padding          | Keep as-is (no constant)                              | lines 517, 539                                                                  |


- **Verify overflow menu** (lines 103-121): Follows Patterns_Guide S9 — uses `extraSmall` shape, "Share with User" item with icon. Only one non-destructive item, no ordering concern.
- **Note: ShareDialog connectivity guard** — The plan flags this per Patterns_Guide S15 but `TaskDetailViewModel` does not expose a connectivity guard for sharing. This is a feature gap, not a code-standards fix. Flag for future.

---

## Phase 2C — FoldersScreen.kt (491 LOC)

**File:** [FoldersScreen.kt](android/app/src/main/java/com/voicemind/ui/folders/FoldersScreen.kt)

### Violations Found

1. `**FolderRow`, `SharedByMeRow`, `SharedItemsRow` repeat the same `GlassCard + Row + Icon + Label` boilerplate** (DeveloperGuide S1 DRY, S4 Reuse)
2. **Hardcoded dp values** — screen padding `16.dp`, row padding `16.dp`/`14.dp`, icon sizes `24.dp`, spacers `8.dp`/`4.dp`/`12.dp`, touch targets `48.dp` — VmDimens barely used (Patterns_Guide S10)
3. `**FolderNameDialog` missing `voiceMindTextFieldColors()`** on `OutlinedTextField` (line 473) — inconsistent with codebase standard

### Fix Plan

- **Create shared `NavigationRow` composable:** Extract to [ui/components/NavigationRow.kt](android/app/src/main/java/com/voicemind/ui/components/NavigationRow.kt)

```kotlin
@Composable
internal fun NavigationRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    GlassCard(modifier = modifier.fillMaxWidth(), innerPadding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VmDimens.SpaceLg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(VmDimens.SpaceXl))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = VmDimens.SpaceMd))
            trailingContent()
        }
    }
}
```

- **Refactor row composables:** Replace `FolderRow`, `SharedByMeRow`, `SharedItemsRow` to use `NavigationRow`, passing only the differing trailing content (count/menu/chevron/badge/overview button) via the `trailingContent` slot. Each row drops from ~70 lines to ~30 lines.
- **Add `voiceMindTextFieldColors()`** to `FolderNameDialog`'s `OutlinedTextField` on line 473:

```kotlin
OutlinedTextField(
    ...
    colors = voiceMindTextFieldColors(),
)
```

- **Replace hardcoded dp with VmDimens:**
  - `16.dp` screen padding (line 108) -> `VmDimens.ScreenHorizontalPadding`
  - `8.dp` spacedBy (line 115) -> `VmDimens.SpaceSm`
  - `4.dp` spacer (line 130) -> `VmDimens.SpaceXs`
  - `24.dp` icon sizes -> `VmDimens.SpaceXl` (handled by NavigationRow)
  - `12.dp` padding -> `VmDimens.SpaceMd` (handled by NavigationRow)
  - `48.dp` touch targets -> `VmDimens.TouchTarget`
  - `20.dp` overview icon -> keep as-is (between constants)
  - `8.dp` dialog spacing -> `VmDimens.SpaceSm`
  - `4.dp` dialog padding -> `VmDimens.SpaceXs`
  - `2.dp` stat row padding -> `VmDimens.SpaceXxs`
- **Verify overflow menu** in `FolderRow` (lines 277-292): Follows Patterns_Guide S9 correctly — Rename first, Delete last with error color.

---

## New Files Created


| File                                           | Type                 |
| ---------------------------------------------- | -------------------- |
| `android/.../ui/components/NavigationRow.kt`   | Shared composable    |
| `android/.../ui/checklist/DateDeadlineCard.kt` | Extracted composable |


## Modified Files


| File                             | Change Summary                                                |
| -------------------------------- | ------------------------------------------------------------- |
| `SharedRecordingDetailScreen.kt` | Fix `!!`, extract sub-composables, fix VmDimens               |
| `TaskDetailScreen.kt`            | Replace hardcoded dp with VmDimens, extract DateDeadlineCard  |
| `FoldersScreen.kt`               | Use NavigationRow, fix VmDimens, add voiceMindTextFieldColors |


## Codephase.md Checklist Updates

After execution, mark the following in `codephase.md`:

- Phase 2A items: all checked
- Phase 2B items: all checked (note ShareDialog connectivity guard as future work)
- Phase 2C items: all checked

