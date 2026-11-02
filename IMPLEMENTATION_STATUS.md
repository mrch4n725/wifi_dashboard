# Implementation status

## Milestone 1 — Android foundation and Wi-Fi inspection

**Status: implemented and build-verified; real-device validation remains pending.**

- Kotlin Android app with Compose and Material 3.
- Dashboard, Wi-Fi detail, and settings navigation.
- Active-network state and transport from Android `ConnectivityManager`.
- Wi-Fi SSID/BSSID, security (API 31+), frequency/channel/band, RSSI and available link speeds.
- Interface, IPv4/IPv6 addresses, prefixes, default routes, DNS, and available DHCP server.
- Android device-wide RX/TX byte totals, explicitly distinguished from network/device traffic.
- Android-version-aware Wi-Fi identifier permission request.
- JVM tests for Wi-Fi channel and byte formatting.
- GitHub Actions workflow for unit tests, lint, APK assembly, checksum, and artifact upload.

## Explicitly not implemented

LAN discovery, Room inventory, hostname/history tracking, router login or OpenWrt calls, firewall or device controls, Wi-Fi configuration, per-device/router traffic analytics, diagnostics, topology map, scheduling, and quarantine are not implemented. The UI does not claim these features work.

## Validation caveat

The development environment initially had no Android SDK configured and no physical Android device attached. Build and CI results must be recorded in [TEST_REPORT.md](TEST_REPORT.md) after execution; local code review does not substitute for hardware validation.
