# Architecture

## Application layers

- **Compose UI (`ui`)** renders the dashboard, Wi-Fi detail, and settings destinations. It renders nullable OS observations as unavailable instead of inventing data.
- **Android network inspection (`network`)** reads the active `ConnectivityManager` network, its capabilities and `LinkProperties`, and the Android Wi-Fi connection information when the active transport is Wi-Fi.
- **Models (`NetworkSnapshot`)** are immutable snapshots used to separate Android framework access from presentation.
- **Formatting (`ui.format`)** contains pure display helpers with JVM unit tests.
- **Local discovery (`discovery`)** reads Android's cached `/proc/net/arp` neighbor table, when exposed, and browses a bounded set of mDNS service types only after the user starts a scan. It does not sweep addresses, ping hosts, or probe ports.
- **Persistent inventory (`storage`)** uses Room transactions for device identities, user labels/groups, first/last observation times, and append-only observation history.

`MainActivity` only hosts the Compose application. Network observations and Room data stay on-device. The Android `INTERNET` permission is used only for the operating system's local-network service discovery; the app has no cloud endpoint or remote service dependency.

## Platform boundaries

Wi-Fi identifiers/security can be redacted by Android and are permission-gated. IP addresses, routes, DNS, interface name, link rates and signal are reported only when Android exposes them. `TrafficStats` counters are device-wide totals since boot, not Wi-Fi-specific or per-device traffic. DHCP server information is read from the Android Wi-Fi DHCP view when available.

The app does not set privileged Wi-Fi configuration. Router-side enforcement belongs in a future router adapter and must not be simulated by Android-only state.

Neighbor entries are only the kernel's cached observations; Android may hide the neighbor table. mDNS yields only services advertised using selected service types; service instance names are kept distinct from actual hostnames. These sources are incomplete and do not provide an authoritative list of all clients. MAC-derived, hostname-derived, and IP-only identities have different confidence and may appear as duplicates when no reliable shared identifier is observable. A recent observation is not asserted to mean a device is currently connected.

## Planned evolution

OpenWrt communication should be behind a capability-aware adapter using authenticated rpcd/ubus calls and TLS validation. Secrets will use Android Keystore-backed encryption. Device controls should use preview/confirm/apply/verify and rollback-aware transactions. Router integration and enforcement are not implemented.
