# Test report

## v0.4.0 combined-tree validation

Command:

```text
./gradlew --no-daemon --max-workers=1 --stacktrace --warning-mode all testDebugUnitTest lintDebug assembleDebug
```

Environment: OpenJDK 21.0.13, Android SDK 36, Gradle wrapper 8.14.6.

- **Unit tests:** 42 passed, 0 failures, 0 errors, 0 skipped across 12 test suites.
- **Android lint:** passed; seven existing dependency-version advisories, no lint errors.
- **Debug APK:** assembled at `app/build/outputs/apk/debug/app-debug.apk`; package `com.wifidashboard`, versionCode `4`, versionName `0.4.0`, min SDK 26, target SDK 36; 18,146,244 bytes.
- **SHA-256:** `5d9d925464adf663b8456547dff2c60b0bc6f3b19220a7932821d3d29023f7da`
- Tests include private-subnet bounds, TCP connect probing against a local test socket, mDNS observation parsing, SSDP/Sky Q header parsing, scan-settings persistence, identity merging/ambiguity, existing OpenWrt transaction behavior, Room audit persistence, and database migration.
- These are unit/protocol tests. No physical Android device, Sky Q box, home LAN, or supported router was available. Real discovery coverage, multicast/permission behavior, comparison against the router's client list, and router packet-level enforcement remain unverified.

Validation used OpenJDK 21.0.13 because the workspace default JDK 25 is not supported by the bundled Kotlin compiler. Android SDK command-line tooling emitted a non-fatal notice that it supports SDK XML through version 3 while the installed SDK contains version 4.

## Existing CI context

- GitHub Actions run [41475828296](https://github.com/mrch4n725/wifi_dashboard/actions/runs/41475828296) for commit `3b9bb44` failed before Gradle ran because `android-actions/setup-android@v3` attempted to install the retired `tools` SDK package.
- The repair already present in the checkout is committed locally in `bd6788b` and `a7979a5`: current Node-compatible actions, `setup-android@v4` requesting `platform-tools`, Ubuntu 24.04, failure report artifacts, APK ZIP validation, and an application-ID check.
- A prior `git push origin main` failed because GitHub credentials were unavailable. The repaired workflow has not been verified by a remote Actions run. No push was attempted for this development branch.

A passing local build does not replace checking discovery results and permissions on supported Android versions or checking router-reported/enforced behavior against actual hardware.
