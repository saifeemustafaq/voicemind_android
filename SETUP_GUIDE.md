# VoiceMind AI — Android & Firebase Setup Guide

Everything you need to do (manually, in browsers and Android Studio) before development can begin. Once every section is complete, the codebase will be ready for coding.

---

## Prerequisites

Make sure these are installed on your Mac:

| Tool | How to check | Install |
|------|-------------|---------|
| **Android Studio** (latest stable, Ladybug or newer) | Open it | https://developer.android.com/studio |
| **JDK 17+** | `java -version` in terminal | Bundled with Android Studio, or `brew install openjdk@17` |
| **Git** | `git --version` | `brew install git` (or Xcode CLI tools) |
| **Node.js 18+** (only if using Cloud Functions) | `node -v` | `brew install node` or https://nodejs.org |
| **Firebase CLI** (only if using Cloud Functions) | `firebase --version` | `npm install -g firebase-tools` |

Open Android Studio at least once to let it download SDKs (API 34 or 35) and accept licenses.

---

## Step 1 — Create the Android Project in Android Studio

1. Open **Android Studio → New Project**.
2. Pick **"Empty Activity"** (the Compose one, not the Views one).
3. Fill in:
   - **Name:** `VoiceMind AI`
   - **Package name:** `com.voicemind` (or `com.voicemind.ai` — pick one and stick with it)
   - **Save location:** This folder (`/Users/msaifee/Desktop/Cursor/mem_and`) or a subfolder like `mem_and/android`
   - **Language:** Kotlin
   - **Minimum SDK:** API 26 (Android 8.0) — recommended for broad support
   - **Build configuration language:** Kotlin DSL (recommended)
4. Click **Finish**. Let Gradle sync complete (may take a few minutes the first time).
5. **Run the app once** on an emulator or physical device to confirm the blank Compose project works. You should see a "Hello Android!" or similar screen.

> **Important:** Note your exact **package name** — you'll need it for Firebase.

---

## Step 2 — Create a Firebase Project

1. Go to **https://console.firebase.google.com**
2. Click **"Add project"** (or "Create a project").
3. **Project name:** `VoiceMind AI` (or similar).
4. **Google Analytics:** You can enable or disable — for MVP it doesn't matter. If you enable it, select or create a Google Analytics account.
5. Click **"Create project"** and wait for it to provision.

---

## Step 3 — Register the Android App in Firebase

1. In the Firebase console, on your project's overview page, click the **Android icon** (or "+ Add app" → Android).
2. Fill in:
   - **Android package name:** `com.voicemind` (must exactly match what you used in Step 1)
   - **App nickname:** `VoiceMind Android` (optional, for your reference)
   - **Debug signing certificate SHA-1:** **Required for Google Sign-In.** Get it by running this in your terminal:

   ```bash
   cd /path/to/your/android/project
   ./gradlew signingReport
   ```

   Look for the **SHA1** under `Variant: debug`. It looks like:
   ```
   SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
   ```

   Copy the full SHA-1 hash and paste it into the Firebase console.

3. Click **"Register app"**.
4. **Download `google-services.json`** — Firebase will offer this file. Download it.
5. **Place `google-services.json`** in your Android project's **`app/`** directory:
   ```
   mem_and/android/app/google-services.json
   ```
   (Adjust the path to wherever your `app/` module lives.)

6. Firebase will show you Gradle snippets to add. **Don't worry about these yet** — I'll configure all the Gradle files when I start coding. Just make sure the `google-services.json` file is in the right place.

7. Click through the remaining steps ("Next", "Continue to console").

---

## Step 4 — Set Up Firebase Authentication

### 4A — Enable Email/Password Sign-In

1. In the Firebase console, go to **Build → Authentication** (left sidebar).
2. Click **"Get started"** if it's your first time.
3. Go to the **"Sign-in method"** tab.
4. Click **"Email/Password"**.
5. Toggle **"Enable"** to ON.
6. Leave "Email link (passwordless sign-in)" OFF for now.
7. Click **"Save"**.

### 4B — Enable Google Sign-In

