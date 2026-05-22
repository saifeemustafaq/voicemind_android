# VoiceMind AI — UI Patterns & Recipes

**Purpose:** Codified interaction and UI patterns used throughout the Android app. When building a new screen or feature, follow these recipes for consistency. Each entry shows **when to use**, a **canonical code pattern**, and **don'ts**.

**Use with:** `DeveloperGuide.md` (engineering practices), `Style_Guide.md` (design system), `Android_Developer_Brief.md` (architecture reference).

---

## 1. State Collection

Every screen collects ViewModel state the same way.

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

- Always use `collectAsStateWithLifecycle()` (not `collectAsState()`). It stops collection when the lifecycle drops below `STARTED`.
- One ViewModel owns the state. The screen reads it. No duplicate sources of truth.
- For screens that need state from a parent's ViewModel (e.g., `RecordingDetailScreen` accessing `RecordingsViewModel`), scope to the parent's back stack entry:

```kotlin
val playbackViewModel: RecordingsViewModel = hiltViewModel(
    navController.previousBackStackEntry!!
)
```

---

## 2. Dialog Management

### 2a. Boolean flag — simple confirm/cancel dialogs

Use when the dialog has no associated data payload.

```kotlin
var showCreateDialog by remember { mutableStateOf(false) }

// trigger
Button(onClick = { showCreateDialog = true }) { Text("Create") }

// render
if (showCreateDialog) {
    AlertDialog(
        onDismissRequest = { showCreateDialog = false },
        title = { Text("Create Folder") },
        confirmButton = {
            TextButton(onClick = {
                viewModel.create(name)
                showCreateDialog = false
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
        },
    )
}
```

**Used in:** bulk delete confirm, add task dialog, delete account confirm, storage clear confirm.

### 2b. Nullable model — dialogs that operate on a specific item

Use when the dialog needs to know *which* item it's acting on.

```kotlin
var showRenameDialog by remember { mutableStateOf<Folder?>(null) }

// trigger (from row action)
onRename = { showRenameDialog = folder }

// render
showRenameDialog?.let { folder ->
    FolderNameDialog(
        title = "Rename Folder",
        initialName = folder.name,
        onConfirm = { name ->
            viewModel.renameFolder(folder.id, name)
            showRenameDialog = null
        },
        onDismiss = { showRenameDialog = null },
    )
}
```

**Used in:** rename recording, move to folder, delete recording, rename folder, delete folder.

### 2c. State-driven — dialogs triggered by ViewModel state

Use when the dialog visibility is driven by business logic (e.g., error from an async operation, share target selection).

```kotlin
// ViewModel exposes nullable field in state
state.collectiveSummarizeError?.let { error ->
    AlertDialog(
        onDismissRequest = { viewModel.clearCollectiveSummarizeError() },
        title = { Text("Summarization Failed") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = { viewModel.clearCollectiveSummarizeError() }) { Text("OK") }
        },
    )
}
```

**Used in:** needs-internet dialog, share-with-user target, selected summary detail, collective summarize error.

### 2d. RecordingDialogsHost — centralized dialog orchestration

When a screen manages multiple recording-related dialogs (transcript, rename, move, delete), use `RecordingDialogsHost` rather than inlining all four:

```kotlin
RecordingDialogsHost(
    showTranscript = showTranscript,
    showRenameDialog = showRenameDialog,
    showMoveDialog = showMoveDialog,
    showDeleteConfirm = showDeleteConfirm,
    folders = state.folders,
    viewModel = viewModel,
    onDismissTranscript = { showTranscript = null },
    onDismissRename = { showRenameDialog = null },
    onDismissMove = { showMoveDialog = null },
    onDismissDelete = { showDeleteConfirm = null },
)
```

### Don'ts

- Don't use a single `showDialog` boolean for multiple dialog types. Each dialog gets its own state variable.
- Don't drive dialog visibility from `LaunchedEffect` — set the state directly in the click handler or ViewModel.

---

## 3. Bottom Sheets

Use `ModalBottomSheet` with `skipPartiallyExpanded = true` for content that should appear fully expanded.

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
) {
    // content
}
```

**Used in:** transcript/summary sheet, recording bottom sheet, share dialog, summary detail, summaries info.

### Don'ts

- Don't use `BottomSheetScaffold` for modal content. Use `ModalBottomSheet`.
- Don't forget `skipPartiallyExpanded = true` — partial expansion causes layout issues with tall content.

---

## 4. List Patterns

### 4a. Grouped LazyColumn with section headers

Use for lists grouped by a computed key (e.g., recordings grouped by date).

```kotlin
val grouped = remember(recordings, appTz) {
    recordings.groupBy { it.createdAt?.toDate()?.toDateSectionKey(appTz) ?: "Unknown" }
}

