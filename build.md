cd android
./gradlew assembleRelease appDistributionUploadRelease

ANd use this for build check:

./gradlew compileDebugKotlin 2>&1

The build number (versionCode) auto-increments every time you run assembleRelease.
The current version is stored in android/version.properties.

To bump the version name for a new release (e.g. 1.0.0 → 1.1.0), edit VERSION_NAME in android/version.properties.

Step 2: Add your friend's email

In app/build.gradle.kts, replace the empty testers string with your friend's email:
testers = "yourfriend@gmail.com"

You can add multiple emails as a comma-separated string: "friend1@gmail.com, friend2@gmail.com".


Github issues command: 

```
gh issue create --title "Issue title" --body "Issue description"

gh issue create --title "" --body ""
```