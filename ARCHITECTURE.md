# Architecture

## Current application layers

- **Compose UI (`ui`)** renders the dashboard, Wi-Fi detail, and settings destinations. It renders nullable OS observations as unavailable instead of inventing data.
- **Android network inspection (`network`)** reads the active `ConnectivityManager` network, its capabilities and `LinkProperties`, and the Android Wi-Fi connection information when the active transport is Wi-Fi.
- **Models (`NetworkSnapshot`)** are immutable snapshots used to separate Android framework access from presentation.
- **Formatting (`ui.format`)** contains pure display helpers with JVM unit tests.

`MainActivity` only hosts the Compose application. The app has no Internet permission or remote service dependency in Milestone 1; inspected data stays on-device.

## Platform boundaries

Wi-Fi identifiers/security can be redacted by Android and are permission-gated. IP addresses, routes, DNS, interface name, link rates and signal are reported only when Android exposes them. `TrafficStats` counters are device-wide totals since boot, not Wi-Fi-specific or per-device traffic. DHCP server information is read from the Android Wi-Fi DHCP view when available.

The app does not set privileged Wi-Fi configuration. Router-side enforcement belongs in a future router adapter and must not be simulated by Android-only state.

## Planned evolution

Later milestones should introduce explicit `router`, `storage`, and `discovery` layers. OpenWrt communication should be behind a capability-aware adapter using authenticated rpcd/ubus calls and TLS validation. Persistence will use Room; secrets will use Android Keystore-backed encryption. Device controls should use preview/confirm/apply/verify and rollback-aware transactions. None of those modules is currently implemented.
