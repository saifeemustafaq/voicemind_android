# Adding Testers to Firebase App Distribution

## Step 1: Add Testers in `build.gradle.kts` (Code Side)

This tells Firebase who should receive the build when you upload it.

In `android/app/build.gradle.kts`, add emails to the `testers` string inside the `firebaseAppDistribution` block:

```kotlin
firebaseAppDistribution {
    releaseNotes = "Latest build of VoiceMind"
    testers = "saifeesaifuddinq@gmail.com, saifeestudy@gmail.com, newfriend@gmail.com"
}
```

Multiple emails are comma-separated.

---

## Step 2: Add Testers in Firebase Console (Cloud Side)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project (e.g. **VoiceMind**)
3. On the left sidebar, look under **Product categories**
4. Hover over **DevOps and Engagement** — a submenu will appear
5. Under the **Testing** subsection, click **App Distribution**
6. Click the **Testers & Groups** tab at the top
7. Click **Add testers**
8. Enter the email addresses of the people you want to test (one per line or comma-separated)
9. Click **Add**

You can also create **groups** (e.g. "beta-testers") and add testers to groups. Then in `build.gradle.kts` you can use `groups = "beta-testers"` instead of listing individual emails.

---

## Step 3: Build & Upload

Run the build command:

```bash
cd android
./gradlew assembleRelease appDistributionUploadRelease
```

This will:
- Build the release APK
- Upload it to Firebase App Distribution
- Send email invitations to all listed testers

---

## Step 4: What the Tester Needs to Do

Once you upload a build, each tester receives an **email invitation** from Firebase. They need to:

1. Open the email and click **Get started** (first-time) or **Download the latest build**
2. Accept the invitation — signs in with the email they were invited with
3. Install the **Firebase App Tester** app (optional but recommended) — search "Firebase App Tester" on the Play Store
4. Enable **Install from unknown sources** on their Android device (Settings > Apps > Special app access > Install unknown apps)
5. Download and install the APK from the email link or through the Firebase App Tester app

---

## Step 5: (Optional) Add Testers via Firebase CLI

You can also manage testers from the command line:

```bash
# Add a single tester
firebase appdistribution:testers:add newfriend@gmail.com --project YOUR_PROJECT_ID

# Add multiple testers
firebase appdistribution:testers:add user1@gmail.com user2@gmail.com --project YOUR_PROJECT_ID

# Create a group and add testers to it
firebase appdistribution:group:create beta-testers --project YOUR_PROJECT_ID
firebase appdistribution:testers:add newfriend@gmail.com --group-alias beta-testers --project YOUR_PROJECT_ID
```

---

## Quick Reference

| Where | What to do |
|---|---|
| `build.gradle.kts` | Add email to `testers = "..."` comma-separated string |
| Firebase Console | **Product categories > DevOps and Engagement > Testing > App Distribution > Testers & Groups > Add testers** |
| CLI (optional) | `firebase appdistribution:testers:add email@gmail.com` |
| Tester's phone | Accept email invite, enable unknown sources, install APK |

The minimum you need to do is add the email to the `testers` string in `build.gradle.kts` and run the build command — Firebase will auto-invite them by email. The console step is optional but useful for managing testers outside of code.
