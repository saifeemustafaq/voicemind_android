# VoiceMind AI

A native Android app (Kotlin / Jetpack Compose) for voice-first note-taking with AI-powered transcription, auto-titling, and action-item extraction. Backed by Firebase (Auth, Firestore, Cloud Storage) and OpenAI via Cloud Functions.

## Repository Structure

```
mem_and/
├── android/                  # Android app (Kotlin / Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/voicemind/
│   │   ├── google-services.json           ← git-ignored (see setup below)
│   │   └── google-services.json.example   ← template with placeholder values
│   ├── build.gradle.kts
│   ├── gradle/
│   └── settings.gradle.kts
├── functions/                # Firebase Cloud Functions (TypeScript)
│   ├── src/
│   ├── package.json
│   └── tsconfig.json
├── firebase.json             # Firebase hosting / functions config
├── firestore.rules           # Firestore security rules
├── storage.rules             # Cloud Storage security rules
├── .firebaserc               # Firebase project alias
├── Android_Developer_Brief.md
├── DeveloperGuide.md
├── Style_Guide_Compose.md
├── SETUP_GUIDE.md
├── VoiceMind_AI_Feature_Document.md
└── mvp.md
```

## Prerequisites

| Tool | Check | Install |
|------|-------|---------|
| Android Studio (Ladybug+) | Open it | https://developer.android.com/studio |
| JDK 17+ | `java -version` | Bundled with Android Studio, or `brew install openjdk@17` |
| Git | `git --version` | `brew install git` |
| Node.js 18+ | `node -v` | `brew install node` |
| Firebase CLI | `firebase --version` | `npm install -g firebase-tools` |
| GitHub CLI (optional) | `gh --version` | `brew install gh` |

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/voicemind-android.git
cd voicemind-android
```

### 2. Set up Firebase credentials

The `google-services.json` file is **not** checked into version control because it contains API keys. Each developer must supply their own:

1. Go to the [Firebase Console](https://console.firebase.google.com) and open your project.
2. Navigate to **Project Settings** (gear icon) → **General** → **Your apps** → **Android app**.
3. Download `google-services.json`.
4. Place it at `android/app/google-services.json`.

A template with the expected structure is available at `android/app/google-services.json.example`.

### 3. Store the OpenAI API key (Cloud Functions)

The OpenAI key is stored as a Firebase secret and never appears in code:

```bash
firebase functions:secrets:set OPENAI_API_KEY
# Paste your sk-proj-... key when prompted
```

### 4. Install Cloud Functions dependencies

```bash
cd functions
npm install
cd ..
```

### 5. Open the Android project

Open the `android/` folder in Android Studio. Let Gradle sync complete, then run on an emulator or device.

## Git & Version Control

### Initial setup (if starting fresh)

```bash
cd /path/to/mem_and
git init
git add .
git commit -m "Initial commit: VoiceMind AI Android app with Firebase backend"
```

### Pushing to GitHub

```bash
# Option A: Using GitHub CLI (recommended)
gh repo create voicemind-android --private --source=. --push

# Option B: Manual
# 1. Create a new repo on github.com (do NOT initialize with README)
# 2. Then run:
git remote add origin https://github.com/YOUR_USERNAME/voicemind-android.git
git branch -M main
git push -u origin main
```

### What is and isn't tracked

The `.gitignore` files are configured to exclude:

| Excluded | Reason |
|----------|--------|
| `android/app/google-services.json` | Contains Firebase API keys |
| `webclientid.md` | Contains OAuth client ID |
| `*.jks`, `*.keystore` | Signing keys |
| `.env`, `.env.*` | Environment variables |
| `node_modules/` | NPM dependencies (recreated via `npm install`) |
| `.gradle/`, `build/`, `.kotlin/` | Gradle build caches (regenerated on build) |
| `.idea/` | Android Studio user-specific settings |
| `local.properties` | Local Android SDK path |
| `.firebase/` | Firebase CLI cache |

### Branching workflow (recommended)

```bash
# Create a feature branch
git checkout -b feature/your-feature-name

# Work on your changes, then commit
git add .
git commit -m "Add your feature description"

# Push the branch
git push -u origin feature/your-feature-name

# Create a pull request on GitHub
gh pr create --title "Add your feature" --body "Description of changes"
```

## Sensitive Files Checklist

Before every commit, verify none of these are staged:

```bash
git status
```

If any of these appear as tracked, remove them from tracking:

```bash
git rm --cached android/app/google-services.json
git rm --cached webclientid.md
```

## Documentation

| Document | Purpose |
|----------|---------|
| [SETUP_GUIDE.md](./SETUP_GUIDE.md) | Step-by-step Firebase and Android Studio setup |
| [Android_Developer_Brief.md](./Android_Developer_Brief.md) | Architecture, data models, and feature flows |
| [DeveloperGuide.md](./DeveloperGuide.md) | Kotlin/Compose engineering practices |
| [Style_Guide_Compose.md](./Style_Guide_Compose.md) | Visual and code conventions for Compose UI |
| [VoiceMind_AI_Feature_Document.md](./VoiceMind_AI_Feature_Document.md) | Full product vision and feature catalog |
| [mvp.md](./mvp.md) | MVP scope and tracking |

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Hilt DI
- **Auth:** Firebase Authentication (Email/Password + Google Sign-In)
- **Database:** Cloud Firestore
- **Storage:** Firebase Cloud Storage (audio files)
- **AI:** OpenAI gpt-4o-mini-transcribe (transcription), gpt-4o-mini (auto-title, action items)
- **Backend:** Firebase Cloud Functions (TypeScript)
