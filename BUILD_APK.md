# Building the APK

## Android Studio

1. Open this repository in Android Studio and allow Gradle sync.
2. Install Android SDK Platform 36 and Build Tools 36.0.0 when prompted.
3. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Command line

Requirements: JDK 17 and Android SDK Platform 36 / Build Tools 36.0.0.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The debug APK is not a production-signed release.

GitHub Actions runs the same checks, validates the APK, calculates its SHA-256, and uploads both the APK and checksum as the `wifi-dashboard-debug` workflow artifact. No signing secrets are needed for debug builds.
