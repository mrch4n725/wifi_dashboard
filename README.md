# WiFi Dashboard

WiFi Dashboard is an Android network-administration control surface. Android reports what the handset can observe; supported routers are the enforcement point for network-wide administration. OpenWrt is the first planned router adapter.

## Current milestone

Milestone 1 establishes the Android app foundation, dark Compose dashboard, and live inspection of the Android device's active network and Wi-Fi connection. Milestone 2 adds an on-device Room inventory populated by user-triggered, read-only cached-neighbor and selected mDNS observations. It does not discover every LAN client. See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for implementation and hardware-validation status.

## Build

Open the repository in Android Studio or follow [BUILD_APK.md](BUILD_APK.md). The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Scope and permissions

The app does not extract saved Wi-Fi passwords, sweep LAN hosts, ping devices, or change router configuration. Device discovery is user-triggered, bounded mDNS service browsing plus the Android cached neighbor table; results are partial. Wi-Fi identifiers and local discovery are subject to Android Nearby devices (Android 13+) or Location (Android 12 and earlier) permission rules. Missing or restricted values are shown as unavailable, never inferred.

Router-side authoritative client discovery and controls are planned for later milestones. No cloud backend is required or planned. Android system-wide traffic totals are not per-device or router traffic.

## Development

- [Architecture](ARCHITECTURE.md)
- [Implementation status](IMPLEMENTATION_STATUS.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Test report](TEST_REPORT.md)

GitHub Actions runs unit tests, Android lint, a debug APK build, and APK checksum/artifact validation on pushes, pull requests, and manual dispatch.
