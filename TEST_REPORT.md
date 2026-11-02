# Test report

## Milestones 1–3

- Ran `./gradlew --no-daemon --max-workers=1 testDebugUnitTest lintDebug assembleDebug` successfully with JDK 17 and Android SDK 36.
- **Unit tests:** 14 passed, 0 failures, 0 errors: Wi-Fi channel/band mapping, byte-counter formatting, identity selection, neighbor parsing, Room persistence, and four OpenWrt endpoint/protocol/capability tests.
- **Android lint:** passed with seven dependency-version advisories and no reported errors.
- **Debug APK:** assembled and ZIP-validated at `app/build/outputs/apk/debug/app-debug.apk` (17,949,537 bytes; package `com.wifidashboard`; min SDK 26, target SDK 36).
- **SHA-256:** `bf59a2942bcee09de30bbaee07dc120e2fd8fc7dd4ce529ba7957d9bdd241d0a`
- **Workflow YAML:** parsed locally; GitHub Actions has not yet run because the changes have not been pushed.
- **Physical-device/LAN/router validation:** pending; no Android device or OpenWrt router is available in the current workspace. Android discovery behavior and real rpcd response compatibility are therefore not hardware-verified. Protocol tests use representative mocked JSON-RPC responses.

CI also validates APK existence, calculates a checksum, and publishes the APK artifact. A successful build does not replace checking permission behavior and reported values on representative Android versions.