LazyColumn {
    grouped.forEach { (dateLabel, items) ->
        item(key = "header_$dateLabel") {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "group_$dateLabel") {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column {
                    items.forEachIndexed { index, item ->
                        ItemRow(item = item)
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                thickness = VmDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    item { Spacer(modifier = Modifier.height(VmDimens.FabClearance)) }
}
```

Key rules:
- Every `item` and `items` call must have a stable `key`.
- Use `remember(data)` for the grouping computation — don't recompute on every recomposition.
- End with `FabClearance` spacer if the screen has a FAB.

### 4b. Flat list with animation

Use for ungrouped lists where items can be added/removed.

```kotlin
LazyColumn {
    items(state.folders, key = { it.id }) { folder ->
        FolderRow(
            folder = folder,
            modifier = Modifier.animateItem(
                fadeInSpec = tween(200),
                fadeOutSpec = tween(200),
            ),
        )
    }
}
```

**Used in:** folders, summaries.

### 4c. Short lists with scroll (no LazyColumn)

Use when the list is short and guaranteed to stay small (e.g., checklist with to-do/done sections).

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
) {
    state.todoItems.forEach { item -> ActionItemRow(item = item) }
}
```

**Used in:** ChecklistScreen (to-do/done sections stay in a single scrollable column).

### Don'ts

- Don't use `LazyColumn` without `key` — it breaks animations and causes recomposition bugs.
- Don't nest `LazyColumn` inside another scrollable container.

---

## 5. Multi-Select Mode

### Entry
Long-press a row to enter multi-select:

```kotlin
Row(
    modifier = Modifier.combinedClickable(
        onClick = { if (isMultiSelectActive) onToggleSelect() else onTap() },
        onLongClick = { if (!isMultiSelectActive) onLongPress() },
    )
)
```

### Exit
`BackHandler` exits multi-select before navigating back:

```kotlin
BackHandler(enabled = state.isMultiSelectActive) {
    viewModel.exitMultiSelect()
}
```

### Top bar swap
Replace the normal top bar with a selection bar:

```kotlin
if (state.isMultiSelectActive) {
    MultiSelectTopBar(
        selectedCount = state.selectedIds.size,
        onClose = { viewModel.exitMultiSelect() },
        onSelectAll = { viewModel.toggleSelectAll() },
        onDelete = { showBulkDeleteConfirm = true },
    )
} else {
    VoiceMindTopAppBar(title = "Recordings", ...)
}
```

### Selection indicator
Circular checkbox replaces the play button in multi-select mode:

```kotlin
if (isMultiSelectActive) {
    Box(modifier = Modifier.size(48.dp).clickable { onToggleSelect() }, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = CircleShape,
                )
                .border(2.dp, if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
        }
    }
}
```

### Bulk action confirmation
Always confirm destructive bulk actions with count-aware copy:

```kotlin
AlertDialog(
    title = { Text("Delete ${selectedCount} recording${if (selectedCount != 1) "s" else ""}?") },
    text = { Text("This will permanently delete the selected recordings and their audio files.") },
    ...
)
```

**Used in:** RecordingsScreen, ChecklistScreen.

---

## 6. Animation Conventions

### 6a. AnimatedVisibility — element show/hide

```kotlin
AnimatedVisibility(
    visible = showBanner,
    enter = expandVertically(),
    exit = shrinkVertically(),
)
```

Common specs by context:
| Context | Enter | Exit |
|---------|-------|------|
| Offline banner | `expandVertically()` | `shrinkVertically()` |
| FAB | `fadeIn() + slideInVertically { it }` | `fadeOut() + slideOutVertically { it }` |
| Toast from top | `slideInVertically { -it } + fadeIn()` | `slideOutVertically { -it } + fadeOut()` |
| Modal island | `scaleIn(spring(MediumBouncy, High), 0.85f) + fadeIn(tween(150))` | `scaleOut(tween(180), 0.9f) + fadeOut(tween(180))` |

### 6b. Crossfade — tab content switching

Use when switching between content panels without a swipe gesture:

```kotlin
Crossfade(targetState = selectedTab, label = "tab_content") { tab ->
    when (tab) {
        Tab.Transcript -> TranscriptContent(...)
        Tab.Summary -> SummaryContent(...)
        Tab.Tasks -> TasksContent(...)
    }
}
```

**Used in:** RecordingDetailScreen, TranscriptSheet.

### 6c. Infinite transitions — pulsing / shimmer

Recording pulse (alpha oscillation):

```kotlin
val transition = rememberInfiniteTransition(label = "pulse")
val alpha by transition.animateFloat(
    initialValue = 0.5f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(800),
        repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
)
Text("Recording", modifier = Modifier.alpha(alpha))
```

Shimmer brush (text gradient sweep):

```kotlin
val offset by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(1800, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = "shimmerOffset",
)
val shimmerBrush = Brush.linearGradient(
    colors = listOf(ShimmerBlue, ShimmerGold, ShimmerPurple, ShimmerBlue),
    start = Offset(offset * 800f - 400f, 0f),
    end = Offset(offset * 800f + 400f, 0f),
)
Text("Generating Summary…", style = typography.bodyMedium.copy(brush = shimmerBrush))
```

### 6d. Item animations in lists

```kotlin
Modifier.animateItem(
    fadeInSpec = tween(200),
    fadeOutSpec = tween(200),
)
```

**Used in:** folder rows, summary rows.

### Don'ts

- Don't use `animate*AsState` for visibility toggling — use `AnimatedVisibility`.
- Don't hardcode animation durations without checking existing specs above.
- Don't add animations to every state change — reserve them for user-initiated transitions and feedback.

---

## 7. Blocking Overlays

### Scrim — dims background during modal floating UI

```kotlin
AnimatedVisibility(
    visible = showPopup,
    enter = fadeIn(tween(250)),
    exit = fadeOut(tween(200)),
    modifier = Modifier.fillMaxSize(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { } },
    )
}
```

The `pointerInput` with empty `detectTapGestures` blocks all interaction with content behind the scrim.

### Full-screen progress — destructive bulk operations

```kotlin
if (state.isBulkDeleting || state.isBulkMoving) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
```

**Used in:** bulk delete, bulk move (RecordingsScreen).

---

## 8. Feedback & Status

### 8a. Auto-dismissing toast

A custom in-app toast that auto-dismisses after a delay:

```kotlin
var showToast by remember { mutableStateOf(false) }

LaunchedEffect(showToast) {
    if (showToast) {
        delay(4000L)
        showToast = false
    }
}

AnimatedVisibility(
    visible = showToast,
    enter = slideInVertically { -it } + fadeIn(),
    exit = slideOutVertically { -it } + fadeOut(),
    modifier = Modifier.align(Alignment.TopCenter),
) {
    Card(onClick = { showToast = false; navController?.navigate(Routes.Summaries.route) }) {
        Row { Icon(...); Text("Summary ready. Tap to view") }
    }
}
```

### 8b. Processing status chip

Shows inline status for items being processed asynchronously:

```kotlin
val (chipText, containerColor, contentColor) = when {
    recording.processingFailed -> Triple(
        "Processing failed",
        colorScheme.errorContainer,
        colorScheme.onErrorContainer,
    )
    recording.syncStatus == SyncStatus.PENDING_UPLOAD -> Triple(
        "Waiting for upload",
        colorScheme.tertiaryContainer,
        colorScheme.onTertiaryContainer,
    )
    else -> Triple(
        "Processing...",
        colorScheme.secondaryContainer,
        colorScheme.onSecondaryContainer,
    )
}
Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
    Text(text = chipText, color = contentColor, style = typography.bodySmall)
}
```

### 8c. Snackbar for transient errors

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(state.error) {
    state.error?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.clearError()
    }
}

SnackbarHost(hostState = snackbarHostState)
```

No color overrides — M3 defaults (`inverseSurface`/`inverseOnSurface`) are correct.

---

## 9. Context Menus

Overflow menu on list rows:

```kotlin
var menuExpanded by remember { mutableStateOf(false) }

Box {
    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        // Conditional items first
        if (recording.transcription != null) {
            DropdownMenuItem(
                text = { Text("View Transcript") },
                onClick = { menuExpanded = false; onTranscript() },
                leadingIcon = { Icon(Icons.Default.Description, null) },
            )
        }
        // Standard items
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { menuExpanded = false; onRename() },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
        )
        // Destructive items last, styled with error color
        DropdownMenuItem(
            text = { Text("Delete", color = colorScheme.error) },
            onClick = { menuExpanded = false; onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = colorScheme.error) },
        )
    }
}
```

Rules:
- Conditional items (e.g., "View Transcript" only when transcription exists) go at the top.
- Destructive items (delete) go last with `error` color.
- Always set `menuExpanded = false` before calling the action callback.
- Hide the overflow menu entirely when in multi-select mode.

---

## 10. Screen Layout Structure

Every screen follows this pattern:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(title = "...", icon = Icons.Default.Mic, ...)

        Column(modifier = Modifier.weight(1f).padding(horizontal = VmDimens.ScreenHorizontalPadding)) {
            // content (LazyColumn, scrollable column, etc.)
            // ends with: Spacer(modifier = Modifier.height(VmDimens.FabClearance))
        }
    }

    // FAB overlay
    RecordFab(
        onStartRecording = { ... },
        modifier = Modifier.align(Alignment.BottomCenter),
    )

    // Floating overlays (toasts, popups, scrims)
}

// Dialogs (outside the Box — they're window-level)
RecordingDialogsHost(...)
```

