```
cd android
./gradlew assembleRelease appDistributionUploadRelease
```

ANd use this for build check:
```
./gradlew compileDebugKotlin 2>&1
```

infact do this from the mem_and folder

```
cd android && ./gradlew compileDebugKotlin 2>&1 && cd ../functions && firebase deploy --only functions,firestore && cd ..
```

The build number (versionCode) auto-increments every time you run assembleRelease.
The current version is stored in android/version.properties.

To bump the version name for a new release (e.g. 1.0.0 → 1.1.0), edit VERSION_NAME in android/version.properties.

Step 2: Add your friend's email

In app/build.gradle.kts, replace the empty testers string with your friend's email:
testers = "yourfriend@gmail.com"

You can add multiple emails as a comma-separated string: "friend1@gmail.com, friend2@gmail.com".


## On-device debugging (adb)

Requires: `brew install android-platform-tools` (already installed).
Connect phone via USB with USB Debugging enabled (Settings > Developer Options).

```bash
# Check device is connected
adb devices

# Stream all app logs (filtered to VoiceMind process)
adb logcat --pid=$(adb shell pidof com.voicemind)

# Widget debugging — Glance errors and crashes only
adb logcat -s GlanceAppWidget:E AndroidRuntime:E

# Filter to Timber logs from the app
adb logcat -s timber:*

# Clear logcat before reproducing a bug, then stream
adb logcat -c && adb logcat --pid=$(adb shell pidof com.voicemind)

# Install debug APK directly to connected device
cd android && ./gradlew installDebug
```

Widgets fail silently with "Can't show content" — always use `adb logcat` to see the actual exception.

---

Github issues command: 

```
gh issue create --title "Issue title" --body "Issue description"

gh issue create --title "" --body ""
```