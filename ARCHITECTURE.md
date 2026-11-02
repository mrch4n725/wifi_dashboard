# Architecture

## Application layers

- **Compose UI (`ui`)** renders the dashboard, Wi-Fi detail, and settings destinations. It renders nullable OS observations as unavailable instead of inventing data.
- **Android network inspection (`network`)** reads the active `ConnectivityManager` network, its capabilities and `LinkProperties`, and the Android Wi-Fi connection information when the active transport is Wi-Fi.
- **Models (`NetworkSnapshot`)** are immutable snapshots used to separate Android framework access from presentation.
- **Formatting (`ui.format`)** contains pure display helpers with JVM unit tests.
- **Local discovery (`discovery`)** reads Android's cached `/proc/net/arp` neighbor table, when exposed, and browses a bounded set of mDNS service types only after the user starts a scan. It does not sweep addresses, ping hosts, or probe ports.
- **Persistent inventory (`storage`)** uses Room transactions for device identities, user labels/groups, first/last observation times, and append-only observation history.
- **OpenWrt adapter (`router.openwrt`)** validates router origins and performs authenticated JSON-RPC calls through rpcd/ubus. It enumerates advertised methods before reads or controls. Device internet controls require the router to advertise the UCI methods needed for read, stage, apply, confirm, and rollback; the adapter restricts writes to one deterministic app-owned `firewall` rule for a router-reported MAC. DHCP leases retain the router-reported network interface. Before preview, that interface must map to exactly one firewall zone with forwarding to the WAN zone. The operation applies with timed rollback, reads back the UCI rule, and confirms only when the stored configuration matches. Router-reported DHCP and hostapd clients are recorded in the same Room inventory.
- **Access-control audit (`storage`)** stores local requested and final operation events with the target device, action, result, timestamp, and sanitized detail. Room schema version 2 migrates existing inventories without dropping data.
- **Router credentials (`RouterCredentialStore`)** encrypt opt-in saved usernames/passwords using AES-GCM and Android Keystore keys scoped to the router origin. Session tokens remain in memory. HTTP endpoints require an explicit user acknowledgement; HTTPS uses the platform's standard TLS certificate and hostname checks, and redirects are not followed.

`MainActivity` only hosts the Compose application. Network observations and Room data stay on-device. The Android `INTERNET` permission supports local mDNS discovery and direct router connections; the app has no cloud endpoint or remote service dependency.

## Platform boundaries

Wi-Fi identifiers/security can be redacted by Android and are permission-gated. IP addresses, routes, DNS, interface name, link rates and signal are reported only when Android exposes them. `TrafficStats` counters are device-wide totals since boot, not Wi-Fi-specific or per-device traffic. DHCP server information is read from the Android Wi-Fi DHCP view when available.

The app does not set privileged Wi-Fi configuration. Router firewall changes are conditional and limited to the app-owned policy. UCI read-back confirms that the router saved the requested rule, not that packets from the device are blocked; the UI must not display `VERIFIED` for device connectivity. Custom firewall zones, missing methods/ACLs, conflicting policy names, pre-existing staged firewall edits, and unsupported identity data fail closed before mutation.

Neighbor entries are only the kernel's cached observations; Android may hide the neighbor table. mDNS yields only services advertised using selected service types; service instance names are kept distinct from actual hostnames. These sources are incomplete and do not provide an authoritative list of all clients. MAC-derived, hostname-derived, and IP-only identities have different confidence and may appear as duplicates when no reliable shared identifier is observable. A recent observation is not asserted to mean a device is currently connected.

## Planned evolution

Future OpenWrt changes must use narrowly scoped application-owned policy, preview/confirm/apply/verify flows, snapshots, rollback, and audit history. All router capabilities must remain conditional because rpcd methods and packages differ across installations.