1. Still on the **"Sign-in method"** tab, click **"Add new provider"** → **"Google"**.
2. Toggle **"Enable"** to ON.
3. **Project public-facing name:** `VoiceMind AI` (users will see this on the Google sign-in consent screen).
4. **Project support email:** Select your email from the dropdown.
5. Click **"Save"**.
6. After saving, you'll see Google listed as an enabled provider. **Expand it and note the "Web client ID"** — you'll need this value in the Android app code. It looks like:
   ```
   123456789-abcdefg.apps.googleusercontent.com
   ```
   Copy it somewhere safe — I'll need it when writing the auth code.

### 4C — (Optional) Add a test user

1. Go to the **"Users"** tab in Authentication.
2. Click **"Add user"**.
3. Enter an email and password you want to test with (e.g., `test@voicemind.ai` / `TestPassword123`).
4. This lets you test email/password login immediately without building a registration flow first.

---

## Step 5 — Set Up Cloud Firestore

1. In the Firebase console, go to **Build → Firestore Database**.
2. Click **"Create database"**.
3. **Choose a location:** Pick the region closest to you (e.g., `us-central1` for US, `europe-west1` for EU). This **cannot be changed** later.
4. **Security rules:** Select **"Start in test mode"** for now (allows all reads/writes for 30 days). We'll replace these with proper rules from the developer brief later.
5. Click **"Create"** (or "Enable").

The database is now ready. It will be empty — the app will create collections (`users/{uid}/recordings`, `users/{uid}/folders`, `users/{uid}/actionItems`) automatically at runtime.

---

## Step 6 — Set Up Firebase Cloud Storage

