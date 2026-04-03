# Linux Pair Programming Setup Guide for VoiceMind AI

Everything needed to get the VoiceMind AI Android project building and running on a collaborator's Linux machine.

---

## Phase 1: Access Provisioning (Project Owner)

Complete these steps **before** the collaborator begins setup.

### 1A. GitHub Repository Access

1. Go to [github.com/saifeemustafaq/voicemind_android](https://github.com/saifeemustafaq/voicemind_android) → **Settings** → **Collaborators** → **Add people**.
2. Add the collaborator's GitHub username or email.
3. They will receive an email invitation to accept.

### 1B. Firebase Project Access

1. Go to [Firebase Console](https://console.firebase.google.com) → **Project Settings** (gear icon) → **Users and permissions**.
2. Click **Add member** → enter the collaborator's Google email.
3. Assign role: **Editor** (allows deploying functions, reading secrets, managing services).
4. This gives them access to download `google-services.json` and manage Firebase resources.

### 1C. Share the Release Keystore

- The release keystore lives at `~/voicemind-release.jks` (git-ignored).
- Send it via a **secure channel** (password manager file share, Signal, etc. — not email or Slack).
- The collaborator should place it at `~/voicemind-release.jks` on their Linux machine.
- The keystore password, key alias, and key password are in `android/app/build.gradle.kts` (available once they clone the repo).

### 1D. Web Client ID

The Google OAuth Web Client ID is already hardcoded in the source code — no separate sharing required. It appears in:

- `android/app/src/main/java/com/voicemind/ui/auth/SignInScreen.kt`
- `android/app/src/main/java/com/voicemind/data/repository/GoogleCalendarRepository.kt`

---

## Phase 2: Linux Machine Setup (Collaborator)

### 2A. Install Prerequisites

**Git:**

```bash
# Ubuntu/Debian
sudo apt update && sudo apt install git

# Fedora
sudo dnf install git

# Verify
git --version
```

**JDK 17:**

```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# Fedora
sudo dnf install java-17-openjdk-devel

# Verify
java -version
javac -version
```

Add to `~/.bashrc` (or `~/.zshrc`):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

Then reload: `source ~/.bashrc`

**Node.js 18+ (for Cloud Functions):**

```bash
# Option A: NodeSource (recommended)
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Option B: nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 18
nvm use 18

# Verify
node -v
npm -v
```

**Firebase CLI:**

```bash
npm install -g firebase-tools
firebase --version
```

**GitHub CLI (optional but recommended):**

```bash
# Ubuntu/Debian (via official apt repo)
type -p curl >/dev/null || sudo apt install curl -y
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
sudo chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
sudo apt update
sudo apt install gh -y

# Verify
gh --version
```

### 2B. Install Android Studio

1. Download from [developer.android.com/studio](https://developer.android.com/studio).

2. Extract and install:

```bash
sudo tar -xzf android-studio-*.tar.gz -C /opt/
```

3. Launch:

```bash
/opt/android-studio/bin/studio.sh
```

4. Inside Android Studio, go to **Tools → Create Desktop Entry** for a launcher shortcut.

**First-launch setup in Android Studio:**

- Accept all SDK license agreements.
- Install **Android SDK Platform 35** (API 35) — this matches the project's `compileSdk = 35`.
- Install **Android SDK Build-Tools** (latest).
- Install **Android SDK Command-line Tools**.
- Install **Android Emulator** + a system image (e.g., API 35 x86_64).

**Set ANDROID_HOME** — add to `~/.bashrc` (or `~/.zshrc`):

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

Then reload: `source ~/.bashrc`

**Accept SDK licenses from terminal:**

```bash
yes | sdkmanager --licenses
```

---

## Phase 3: Clone and Configure the Project

### 3A. Clone the Repository

```bash
# Authenticate with GitHub (choose HTTPS or SSH when prompted)
gh auth login

# Clone
git clone https://github.com/saifeemustafaq/voicemind_android.git
cd voicemind_android

# Checkout the working branch
git checkout v10_recordingspage
```

If not using `gh`, set up an SSH key instead: [GitHub SSH docs](https://docs.github.com/en/authentication/connecting-to-github-with-ssh).

### 3B. Set Up `google-services.json`

1. Go to [Firebase Console](https://console.firebase.google.com) → select the **voicemind-androids** project.
2. Go to **Project Settings** → **General** → scroll to **Your apps** → **Android app**.
3. Click **Download google-services.json**.
4. Place it at `android/app/google-services.json`.

A template showing the expected structure is available at `android/app/google-services.json.example`.

### 3C. Add Debug SHA-1 to Firebase

This is **critical** — Google Sign-In will not work without the collaborator's debug SHA-1 registered in Firebase.

```bash
cd android
chmod +x gradlew
./gradlew signingReport
```

Look for output like:

```
Variant: debug
Config: debug
Store: /home/username/.android/debug.keystore
Alias: AndroidDebugKey
MD5:  ...
SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
SHA-256: ...
```

Then:

1. Copy the **SHA1** value.
2. Go to Firebase Console → **Project Settings** → **Your apps** → Android app → **Add fingerprint**.
3. Paste the SHA-1 and save.
4. **Re-download** `google-services.json` after adding the fingerprint (it gets embedded) and replace the file at `android/app/google-services.json`.

### 3D. Place the Release Keystore

```bash
cp /path/to/received/voicemind-release.jks ~/voicemind-release.jks
```

The build expects this file at `~/voicemind-release.jks` (configured in `android/app/build.gradle.kts`).

### 3E. Set Up `local.properties`

This file is auto-generated when you open the project in Android Studio. To create it manually:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > android/local.properties
```

### 3F. Firebase CLI Login and Secrets

```bash
# Login to Firebase (opens a browser window)
firebase login

# Verify project access
firebase projects:list
# Should show "voicemind-androids" in the list

# Set the active project
firebase use voicemind-androids

# Verify access to secrets
firebase functions:secrets:access OPENAI_API_KEY
firebase functions:secrets:access GOOGLE_CLIENT_SECRET
```

Both secrets should already be configured server-side. If either is missing, the project owner needs to set them:

```bash
firebase functions:secrets:set OPENAI_API_KEY
firebase functions:secrets:set GOOGLE_CLIENT_SECRET
```

### 3G. Install Cloud Functions Dependencies

```bash
cd functions
npm install
cd ..
```

---

## Phase 4: Build and Verify

### 4A. Gradle Sync

Open the `android/` folder in Android Studio and wait for Gradle sync to complete (the first sync downloads ~500MB+ of dependencies).

Or from the terminal:

```bash
cd android
./gradlew --refresh-dependencies
```

### 4B. Debug Build Check

```bash
cd android
./gradlew compileDebugKotlin 2>&1
```

A clean build should complete with **BUILD SUCCESSFUL**.

**Common first-build issues:**

| Error | Fix |
|-------|-----|
| "SDK location not found" | Check `local.properties` has the correct `sdk.dir` path |
| "No matching client found" | `google-services.json` package name doesn't match `com.voicemind` — re-download from Firebase |
| "License for package ... not accepted" | Run `yes \| sdkmanager --licenses` |
| Permission denied on `gradlew` | Run `chmod +x gradlew` |

### 4C. Run on Emulator or Device

**Emulator:**

1. Open Android Studio → **Device Manager** → **Create Virtual Device**.
2. Pick a device (e.g., Pixel 7) → select **API 35** system image → Finish.
3. Run the app via the green play button or `./gradlew installDebug`.

**Physical device:**

1. Enable **Developer Options** on the device (tap Build Number 7 times in Settings → About Phone).
2. Enable **USB Debugging** in Developer Options.
3. Connect via USB, approve the debugging prompt on the device.
4. Run the app.

### 4D. Full Release Build (optional)

```bash
cd android
./gradlew assembleRelease
```

This requires `~/voicemind-release.jks` to be in place.

---

## Phase 5: Pair Programming Environment

### 5A. IDE Recommendations

| Tool | Best For |
|------|----------|
| **Android Studio** | Building, running, debugging, emulator management, layout preview |
| **Cursor** | AI-assisted editing; open the project root for full context across Android + Cloud Functions |

Recommended: use both. Android Studio for build/run/debug, Cursor for editing.

### 5B. Branch Workflow

```bash
# Work on feature branches, not directly on v10_recordingspage
git checkout -b feature/your-feature-name

# Pull latest changes frequently
git pull origin v10_recordingspage

# Push your branch
git push -u origin feature/your-feature-name

# Create a pull request
gh pr create --title "Your feature" --body "Description of changes"
```

### 5C. Key Documentation

| Document | Purpose |
|----------|---------|
| [Android_Developer_Brief.md](Android_Developer_Brief.md) | Architecture, data models, feature flows |
| [DeveloperGuide.md](DeveloperGuide.md) | Kotlin/Compose engineering practices |
| [Style_Guide_Compose.md](Style_Guide_Compose.md) | UI conventions |
| [mvp.md](mvp.md) | MVP scope and tracking |

---

## Quick Reference: What Goes Where

| File | Location | Source |
|------|----------|--------|
| Release keystore | `~/voicemind-release.jks` | Shared by project owner |
| Local SDK path | `android/local.properties` | Auto-generated by Android Studio |
| Firebase config | `android/app/google-services.json` | Downloaded from Firebase Console |
| OpenAI API key | Firebase secret (`OPENAI_API_KEY`) | Stored server-side via `firebase functions:secrets:set` |
| Google client secret | Firebase secret (`GOOGLE_CLIENT_SECRET`) | Stored server-side via `firebase functions:secrets:set` |

---

## Setup Checklist

Use this to verify everything is in place:

- [ ] GitHub collaborator invite sent and accepted
- [ ] Firebase Editor access granted
- [ ] Git, JDK 17, Node 18+, Firebase CLI installed on Linux
- [ ] Android Studio installed with SDK 35, build tools, emulator
- [ ] `ANDROID_HOME` and `JAVA_HOME` environment variables set
- [ ] Repository cloned, correct branch checked out (`v10_recordingspage`)
- [ ] `google-services.json` downloaded and placed in `android/app/`
- [ ] Debug SHA-1 added to Firebase project
- [ ] `google-services.json` re-downloaded after adding SHA-1
- [ ] Release keystore placed at `~/voicemind-release.jks`
- [ ] `local.properties` created or auto-generated
- [ ] `firebase login` completed, project access verified
- [ ] `npm install` run in `functions/`
- [ ] `./gradlew compileDebugKotlin` passes with BUILD SUCCESSFUL
- [ ] App runs on emulator or physical device