Key points:
- Content column uses `Modifier.weight(1f)` to fill remaining space after the top bar.
- FAB and overlays are siblings inside the same `Box`, using `Modifier.align()`.
- Dialogs are composable calls outside the layout `Box` — they render at the window level.

---

## 11. Settings Section Pattern

Group related settings in labeled sections:

```kotlin
Text(
    text = "NAVIGATION",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = VmDimens.SpaceLg, bottom = VmDimens.SpaceXs),
)

GlassCard(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Use sidebar navigation", style = typography.bodyMedium)
            Text(
                "Swipe or tap menu to open drawer",
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = useSidebar, onCheckedChange = { viewModel.toggleNavMode() })
    }
}
```

Pattern: uppercase `bodySmall` label → `GlassCard` with `Row(title+subtitle, weight(1f), control)`.

---

## 12. Offline-Aware Actions

Gate online-only actions with a dialog instead of silently failing:

```kotlin
// ViewModel checks connectivity before starting the action
fun requestShareWithUser(recording: Recording) {
    if (!connectivityObserver.isCurrentlyOnline()) {
        _state.update { it.copy(needsInternetDialog = NeedsInternetReason.ShareWithUser) }
        return
    }
    _state.update { it.copy(shareWithUserTarget = recording) }
}

// Screen renders the dialog
state.needsInternetDialog?.let { reason ->
    AlertDialog(
        onDismissRequest = { viewModel.dismissNeedsInternetDialog() },
        title = { Text("Internet Required") },
        text = { Text("Sharing requires an internet connection.") },
        confirmButton = { TextButton(onClick = { viewModel.dismissNeedsInternetDialog() }) { Text("OK") } },
    )
}
```

