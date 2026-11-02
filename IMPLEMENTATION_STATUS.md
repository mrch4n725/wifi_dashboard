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

## Milestone 3 — OpenWrt authentication and read-only discovery

**Status: implemented in source and build-verified; validation against a real OpenWrt router remains pending.**

- Direct local rpcd/ubus JSON-RPC transport with bounded response size, finite timeouts, disabled redirects, and default Android TLS certificate/hostname validation.
- Strict router-origin validation; HTTPS by default. Cleartext HTTP is only accepted after an explicit in-app user acknowledgement.
- Authenticated `session.login`, per-session calls, explicit `session.logout`, and capability detection via `list`. Unsupported methods are not called; router and method errors remain visible.
- Capability-gated reads for `system.board`, `system.info`, `network.interface.dump`, `dhcp.ipv4leases`, and advertised `hostapd.*.get_clients` methods.
- Router, interface, DHCP lease, and wireless-client results are shown as router-provided data. DHCP leases retain their reported network interface; reported clients are recorded in the existing Room inventory with their discovery source.
- Optional saved credentials use AES-GCM encryption with a per-router Android Keystore key; plaintext passwords and session tokens are not logged or persisted.
- Robolectric protocol tests cover endpoint checks, login/capability discovery, returned router data, unsupported methods, and ubus status failures.

## Milestone 4 — Transactional device internet access controls

**Status: implemented and build/test-verified; real-router validation remains pending.**

- Added capability-gated OpenWrt UCI preview and apply operations. The adapter requires the needed `uci` read/write/transaction calls, maps the DHCP-reported client interface to exactly one firewall zone, and requires a `wan` zone plus forwarding from the client zone to WAN.
- Block creates one deterministic named app-owned `REJECT` rule for a router-reported MAC and moves only that rule to the start of the firewall section order. Restore deletes only that app-owned rule. IP-only identities are not accepted.
- Each change re-reads firewall configuration before mutation, refuses pre-existing staged changes or conflicting app-owned names, applies with a 45-second rollback window, checks the resulting UCI configuration, and confirms only if it matches. The status is `APPLIED`; the API cannot verify the target device's real internet connectivity.
- Added an explicit action preview/confirmation, interface-aware client controls in the OpenWrt screen, randomized-MAC and stale-observation explanations, and a local Room audit trail for requested/final outcomes. A later inventory refresh failure no longer changes a successfully applied operation into a failed audit result.
- Added a Room 1→2 migration for audit records. Router ACLs may deny UCI operations; custom zones and nonstandard policy layouts are unsupported and fail closed.
- Verification: 21 unit tests passed; Android lint and debug APK assembly passed. Lint reported seven dependency-version advisories. Physical Android/OpenWrt testing remains pending.

## Explicitly not implemented

Wi-Fi configuration, DHCP/DNS configuration, QoS, port forwarding, per-device/router traffic analytics, diagnostics, topology map, scheduling, and quarantine remain unimplemented. Internet access controls are not verified against a physical OpenWrt router or end-device traffic. Android-only discovery sources are partial and do not determine current connection status. The UI does not claim unsupported features work.

## Validation caveat

Local build results are recorded in [TEST_REPORT.md](TEST_REPORT.md). GitHub Actions has not run because the push was rejected without GitHub credentials. No physical Android device or supported OpenWrt router is attached, so hardware and router validation remain pending.
