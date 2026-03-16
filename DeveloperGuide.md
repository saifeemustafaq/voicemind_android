# VoiceMind AI -- Android Kotlin/Compose Developer Guidelines

**Use this with:** [Android_Developer_Brief.md](Android_Developer_Brief.md) (what to build, Firebase architecture, data models, flows) and [Style_Guide_Compose.md](Style_Guide_Compose.md) (colors, components, layout, Lucide icons, no emoji). This document covers language, architecture, concurrency, Firebase usage, and engineering practices.

---

## 1) Core Principles

- Write idiomatic Kotlin. Prefer clarity over cleverness.
- Small diffs, always compiling. Every change must build without errors.
- Keep components single-responsibility (Composables render, ViewModels orchestrate, repositories do data work).
- Prefer composition over inheritance.
- Make code testable by default (interfaces + dependency injection).
- **DRY (Don't Repeat Yourself):** Never duplicate logic, layouts, or data transformations. If you write the same (or nearly the same) code twice, extract it into a shared function, composable, or utility. Before writing new code, search the codebase for existing implementations that solve the same problem.
- **Reuse first, create second:** Always check `ui/components/`, repositories, and utility packages before building something new. Extend or parameterize an existing component rather than creating a near-copy.
- **Keep it concise:** Leverage Kotlin's expressive features (scope functions, extension functions, default parameters, destructuring) to reduce boilerplate. Fewer lines of clear code is better than many lines of verbose code.
- **Product alignment:** Feature scope, data models, and Firebase architecture come from **Android_Developer_Brief.md**. Do not invent collections, fields, or flows; use only what the brief defines.
- **UI alignment:** Follow **Style_Guide_Compose.md**: 48dp minimum touch targets, no emoji in UI or code (use Lucide icons), M3 surface-based visuals with `MaterialTheme.colorScheme.*` and `MaterialTheme.shapes.*` throughout.

---

## 2) Kotlin Language Practices

### Style and correctness
- Use `val` by default; use `var` only when needed.
- Use `data class` for models and DTOs.
- Avoid `!!` (not-null assertion). Only allowed if proven impossible to be null and documented.
- Prefer `?.let {}`, `?: return`, or `requireNotNull()` with a clear message.
- Use `sealed class` / `sealed interface` for finite state (e.g. `RecordingState`, `UiState`).
- Prefer `when` over `if/else` chains for exhaustive state handling.

### Write concise, expressive Kotlin
- Use scope functions (`let`, `run`, `apply`, `also`, `with`) to reduce temporary variables and flatten logic.
- Use extension functions to add behavior to existing types instead of writing standalone utility functions with the type as the first parameter.
- Use default parameter values instead of overloaded functions.
- Use destructuring declarations for data classes and pairs: `val (title, duration) = recording`.
- Use `mapNotNull`, `filterIsInstance`, `groupBy`, and other collection operators instead of manual loops with mutable accumulators.
- Use single-expression functions (`fun foo() = ...`) when the body is a single return.
- Avoid writing wrapper functions that add no logic — call the underlying API directly.

### Nullability
- Prefer explicit null handling. Don't swallow nulls silently if the failure matters to the user (e.g. missing transcription should show "No transcript", not a blank screen).

### Errors
- Define typed errors or sealed results:

```kotlin
sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}
```

- Convert low-level errors (Firebase, network, OpenAI) into user-safe messages at the ViewModel boundary.

---

## 3) Coroutines and Concurrency

- Use Kotlin coroutines (`suspend`, `Flow`, `StateFlow`). Avoid callbacks unless bridging an Android API that requires them.
- ViewModels launch coroutines via `viewModelScope`. Use `Dispatchers.IO` for network, file, and Firebase calls.
- Never block the main thread: no heavy parsing, file I/O, uploads, or API calls on `Dispatchers.Main`.
- Use `StateFlow` or `MutableStateFlow` for UI state. Composables collect via `collectAsStateWithLifecycle()`.
- **Cancellation:** Ensure long tasks (recording, upload, transcription) can be cancelled. Cancel coroutines when the user navigates away or explicitly cancels. Don't leak coroutines.
- Prefer structured concurrency (`coroutineScope`, `async/await`) over `GlobalScope`.

---

## 4) Jetpack Compose Best Practices

### View composition
- Keep Composables small. If a Composable exceeds ~200 lines, extract sub-composables.
- Composables should be "mostly pure": derive UI from state. Side effects go in `LaunchedEffect`, `DisposableEffect`, or the ViewModel.
- Use `private` helper Composables for view fragments.

### Reuse and shared components
- **Check `ui/components/` first.** Before building any UI element (card, button, row, dialog, bottom sheet), check if a shared composable already exists. Use it, or extend it with parameters — don't fork a copy.
- **Parameterize, don't duplicate.** If two screens need a similar list row (e.g. recording row vs. folder row), build one generic composable with content slots or lambdas rather than two near-identical composables.
- **Extract when a pattern repeats.** The moment you copy-paste a composable or layout block, stop and extract it into `ui/components/` with clear parameter names.
- **Compose modifiers over wrappers.** Prefer adding `Modifier` parameters to existing composables over wrapping them in a new composable that only adds padding/styling.

### State management
- **`remember` / `mutableStateOf`:** Simple local UI state (e.g. text field value, dialog open).
- **ViewModel + `StateFlow`:** Screen-level state (list of recordings, loading, errors).
- **`collectAsStateWithLifecycle()`:** Collect ViewModel flows in Composables.
- Avoid duplicating sources of truth. One owner (ViewModel or parent Composable), others read.

### Navigation
- Use Jetpack Navigation Compose (`NavHost`, `composable()` destinations).
- Keep route definitions in a single `Routes` object or sealed class.
- Do not embed complex navigation logic inside Composables; keep in ViewModel/navigator if needed.

### Performance
- Avoid heavy work in `@Composable` functions.
- Use `key()` in `LazyColumn` items to help recomposition.
- Use `derivedStateOf` for expensive computations derived from state.

---

## 5) Architecture (Clean + Practical)

### MVVM boundaries
- **Composable (View):** Rendering + user events -> calls ViewModel.
- **ViewModel:** Holds `StateFlow<UiState>`, orchestrates use cases, maps errors to UI messages.
- **Repository:** Abstracts data source (Firestore, Cloud Storage, OpenAI API). No Compose imports.
- **Data source / service:** Firebase SDK calls, OpenAI HTTP calls, MediaRecorder wrapper.

### Dependency injection
- Use **Hilt** (recommended) or manual DI with an `AppContainer`.
- Inject repositories and services into ViewModels via constructor.
- No singletons unless it's Android-provided (e.g. `Application` context).

### Example layer structure

```
com.voicemind/
  data/
    model/          -- Recording, Folder, ActionItem data classes
    repository/     -- RecordingRepository, FolderRepository, ActionItemRepository
    remote/         -- OpenAiService (transcription, title, extraction)
  ui/
    theme/          -- Color.kt, Theme.kt, Type.kt
    home/           -- HomeScreen, HomeViewModel
    recording/      -- RecordingListScreen, RecordingRow, RecordingBottomSheet
    checklist/      -- ChecklistScreen, ActionItemRow, ChecklistViewModel
    folders/        -- FoldersScreen, FolderDetailScreen, FoldersViewModel
    settings/       -- SettingsScreen
    components/     -- GlassCard, PrimaryButton, shared composables
  audio/            -- AudioRecorderService (MediaRecorder wrapper)
  di/               -- Hilt modules
```

---

## 6) Firebase Usage

### Firestore
- Use the Firebase Android SDK (`com.google.firebase:firebase-firestore-ktx`).
- Scope all paths under `users/{uid}/`. Get UID from `FirebaseAuth.getInstance().currentUser?.uid`.
- Use **snapshot listeners** (`addSnapshotListener`) for real-time UI updates on recordings, folders, and action items.
- Use `Timestamp` (Firestore) for `createdAt` fields; convert to `Date` or formatted string in the ViewModel.
- Enable **offline persistence** (enabled by default). This gives the app basic offline read capability.

### Cloud Storage
- Use `com.google.firebase:firebase-storage-ktx`.
- Upload audio to `users/{uid}/audio/{recordingId}.m4a`.
- Get download URLs for playback via `storageRef.downloadUrl`.

### Authentication
- Use `com.google.firebase:firebase-auth-ktx`.
- Provide email/password sign-in and/or Google Sign-In.
- Listen for auth state changes: `FirebaseAuth.getInstance().addAuthStateListener { ... }`.
- On sign-out, clear local state and navigate to sign-in.

### Cloud Functions (optional, recommended)
- If using a Cloud Function for transcription/extraction: call via `FirebaseFunctions.getInstance().getHttpsCallable("functionName")`.
- Keep the OpenAI API key server-side in the Cloud Function.
- If calling OpenAI directly from the client (simpler for MVP): store the key securely (e.g. Firebase Remote Config, BuildConfig field not checked into source control).

---

## 7) Audio Recording

- Use `MediaRecorder` with output format `MPEG_4` and audio encoder `AAC` for `.m4a` output. This is well-supported on Android and accepted by OpenAI.
- Configure `MediaRecorder` carefully: set audio source (`MIC`), output format, encoder, output file, sample rate (44100 recommended), encoding bit rate (128000).
- Handle `MediaRecorder` lifecycle: `prepare()`, `start()`, `pause()`, `resume()`, `stop()`, `release()`. Release on stop/cancel.
- Handle audio interruptions (phone call, other app taking audio focus): pause recording or show a warning.
- Request `RECORD_AUDIO` permission at runtime (just-in-time, when user taps record). Handle denial gracefully with an explanation and Settings link.
- Store temp recording files in the app's cache directory; delete after successful upload.

---

## 8) File and Module Organization

- **One type per file** unless tightly coupled and small.
- **Group by feature** (e.g. `recording/`, `checklist/`, `folders/`) rather than only by type.
- Mark access control intentionally: `private` for helpers, `internal` by default, `public` only for API boundaries.
- Keep "shared" or "util" packages small and justified. A utility is valid only if used by 2+ distinct features.

### Refactoring for reuse
- **Promote on second use.** When a function, composable, or data mapping is needed by a second feature, move it out of the feature package into a shared location (`ui/components/`, `data/util/`, or a common extension file).
- **Keep shared code general.** Shared utilities should not import feature-specific types. If they do, they belong in the feature package, not in shared.
- **Name shared files by purpose,** not by the feature that first created them (e.g. `DateFormatting.kt` not `RecordingDateUtils.kt`).
- **Delete dead code.** After refactoring, remove the old copy. Don't leave commented-out or orphaned implementations.

---

## 9) Logging and Debugging

- Use `Timber` or `android.util.Log` with tag conventions (e.g. `TAG = "RecordingVM"`).
- **Never log:** raw transcripts, audio file paths with user content, user email, or any PII.
- Log only event-level info: "recordingStarted", "transcriptionFailed(code)", "uploadComplete".

---

## 10) Permissions

- Request `RECORD_AUDIO` just-in-time (when user taps record).
- If targeting API 33+, request `POST_NOTIFICATIONS` for any notification (e.g. "transcription complete").
- Handle denial gracefully: explain why, offer Settings link, do not crash.
- Add proper entries in `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  - `<uses-permission android:name="android.permission.INTERNET" />`

---

## 11) Testing

- **Unit-test pure logic:** date formatting, title truncation, action-item JSON parsing, state mapping.
- **Mock repositories** using interfaces for ViewModel tests.
- **Avoid UI tests early** unless requested; focus on ViewModel and repository tests.
- Use `kotlinx-coroutines-test` for testing coroutine-based code.
- Use Hilt test utilities or manual DI for injecting mocks.

---

## 12) Code Review Checklist

Before finalizing any change:
- Builds with no errors or warnings (or explain unavoidable warnings).
- No `!!` (unless documented and justified).
- No main-thread blocking (network, file I/O, Firebase calls on Dispatchers.IO).
- Errors are handled and mapped to UI safely (no raw exceptions shown to user).
- State ownership is correct (ViewModel owns, Composable reads).
- Access control is sensible (`private` where possible).
- No sensitive data in logs.
- New UI follows **Style_Guide_Compose.md** (colors, touch targets, no emoji, M3 surface hierarchy).
- **No duplicated logic.** If similar code exists elsewhere, refactor into a shared function or composable.
- **Existing components reused.** Check that `ui/components/`, repositories, and utilities were searched before introducing new ones.
- **Code is concise.** No unnecessary wrapper functions, redundant variables, or verbose patterns that Kotlin can express more cleanly.
- **Dead code removed.** No commented-out blocks, unused imports, or orphaned functions left behind after refactoring.

---

## 13) Output Requirements When Generating Code

When adding code, include:
- A brief note: what files changed.
- How to run it (e.g. "Build & run on emulator or device").
- If a new pattern was introduced, a short explanation (2-3 lines max).
- Ensure new UI respects **Style_Guide_Compose.md**.

---

## 14) "Don't Do This" List

- Don't paste huge code blocks without integrating into existing structure.
- Don't create duplicate models/repositories for similar concepts.
- **Don't invent Firebase collections or fields.** All data shapes are defined in **Android_Developer_Brief.md**. Use those only.
- Don't ignore Android API level constraints -- if using API 31+ features (e.g. `Modifier.blur`), provide a fallback or call it out.
- Don't hardcode the OpenAI API key in source code. Use Cloud Functions, Remote Config, or BuildConfig (not checked into VCS).
- Don't use emoji in UI, copy, or code; use Lucide icons (`com.composables.icons.lucide`) per **Style_Guide_Compose.md**.
- Don't call Firestore or Storage on the main thread.
- **Don't copy-paste code across features.** If two features need the same logic, extract it. Copy-pasting is a code smell that leads to divergent bugs.
- **Don't create a new composable when a shared one already exists** in `ui/components/`. Search first, add parameters if needed, only then create new.
- **Don't write verbose code when Kotlin offers a concise alternative.** Avoid manual loops where collection operators work, Java-style builders where `apply {}` works, or multiple overloads where default parameters work.
- **Don't leave dead code.** No commented-out blocks, no "just in case" unused functions, no orphaned files after refactoring.

---

## 15) Suggested Defaults

- **Min SDK:** 26 (Android 8.0) for broad reach. Increase to 28 or 29 if you want `MediaRecorder.pause/resume` without compat workarounds.
- **Target SDK:** Latest stable (e.g. 34 or 35).
- **Compose BOM:** Latest stable Compose BOM.
- **Kotlin:** Latest stable (e.g. 2.0+).
- **Build system:** Gradle with Kotlin DSL.
- **DI:** Hilt.
- **Networking (OpenAI):** Retrofit + OkHttp or Ktor. Keep it thin; only transcription + chat endpoints.
- **Image loading (if needed later):** Coil.

---

*Follow this guide alongside Android_Developer_Brief.md and Style_Guide_Compose.md for a complete, consistent, production-ready Android app.*
