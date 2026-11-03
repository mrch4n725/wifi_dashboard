# Test report

## Milestones 1–4

- Ran `./gradlew --no-daemon --max-workers=1 testDebugUnitTest lintDebug assembleDebug` successfully with JDK 17 and Android SDK 36.
- **Unit tests:** 21 passed, 0 failures, 0 errors, 0 skipped. Coverage includes Wi-Fi formatting, identity and neighbor parsing, Room persistence/migration, OpenWrt RPC and DHCP lease parsing, and internet access policy transactions.
- **Android lint:** passed with seven dependency-version advisories and no reported errors.
- **Debug APK:** assembled at `app/build/outputs/apk/debug/app-debug.apk` (package `com.wifidashboard`; version `0.2.0` / code 2; min SDK 26, target SDK 36; 17,928,716 bytes). This is a debug build, not production-signed.
- **SHA-256:** `e14ddd4474dc3c8ad2023882ddf8516e255ef508d1d3e7bd5407dd1deaf9feea`
- **GitHub Actions:** configured to run tests, lint, assemble the APK, and upload it as a workflow artifact on pushes and tags. The v0.2.0 tag build is pending.
- **Physical-device/LAN/router validation:** pending; no Android device or OpenWrt router is available in the current workspace. Android discovery behavior and real rpcd response compatibility are therefore not hardware-verified. Protocol tests use representative mocked JSON-RPC responses.

CI also validates APK existence, calculates a checksum, and publishes the APK artifact. A successful build does not replace checking permission behavior and reported values on representative Android versions.

## Milestone 4 — transactional device internet access controls

- Added focused unit coverage for stable app-owned policy identity, invalid MAC rejection, supported OpenWrt UCI transaction ordering, and unsupported firewall layouts; added Room audit-event persistence coverage.
- Final verification after DHCP interface parsing and interface-to-firewall-zone mapping changes: `testDebugUnitTest`, `lintDebug`, and `assembleDebug` all succeeded.
- **Unit tests:** 21 passed, 0 failures, 0 errors, 0 skipped.
- **Android lint:** passed with seven dependency-version advisory warnings and no lint errors.
- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`.
- **SHA-256:** `e14ddd4474dc3c8ad2023882ddf8516e255ef508d1d3e7bd5407dd1deaf9feea`.
- For this run, JDK 17 and Android Build Tools 35.0.0/platform 36 were installed in the workspace SDK after SDK license acceptance. Robolectric required `-Duser.home=/home/osama/Documents/Codex` because the default home directory is read-only.
- DHCP leases use the router's interface-keyed `dhcp.ipv4leases` response; access control requires the reported interface to resolve to exactly one firewall zone with WAN forwarding. The operation verifies the saved UCI rule only; it cannot verify the selected device's actual internet connectivity.
- **Router validation:** pending. No physical OpenWrt router is attached.