Use a sealed class or enum for `NeedsInternetReason` when multiple actions need this pattern:

```kotlin
enum class NeedsInternetReason {
    GenerateSummary, GenerateTasks, CollectiveSummarize, StillProcessing, ShareWithUser
}
```

---

## 13. Commit-on-Blur Editing

For inline text editing (task title, notes), commit when the field loses focus instead of requiring a save button:

```kotlin
var localTitle by remember(title) { mutableStateOf(title) }

BasicTextField(
    value = localTitle,
    onValueChange = { localTitle = it },
    modifier = Modifier.onFocusChanged { focus ->
        if (!focus.isFocused && localTitle != title) {
            viewModel.renameItem(localTitle)
        }
    },
)
```

Key: `remember(title)` resets local state when the authoritative value changes (e.g., from a sync update).

**Used in:** TaskDetailScreen (title, notes).

---

## 14. Tab Chips (FilterChip tabs)

Use `FilterChip` instead of `TabRow` when tabs are few and content doesn't need swipe:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    tabs.forEachIndexed { index, tab ->
        FilterChip(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            label = { Text(tab.label) },
        )
    }
}

Crossfade(targetState = tabs[selectedTab]) { tab ->
    when (tab) { ... }
}
```

**Used in:** RecordingDetailScreen, TranscriptSheet, SharedItemsScreen.

---

## 15. Share Dialog Pattern

The `ShareDialog` is a reusable `ModalBottomSheet` that handles user lookup, sharing, and showing existing shares:

```kotlin
ShareDialog(
    itemId = recording.id,
    itemType = "recording",   // or "summary"
    onDismiss = { viewModel.clearShareTarget() },
)
```

It manages its own ViewModel (`ShareViewModel`) and handles: email input, find user, share, success feedback with auto-dismiss, "Shared with" revoke list.

Gate the dialog behind a connectivity check (pattern §12).

---

*Follow these patterns for consistency across the app. When a pattern repeats in new code, check here first. If you create a new pattern, add it to this guide.*
