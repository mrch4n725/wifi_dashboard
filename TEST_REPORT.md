# Test report

## Milestones 1 and 2

- Ran `./gradlew --no-daemon --max-workers=1 testDebugUnitTest lintDebug assembleDebug` successfully with JDK 17 and Android SDK 36.
- **Unit tests:** 10 passed, 0 failures, 0 errors: Wi-Fi channel/band mapping, byte-counter formatting, device-identity selection, cached-neighbor parsing, and an in-memory Room repository integration test.
- **Android lint:** passed with seven dependency-update advisories. Room 2.7.2 and AndroidX versions remain pinned to versions verified with AGP 8.13.2 / compile SDK 36.
- **Debug APK:** assembled and ZIP-validated at `app/build/outputs/apk/debug/app-debug.apk` (17,912,306 bytes; package `com.wifidashboard`; min SDK 26, target SDK 36).
- **SHA-256:** `4bf10297110206f0b48af996f9a853ce7bad27bb64c32e18044a1bd1952364cc`
- **Workflow YAML:** parsed locally; GitHub Actions has not yet run because the changes have not been pushed.
- **Physical-device/LAN validation:** pending; no Android device or controlled LAN is available in the current workspace. mDNS discovery and Android neighbor-table access are therefore build-tested, not hardware-verified.

CI also validates APK existence, calculates a checksum, and publishes the APK artifact. A successful build does not replace checking permission behavior and reported values on representative Android versions.
