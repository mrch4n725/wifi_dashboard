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

## Milestone 2 — LAN discovery and persistent device inventory

**Status: implemented in source and build-verified; validation on a real Android device/LAN remains pending.**

- Room database for devices and observation history, with transactional insertion and persistent custom names/groups.
- Robolectric-backed in-memory Room test verifies stable identity, label/group persistence, first/last-seen values, IP changes, and observation history.
- Stable identity preference order: valid MAC, then hostname, then IP; confidence is shown and weak identities may be duplicates.
- User-triggered 12-second mDNS browsing of a bounded list of advertised service types, capped at 128 unique service advertisements.
- Reads only Android's already-cached ARP neighbor table when accessible; it does not generate ARP requests.
- Per-device detail page with addresses, observed source/services, timestamps, identity confidence, labels/groups, and history.
- No automatic discovery loop, subnet scan, ping, or TCP port probing.

## Explicitly not implemented

Authoritative router client inventory, OpenWrt login/calls, firewall or device controls, Wi-Fi configuration, per-device/router traffic analytics, diagnostics, topology map, scheduling, and quarantine are not implemented. Android discovery sources are partial and do not determine current connection status. The UI does not claim these features work.

## Validation caveat

The development environment initially had no Android SDK configured; the SDK was installed and local build results are recorded in [TEST_REPORT.md](TEST_REPORT.md). GitHub Actions has not run because the push was rejected without GitHub credentials. No physical Android device or supported OpenWrt router is attached, so hardware and router validation remain pending.