1. In the Firebase console, go to **Build → Storage**.
2. Click **"Get started"**.
3. **Security rules:** Accept the default or select **"Start in test mode"** (same as Firestore — we'll lock it down later).
4. **Location:** Should default to the same region you chose for Firestore. Confirm and click **"Done"**.

Storage is now ready. Audio files will be uploaded to paths like `users/{uid}/audio/{recordingId}.m4a`.

---

## Step 7 — Get Your OpenAI API Key

The app needs an OpenAI API key for transcription (gpt-4o-mini-transcribe) and AI features (gpt-4o-mini for auto-title and action-item extraction).

1. Go to **https://platform.openai.com/api-keys**
2. Click **"Create new secret key"**.
3. Name it something like `VoiceMind Android`.
4. Copy the key immediately (you can't see it again). It looks like:
   ```
   sk-proj-...
   ```
5. **Store it securely.** For MVP, we have two options for how to use it:

### Decision: Option B — Firebase Cloud Functions (production-grade)

The OpenAI API key lives on the server inside Cloud Functions and **never touches the Android client**. The Android app calls your Cloud Function via HTTPS; the function calls OpenAI and returns results. This is the correct architecture for a real product.

---

## Step 8 — Set Up Firebase Cloud Functions (REQUIRED)

### 8.1 — Upgrade to Blaze plan FIRST

Cloud Functions **require** the Blaze (pay-as-you-go) plan. You must do this before you can deploy functions or make external network calls (to OpenAI).

1. In the Firebase console, click **"Upgrade"** (bottom-left) or go to **Usage & Billing → Details & settings**.
2. Select **Blaze (pay-as-you-go)**.
3. Add a billing account / payment method.
4. **Cost reality:** The Blaze plan still includes the full free tier. For a personal project you'll typically pay $0–5/month. Cloud Functions gives you 2 million free invocations/month and 400K GB-seconds of compute free. You'll only pay for OpenAI API usage (their pricing, not Google's).
5. **Set a budget alert** if you want peace of mind: Billing → Budgets & alerts → Create budget → e.g. $10/month alert.

### 8.2 — Install prerequisites

In your terminal:

```bash
# Check if Node.js is installed (need 18+)
node -v

# If not installed:
brew install node

# Install Firebase CLI globally
npm install -g firebase-tools

# Verify
firebase --version
```

### 8.3 — Login and initialize

```bash
# Login to Firebase (opens browser)
firebase login

# Navigate to your project folder
cd /Users/msaifee/Desktop/Cursor/mem_and

# Initialize Cloud Functions
firebase init functions
```

When prompted during `firebase init functions`:

| Prompt | What to pick |
|--------|-------------|
| **Select a project** | Use an existing project → pick your VoiceMind project |
| **Language** | **TypeScript** (recommended) |
| **ESLint** | Yes |
| **Install dependencies now** | Yes |

This creates a `functions/` folder in your project with boilerplate code.

### 8.4 — Store the OpenAI API key as a secret

```bash
firebase functions:secrets:set OPENAI_API_KEY
```

It will prompt you to paste your key. Paste `sk-proj-...` and press Enter. The key is now stored encrypted in Google Cloud Secret Manager — it never appears in code or config files.

### 8.5 — Don't deploy yet

I'll write the actual Cloud Function code (transcription, auto-title, action-item extraction) when I start development. **You don't need to deploy anything right now** — just make sure the `functions/` folder exists and the secret is set.

To verify the secret was saved:
```bash
firebase functions:secrets:access OPENAI_API_KEY
```
This should print your key back. If it does, you're good.

---

## Step 9 — Verify Everything Is in Place

Run through this checklist:

| # | Item | Status |
|---|------|--------|
| 1 | Android Studio installed, project created, runs on emulator | ☐ |
| 2 | Firebase project created (Blaze plan enabled) | ☐ |
| 3 | Android app registered in Firebase (package name matches) | ☐ |
| 4 | `google-services.json` downloaded and placed in `app/` folder | ☐ |
| 5 | SHA-1 debug key added to Firebase (for Google Sign-In) | ☐ |
| 6 | Firebase Auth: Email/Password provider **enabled** | ☐ |
| 7 | Firebase Auth: Google provider **enabled** | ☐ |
| 8 | **Web client ID** from Google provider noted down | ☐ |
| 9 | Cloud Firestore database created (test mode, free-tier region like `us-central1`) | ☐ |
| 10 | Cloud Storage enabled (test mode) | ☐ |
| 11 | Firebase CLI installed, `firebase login` done | ☐ |
| 12 | `firebase init functions` completed (`functions/` folder exists) | ☐ |
| 13 | OpenAI API key stored as Firebase secret (`firebase functions:secrets:set OPENAI_API_KEY`) | ☐ |

---

## Step 10 — What to Share with Me

Once you've completed the above, tell me:

1. **Your exact package name** (e.g., `com.voicemind` or `com.voicemind.ai`).
2. **The Web client ID** from Firebase Google Sign-In provider.
3. **Where the Android project lives** — is it in this folder directly, or a subfolder (e.g., `android/`)?
4. **Min SDK** preference — API 26 (broader reach) or API 28+ (cleaner MediaRecorder pause/resume).
5. Confirm: **`functions/` folder exists** and **OpenAI secret is set**.

With that information, I'll:
- Set up all Gradle dependencies (Firebase, Compose, Hilt, Retrofit/OkHttp, etc.)
- Configure the `google-services` plugin
- Wire up the theme, colors, and glass composables from the Style Guide
- Build the full MVVM architecture (auth → home → recording → checklist → folders → settings)
- Implement both sign-in methods (email/password + Google)
- Write and deploy the Cloud Functions (transcription, auto-title, action-item extraction)
- Build the recording pipeline that calls those functions

---

## Troubleshooting

### "SHA-1 not found" when running signingReport
Make sure you're running the command from the root of your Android project (where `gradlew` lives). On Mac you may need `chmod +x gradlew` first.

### Google Sign-In fails with error 10 or 12500
This almost always means the SHA-1 in Firebase doesn't match your debug keystore. Re-run `./gradlew signingReport`, copy the SHA-1, and update it in Firebase Console → Project Settings → Your App → SHA certificate fingerprints.

### Firestore / Storage permission denied
If you're in test mode, check that the 30-day window hasn't expired. Go to Firestore → Rules and extend or update the rules.

### "No matching client found" at build time
The package name in `google-services.json` must exactly match the `applicationId` in your `build.gradle.kts`. If they differ, re-register the app in Firebase with the correct package name and re-download the JSON.

---

*Once all boxes are checked, come back and I'll start writing code immediately.*
