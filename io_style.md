# VoiceMind AI — Style Guide (Swift / SwiftUI)

This guide documents the VoiceMind AI design system for iOS, built natively with SwiftUI. Since we are on the native platform, we lean directly into Apple's system colors, SF Pro typography, and SF Symbols rather than approximating them.

---

## 1. Design philosophy

- **Clarity** -- Clean whitespace, content-first. UI elements are legible and purposeful.
- **Deference** -- The UI recedes; content takes center stage. Neutral backgrounds, no visual noise.
- **Depth** -- Subtle layering via white cards on a grouped gray background. No glassmorphism.
- **Restraint** -- Color is used sparingly and meaningfully. A single accent color (system blue) for interactive elements, red for destructive actions, green for success/completion.

---

## 2. Color palette

Use Apple's built-in semantic colors wherever possible. Define custom `Color` extensions in a `Colors.swift` file only when a system equivalent doesn't exist.

### Backgrounds

| SwiftUI Color | Android Equivalent | Usage |
|---------------|-------------------|-------|
| `Color(.systemGroupedBackground)` | `IosBackground` (#F2F2F7) | App background (grouped table style) |
| `Color(.secondarySystemGroupedBackground)` | `IosSecondaryBackground` (#FFFFFF) | Card/surface white |
| `Color(.tertiarySystemGroupedBackground)` | `IosTertiaryBackground` (#F2F2F7) | Secondary grouped background |

### Accent and Semantic

| SwiftUI Color | Android Equivalent | Usage |
|---------------|-------------------|-------|
| `.accentColor` / `.blue` | `IosAccent` (#007AFF) | Primary actions, links, interactive elements |
| `.red` | `IosDestructive` (#FF3B30) | Delete, stop recording, destructive actions |
| `.green` | `IosSuccess` (#34C759) | Completion, toggle on state |
| `.orange` | `IosWarning` (#FF9500) | Warning states |

Set the app-wide accent in the asset catalog (`AccentColor`) to `.blue` / `#007AFF`.

### Text

| SwiftUI Color | Android Equivalent | Usage |
|---------------|-------------------|-------|
| `.primary` | `IosLabel` (#000000) | Primary text |
| `.secondary` | `IosSecondaryLabel` (#3C3C43 @ 60%) | Secondary/metadata text |
| `Color(.tertiaryLabel)` | `IosTertiaryLabel` (#3C3C43 @ 30%) | Placeholder/disabled text |

### Separators and Fills

| SwiftUI Color | Android Equivalent | Usage |
|---------------|-------------------|-------|
| `Color(.separator)` | `IosSeparator` (#3C3C43 @ 12%) | Thin dividers between items |
| `Color(.opaqueSeparator)` | `IosOpaqueSeparator` (#C6C6C8) | Opaque borders (text fields, outlines) |
| `Color(.tertiarySystemFill)` | `IosTertiaryFill` (#787880 @ 12%) | Subtle fills |
| `Color(.quaternarySystemFill)` | `IosQuaternaryFill` (#747480 @ 8%) | Lightest fills (selected container tints) |
| `.white` | `IosWhite` (#FFFFFF) | Card surfaces, navigation bars |

---

## 3. Typography

Use the system **SF Pro** font via SwiftUI's built-in `Font` API. No custom font files needed.

| SwiftUI Font | Android Equivalent | Size | Weight |
|--------------|-------------------|------|--------|
| `.largeTitle` | `headlineLarge` | 34pt | Bold |
| `.title` | `titleLarge` | 28pt | Bold |
| `.title2` | `titleMedium` | 22pt | SemiBold (use `.bold()`) |
| `.headline` | `titleSmall` | 17pt | SemiBold |
| `.body` | `bodyLarge` | 17pt | Regular |
| `.callout` | `bodyMedium` | 16pt | Regular |
| `.footnote` | `bodySmall` | 13pt | Regular |
| `.caption` | `labelMedium` | 12pt | Regular |
| `.caption2` | `labelSmall` | 11pt | Regular |

Text color should be applied via `.foregroundStyle()` using the semantic colors above rather than baking color into custom font styles.

---

## 4. Shapes

Use `RoundedRectangle(cornerRadius:)` with these standard radii:

| Radius | Usage | Android Equivalent |
|--------|-------|--------------------|
| 4pt | Small badges | `extraSmall` |
| 8pt | Buttons, chips | `small` |
| 10pt | Cards, list containers | `medium` |
| 12pt | Primary buttons, larger cards | `large` |
| 22pt | Bottom sheets, modals | `extraLarge` |

---

## 5. Components

### CardView

Opaque white surface with 10pt corner radius, no border, no shadow. Default 16pt inner padding. Android equivalent: `GlassCard`.

```swift
struct CardView<Content: View>: View {
    let cornerRadius: CGFloat
    let innerPadding: CGFloat
    let content: () -> Content

    init(
        cornerRadius: CGFloat = 10,
        innerPadding: CGFloat = 16,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.cornerRadius = cornerRadius
        self.innerPadding = innerPadding
        self.content = content
    }

    var body: some View {
        content()
            .padding(innerPadding)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}
```

### PrimaryButton

Accent blue, 12pt radius, 50pt height, white text. Disabled state at 40% opacity.

```swift
struct PrimaryButton: View {
    let title: String
    let action: () -> Void
    var isEnabled: Bool = true

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(isEnabled ? .accent : .accent.opacity(0.4))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(!isEnabled)
    }
}
```

### VoiceMindNavBar

Inline navigation bar with a 22pt accent-tinted SF Symbol beside the title (`.headline` weight). Supports optional back navigation. Android equivalent: `VoiceMindTopAppBar`.

```swift
.navigationBarTitleDisplayMode(.inline)
.toolbar {
    ToolbarItem(placement: .principal) {
        HStack(spacing: 8) {
            Image(systemName: "mic.fill")
                .foregroundStyle(.accent)
                .font(.system(size: 18))
            Text("Recordings")
                .font(.headline)
        }
    }
}
```

### EmptyStateView

A `CardView` with a centered 48pt SF Symbol (`.secondary`), a message (`.callout`, `.secondary`), and an optional extra content slot. Android equivalent: `EmptyStateCard`.

```swift
struct EmptyStateView: View {
    let systemImage: String
    let message: String

    var body: some View {
        CardView {
            VStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}
```

### VoiceMindTextField

Styled `TextField` with accent focused border, opaque separator unfocused border, 10pt corner radius. Android equivalent: `voiceMindTextFieldColors()`.

```swift
struct VoiceMindTextField: View {
    let label: String
    @Binding var text: String
    var systemImage: String? = nil

    @FocusState private var isFocused: Bool

    var body: some View {
        HStack {
            if let systemImage {
                Image(systemName: systemImage)
                    .foregroundStyle(.secondary)
            }
            TextField(label, text: $text)
                .focused($isFocused)
        }
        .padding(12)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(isFocused ? .accent : Color(.opaqueSeparator), lineWidth: 1)
        )
    }
}
```

### RecordButton

72pt circular button (accent container, white mic icon at 32pt) inside a 160pt frame with a radial gradient (`systemGroupedBackground` → clear) for contrast over content. Android equivalent: `RecordFab`.

```swift
struct RecordButton: View {
    let action: () -> Void

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(.systemGroupedBackground), .clear],
                center: .center,
                startRadius: 0,
                endRadius: 80
            )
            .frame(width: 160, height: 160)

            Button(action: action) {
                Image(systemName: "mic.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(.white)
                    .frame(width: 72, height: 72)
                    .background(.accent)
                    .clipShape(Circle())
            }
        }
    }
}
```

---

## 6. Spacing and layout

- **Grid:** 4pt base. Use 8, 16, 24, 32pt for padding and spacing.
- **Touch targets:** 44pt minimum for tappable controls (Apple HIG standard).
- **Screen margins:** 16pt horizontal (use `.padding(.horizontal, 16)`).
- **Section headers:** Use `.footnote` in `.secondary`, uppercase, with 16pt leading padding — matches iOS grouped table section headers. Or use native `Section` with a `header:` inside a `List`/`Form`.

---

## 7. Navigation

### Tab bar (default)

Use SwiftUI `TabView`. The system provides native styling automatically.

```swift
TabView {
    HomeView()
        .tabItem { Label("Home", systemImage: "house.fill") }
    RecordingsView()
        .tabItem { Label("Recordings", systemImage: "waveform") }
    ChecklistView()
        .tabItem { Label("Checklist", systemImage: "checklist") }
    FoldersView()
        .tabItem { Label("Folders", systemImage: "folder.fill") }
    SettingsView()
        .tabItem { Label("Settings", systemImage: "gearshape.fill") }
}
.tint(.accent)
```

- System handles selected/unselected colors via `.tint()`
- No indicator highlight (system default)

### Navigation bar (inline style)

- Inline display mode (`.navigationBarTitleDisplayMode(.inline)`)
- Title: `.headline` weight, preceded by an accent-tinted SF Symbol
- Back button: system-provided via `NavigationStack`

### Sidebar (iPad / optional)

If supporting sidebar on iPad, use `NavigationSplitView`:

- System handles selected/unselected item styling
- Selected item: accent text/icon with system highlight
- Unselected: primary text, secondary icon

---

## 8. Semantic color usage

| Context | Color |
|---------|-------|
| Interactive elements (buttons, links, play) | `.accent` / `.blue` |
| Stop recording, delete, destructive | `.red` |
| Completed/toggle on | `.green` |
| Warning | `.orange` |
| Folder/item icons | `.accent` |
| Section headers, secondary text | `.secondary` |
| Dividers | `Color(.separator)` |

---

## 9. Recording UI

- **Record button:** 72pt accent circle with 32pt white mic SF Symbol, wrapped in a 160pt container with a radial gradient (`systemGroupedBackground` → clear) for contrast over content
- **Recording status label:** Red (`.red`) with pulse animation (`.phaseAnimator`)
- **Timer:** Primary color (`.primary`), monospaced (`.monospacedDigit()`)
- **Stop button:** Red circle (`.red`)
- **Pause/Resume:** Accent blue circle (`.accent`)
- **Discard:** Red SF Symbol on light red background (`.red.opacity(0.1)`)

---

## 10. Icons

Use **SF Symbols** (filled variant preferred). No emoji.

| Function | SF Symbol | Android Equivalent |
|----------|-----------|-------------------|
| Record | `mic.fill` | `Mic` |
| Play | `play.circle.fill` | `PlayCircle` |
| Pause | `pause.circle.fill` | `PauseCircle` |
| Stop | `stop.fill` | `Stop` |
| Delete | `trash.fill` | `Delete` |
| Folder | `folder.fill` | `Folder` |
| Checklist done | `checkmark.circle.fill` | `CheckCircle` |
| Checklist pending | `circle` | `RadioButtonUnchecked` |
| More options | `ellipsis` | `MoreVert` |
| Edit/rename | `pencil` | `Edit` |
| Share | `square.and.arrow.up` | `Share` |
| Disclosure | `chevron.right` | `ChevronRight` |
| Home | `house.fill` | `Home` |
| Settings | `gearshape.fill` | `Settings` |
| Waveform | `waveform` | `GraphicEq` |

Icon colors: `.accent` for interactive, `.red` for destructive, `.secondary` for passive/disclosure.

---

## 11. Quick reference

| Element | Treatment |
|---------|-----------|
| App background | `Color(.systemGroupedBackground)` |
| Cards | White, 10pt radius, no border/shadow |
| Section headers | Uppercase, `.footnote`, `.secondary` |
| Primary buttons | `.accent` blue, 12pt radius, 50pt height |
| Record button | 72pt accent circle, 160pt gradient container |
| Destructive actions | `.red` |
| Toggle on state | System default (`.green` track, white thumb) |
| Separators | `Color(.separator)` @ system thickness |
| Text fields | 10pt radius, `.accent` focused border, `Color(.opaqueSeparator)` unfocused |
| Sheets | Use `.sheet()` — system handles corner radius and presentation |

---

## 12. Key differences from Android

| Aspect | Android (Compose) | iOS (SwiftUI) |
|--------|-------------------|---------------|
| Colors | Custom `Color.kt` constants | System semantic colors (`Color(.systemX)`) |
| Fonts | Inter font family (custom) | SF Pro (system default) |
| Icons | Material Icons | SF Symbols |
| Units | `dp` / `sp` | `pt` (1:1 equivalent) |
| Cards | `GlassCard` (custom) | `CardView` (custom) |
| FAB | `RecordFab` | `RecordButton` (overlay, not FAB) |
| Navigation | `TabView` + sidebar drawer | `TabView` + `NavigationSplitView` |
| Sheets | `ModalBottomSheet` | `.sheet()` / `.fullScreenCover()` |
| Lists | `LazyColumn` | `List` with `ForEach` |
| Text fields | `OutlinedTextField` + custom colors | `VoiceMindTextField` (custom) |
| Touch target | 48dp minimum | 44pt minimum (Apple HIG) |

---

*This style guide keeps the iOS app visually identical to the Android app while using native SwiftUI patterns, system colors, SF Pro, and SF Symbols for a first-class iOS experience.*
