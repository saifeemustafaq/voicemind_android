---
name: Pill tabs shared items
overview: Replace the subsection headers (RECORDINGS, TASKS, SUMMARIES) in the Shared Items screen with a FilterChip pill-tab row at the top, and update the PRD and phases documents to reflect this new navigation pattern.
todos:
  - id: update-prd
    content: Update sharing.md Section 3.3 — replace subsection description with pill tab navigation pattern
    status: completed
  - id: update-phases
    content: Update sharingphases.md Phase 4 and Phase 9 — replace subsection references with pill tab references
    status: completed
  - id: update-viewmodel
    content: Update SharedItemsViewModel.kt — add SharedItemsTab enum, selectedTab state, selectTab function, and tasks placeholder
    status: completed
  - id: update-screen
    content: Update SharedItemsScreen.kt — replace SectionHeader pattern with FilterChip pill row, show content based on selected tab
    status: completed
isProject: false
---

# Pill Tabs for Shared Items Screen

## Context

The current `SharedItemsScreen` uses vertically stacked `SectionHeader` composables ("RECORDINGS", "SUMMARIES") to group shared items by type. The user wants to replace this with **3 pill-shaped tabs** at the top (Recordings, Tasks, Summaries), where tapping a pill shows only that content type.

The app already uses this exact `FilterChip`-in-a-`Row` pattern in `[RecordingDialogs.kt](android/app/src/main/java/com/voicemind/ui/recording/RecordingDialogs.kt)` (lines 92-110) for Transcript/Summary/Tasks tabs.

## Changes

### 1. Update PRD: `sharing.md` Section 3.3

Replace the "Populated State" subsection description from dynamically-shown subsections to pill tab navigation:

- Three pill tabs at the top: Recordings, Tasks, Summaries
- Tapping a pill shows only items of that type
- Each pill tab has its own empty state when no items of that type are shared
- Default selected tab is "Recordings"

### 2. Update phases doc: `sharingphases.md`

- **Phase 4** (Shared Items Folder UI): Replace references to "Section headers for RECORDINGS, TASKS, SUMMARIES" and "subsections" with pill tab language. Update `SharedItemsScreen` and `SharedItemsViewModel` checklist items to describe the pill tab pattern instead.
- **Phase 9** (Task Sharing): Update the "Tasks Subsection in Shared Items" checklist to reference the Tasks pill tab instead of a subsection.

### 3. Update `SharedItemsViewModel.kt`

File: `[SharedItemsViewModel.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedItemsViewModel.kt)`

- Add an `enum class SharedItemsTab { Recordings, Tasks, Summaries }` for the tab options
- Add `selectedTab: SharedItemsTab` to `SharedItemsUiState` (default: `Recordings`)
- Add a `selectTab(tab: SharedItemsTab)` function to update the selected tab
- Add placeholder `tasks` list to `SharedItemsUiState` (empty for now; will be populated in Phase 9)

### 4. Update `SharedItemsScreen.kt`

File: `[SharedItemsScreen.kt](android/app/src/main/java/com/voicemind/ui/sharing/SharedItemsScreen.kt)`

- Remove the private `SectionHeader` composable
- Add a `Row` of 3 `FilterChip` pills at the top of the content area (below the top app bar), matching the pattern from `RecordingDialogs.kt` lines 92-110:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
) {
    SharedItemsTab.entries.forEach { tab ->
        FilterChip(
            selected = state.selectedTab == tab,
            onClick = { viewModel.selectTab(tab) },
            label = { Text(tab.name, style = MaterialTheme.typography.labelMedium) },
        )
    }
}
```

- Replace the current `if (state.recordings.isNotEmpty()) { ... SectionHeader ... items() }` blocks with a single block that switches on `state.selectedTab` and shows only the relevant list
- Each tab shows its own per-tab empty state (e.g., "No shared recordings yet") when no items of that type exist
- Keep the overall empty state when nothing at all is shared (all three lists empty)
- The pill row is **always visible** when the overall empty state is not shown (so users can still switch tabs even if the current tab is empty)

