# WiFi Dashboard

WiFi Dashboard is an Android network-administration control surface. Android reports what the handset can observe; supported routers are the enforcement point for network-wide administration. OpenWrt is the first planned router adapter.

## Current milestone

Milestone 1 establishes the Android app foundation, a dark Compose dashboard, and live inspection of the Android device's active network and Wi-Fi connection. It reports only values supplied by Android and labels device-wide traffic counters as system-wide. See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for what is and is not implemented.

## Build

Open the repository in Android Studio or follow [BUILD_APK.md](BUILD_APK.md). The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Scope and permissions

The app does not extract saved Wi-Fi passwords, scan LAN hosts, or change router configuration in this milestone. Wi-Fi identifiers may be hidden by Android unless the relevant Nearby devices (Android 13+) or Location (Android 12 and earlier) permission is granted. Missing or restricted values are shown as unavailable, never inferred.

Router-side device discovery, inventory, and controls are planned for later milestones. No cloud backend is required or planned.

## Development

- [Architecture](ARCHITECTURE.md)
- [Implementation status](IMPLEMENTATION_STATUS.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Test report](TEST_REPORT.md)

GitHub Actions runs unit tests, Android lint, a debug APK build, and APK checksum/artifact validation on pushes, pull requests, and manual dispatch.
