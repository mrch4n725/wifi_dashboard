# Test report

## Milestone 1 implementation

- Ran `./gradlew --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug` successfully with JDK 17 and Android SDK 36.
- **Unit tests:** 5 passed, 0 failures, 0 errors (Wi-Fi channel/band mapping and byte-counter formatting).
- **Android lint:** passed with four dependency-update advisories. The newest suggested Core and Lifecycle versions require Android API 37 / AGP 9.1, so the project remains pinned to tested versions compatible with compile SDK 36 / AGP 8.13.2.
- **Debug APK:** assembled and ZIP-validated at `app/build/outputs/apk/debug/app-debug.apk` (17,907,589 bytes; package `com.wifidashboard`; min SDK 26, target SDK 36).
- **SHA-256:** `9b4827be2270f6ffde761d1ce59c46ecb55e0f30d75735f47528eeca382493cb`
- **Workflow YAML:** parsed locally; GitHub Actions has not yet run because the changes have not been pushed.
- **Physical-device validation:** pending; no device is available in the current workspace.

CI also validates APK existence, calculates a checksum, and publishes the APK artifact. A successful build does not replace checking permission behavior and reported values on representative Android versions.
