# Implementation status and initial audit

## Audit scope and latest release

The repository is a small Kotlin/Compose Android application, with its app, Room persistence, router adapter, tests, and CI workflow reviewed. The latest public GitHub release at audit time is [v0.3.0](https://github.com/mrch4n725/wifi_dashboard/releases/tag/v0.3.0), published with a debug APK and SHA-256 file. Its release notes describe read-only OpenWrt wireless inspection and transactional SSID rename; the local checkout also contains subsequent OpenWrt device Internet block/restore work. GitHub CLI authentication is unavailable here, so no remote branch or release mutation was attempted.

## Root-cause report

### Why discovery missed connected devices

Before this change, the Devices action:

1. Read `/proc/net/arp` only when Android exposed it. That file is the kernel's cached IPv4 neighbor table; it is not populated by a subnet sweep and can omit sleeping, quiet, or never-contacted devices.
2. Browsed only six DNS-SD service types (`_http`, `_ipp`, `_airplay`, `_googlecast`, `_smb`, `_ssh`). mDNS finds devices advertising those exact services, not every LAN client.
3. Had no TCP probes, SSDP/UPnP, active ARP/neighbor solicitation, reverse-name resolution, or periodic scan. A device without a matching mDNS service and an existing ARP entry was therefore invisible.
4. Persisted observations, but did not make their presence status authoritative. Old observations stayed in the list; the detail screen explicitly said connection state was not authoritative. This avoided a false “online” claim but gave no clear age-based state.

The router client list is more authoritative, but it was only read from an authenticated OpenWrt session when the user connected or refreshed that screen. It is not a generic Android router API.

### Why router connection assumed OpenWrt

- Direct local rpcd/ubus JSON-RPC transport with bounded response size, finite timeouts, disabled redirects, and default Android TLS certificate/hostname validation.
- Strict router-origin validation; HTTPS by default. Cleartext HTTP is only accepted after an explicit in-app user acknowledgement.
- Authenticated `session.login`, per-session calls, explicit `session.logout`, and capability detection via `list`. Unsupported methods are not called; router and method errors remain visible.
- Capability-gated reads for `system.board`, `system.info`, `network.interface.dump`, `dhcp.ipv4leases`, and advertised `hostapd.*.get_clients` methods.
- Router, interface, DHCP lease, and wireless-client results are shown as router-provided data. DHCP leases retain their reported network interface; reported clients are recorded in the existing Room inventory with their discovery source.
- Optional saved credentials use AES-GCM encryption with a per-router Android Keystore key; plaintext passwords and session tokens are not logged or persisted.
- Robolectric protocol tests cover endpoint checks, login/capability discovery, returned router data, unsupported methods, and ubus status failures.
Before 0.4.0, the UI defaulted to a fixed address and assumed the `root` username. The updated UI can prefill Android's detected IPv4 gateway and leaves the username blank. OpenWrt remains the only supported API provider; no router model/API was specified for a second documented adapter.

## Milestone 4 — Transactional device internet access controls

**Status: implemented and build/test-verified; real-router validation remains pending.**

- Added capability-gated OpenWrt UCI preview and apply operations. The adapter requires the needed `uci` read/write/transaction calls, maps the DHCP-reported client interface to exactly one firewall zone, and requires a `wan` zone plus forwarding from the client zone to WAN.
- Block creates one deterministic named app-owned `REJECT` rule for a router-reported MAC and moves only that rule to the start of the firewall section order. Restore deletes only that app-owned rule. IP-only identities are not accepted.
- Each change re-reads firewall configuration before mutation, refuses pre-existing staged changes or conflicting app-owned names, applies with a 45-second rollback window, checks the resulting UCI configuration, and confirms only if it matches. The status is `APPLIED`; the API cannot verify the target device's real internet connectivity.
- Added an explicit action preview/confirmation, interface-aware client controls in the OpenWrt screen, randomized-MAC and stale-observation explanations, and a local Room audit trail for requested/final outcomes. A later inventory refresh failure no longer changes a successfully applied operation into a failed audit result.
- Added a Room 1→2 migration for audit records. Router ACLs may deny UCI operations; custom zones and nonstandard policy layouts are unsupported and fail closed.
- Verification: 21 unit tests passed; Android lint and debug APK assembly passed. Lint reported seven dependency-version advisories. Physical Android/OpenWrt testing remains pending.

## Milestone 5 — Wireless/router configuration

**Status: initial SSID management slice implemented; automated execution and real-router validation remain pending.**

- Reads OpenWrt UCI wireless access-point settings when `uci.get` is advertised; displays SSID, mode, encryption, radio, channel, and disabled state without reading or displaying wireless keys.
- Allows renaming only an enabled, router-reported AP interface. The UI requires review and explicit confirmation; the adapter validates the SSID, refuses any pre-existing UCI edits, rechecks the configuration fingerprint and AP identity, applies with a 45-second rollback window, reads the SSID back, and confirms only on an exact match.
- Records requested and final outcomes in the local router-action audit without persisting SSID values. The saved configuration check does not verify that clients can reconnect; changing an SSID may interrupt the management connection.
- Protocol tests cover wireless settings discovery, successful read-back/confirmation, rollback on a mismatch, pending UCI edits, and AP-only restrictions. The focused OpenWrt adapter test class and debug APK assembly passed using an isolated JDK 21 and Android SDK 36 under `/tmp`; physical OpenWrt validation remains pending.
- Password, encryption, channel, radio, DHCP/DNS, and broader router settings remain read-only or unsupported. Physical OpenWrt validation is still required before treating this slice as verified.

### Existing feature reality

| Area | Audited behavior |
|---|---|
| Android dashboard / Wi-Fi details | Functional Android `ConnectivityManager`, `LinkProperties`, and permission-aware Wi-Fi reads. `TrafficStats` is handset-wide only. |
| Inventory / labels / history | Functional Room observations, local names/groups, and audit entries. Before 0.4.0, weak IP/hostname identities could duplicate later MAC identities. |
| mDNS / Android neighbor table | Functional but narrow/passive; neither is a complete client list. |
| OpenWrt router inspection | Authenticated ubus transport, method discovery, model/firmware, interfaces, DHCP leases, advertised hostapd clients, and wireless configuration. Router/hardware validation is pending. |
| OpenWrt administration | Transactional app-owned LAN-to-WAN firewall rule and SSID rename with preview, confirmation, rollback attempt, configuration verification, and local audit. It does not isolate LAN traffic, disconnect Wi-Fi, or prove packet enforcement. |
| Traffic, ports/NAT, generic administration, network/router diagnostics | Not implemented. No per-device traffic history, live connection-tracking view, port-forwarding manager, VLAN isolation, QoS, schedules, or exported diagnostic report exists. |
| Settings | Previously informational only. 0.4.0 adds persisted active-scan concurrency, TCP timeout, and scan-duration controls. Other requested preferences remain unimplemented. |

## Milestone 1 — discovery foundation

**Status: implemented in source and unit-tested; real Android/home-network validation remains required.**

- User-triggered IPv4 TCP-connect scan of the active Android Wi-Fi/Ethernet subnet. The subnet defaults to Android's active link prefix; an optional persisted CIDR can be selected in Settings. The handset's observed address must belong to that CIDR; only RFC1918 or link-local ranges are accepted. Sockets are bound to the active Android `Network`, and a mismatch between the current network and displayed snapshot skips active scanning. Networks larger than 1,024 usable hosts, prefixes outside `/8`–`/30`, external ranges, and non-local ranges are rejected.
- Bounded TCP connectivity/service checks for 11 common ports. Default concurrency is 48 hosts, connect timeout 300 ms, and active scan limit 30 seconds; Settings persistently configures concurrency 1–64, timeout 100–1,000 ms, and duration 5–60 seconds. Progress is reported, results appear during the scan, and the scan can be cancelled.
- mDNS browsing remains bounded to the existing selected service types and 12-second browse period.
- SSDP/UPnP M-SEARCH queries `ssdp:all`, root device, media server, and media renderer targets on the active interface. Response source addresses outside the selected subnet are ignored. Sky Q is explicitly labelled when SSDP `SERVER`, `ST`/`NT`, or `USN` headers identify `SkyQ` / `Sky Q`.
- Cached Android neighbor entries are read before and after the active scan so successful TCP connects can populate the neighbor cache on Android versions that expose it. Authenticated OpenWrt DHCP/wireless client observations remain an additional source.
- Recent observations sharing IP/hostname with exactly one identity are reconciled for two minutes, including unambiguous weak-to-weak and weak-to-MAC observations. Ambiguous matches are left separate; when a MAC becomes available, a single matching weak identity is merged into it. This does not solve MAC randomization, shared hostnames, reused addresses, or historical duplicate cleanup.
- Devices continue to be shown as observed identities, not currently online devices. Router-reported connected clients are the strongest available current-connection signal.

### Nmap feasibility decision

Nmap is not categorically impossible without root: its [TCP connect scan (`-sT`)](https://nmap.org/book/man-port-scanning-techniques.html) uses the normal socket `connect` API and is specifically available when raw-packet privileges are unavailable. Its raw-packet methods and many local ARP/neighbor discovery techniques require privileges that an ordinary Android app cannot assume; see Nmap's [host discovery documentation](https://nmap.org/book/man-host-discovery.html). Shipping and maintaining Android-native Nmap binaries would add ABI, packaging, lifecycle, and privilege complexity without giving this non-root app the raw-packet capabilities requested. The bounded Kotlin TCP-connect scanner therefore implements the useful unprivileged subset directly. It is not full Nmap: no OS fingerprinting, raw ICMP/ARP, UDP port scan, arbitrary scripts, or complete host discovery. Physical Android, LAN, Sky Q, and OpenWrt validation remains pending.

## Milestone 2 — router integration

**Status: partial; OpenWrt only.**

- Android default gateway is shown as the initial IPv4 router address when available; the address remains editable. The username is no longer assumed to be `root`.
- OpenWrt rpcd/ubus authentication and capability enumeration are implemented. The app distinguishes failed transport, authentication/RPC errors, unsupported methods, and limited administrative operations in its UI/results.
- Opt-in credentials use Android Keystore AES-GCM. HTTPS retains platform certificate/hostname verification; cleartext HTTP requires explicit acknowledgement.
- There is still no provider abstraction or non-OpenWrt adapter, router fingerprinting for arbitrary vendors, multi-router profile manager, or generic API. The actual home-router model/API is unknown. A generic browser login will not be represented as router administration.

## Milestone 3 — network visibility

**Status: not implemented beyond limited router-provided client details.**

- OpenWrt hostapd client details may include radio signal and RX/TX byte counters, but the app does not calculate rates, totals, daily/monthly usage, rankings, or history from them.
- Active TCP results only identify selected ports that accepted a connection during this scan. They are not a record of live connections or router NAT state.
- No UDP scan, physical switch-port mapping, connection tracking, NAT/forwarding view, DNS/DHCP configuration diagnostics, or router-wide traffic monitor exists.
- Android `TrafficStats` remains explicitly handset-wide and is never labelled network-wide.

## Milestone 4 — administration

**Status: narrow OpenWrt Internet forwarding control only; real enforcement validation pending.**

- An app-owned, per-MAC firewall rule can block/restore LAN-to-WAN forwarding when OpenWrt advertises the required UCI transaction methods and standard zones.
- The flow previews and reconfirms router state, uses automatic rollback, verifies UCI configuration, attempts recovery on failure, and writes a local audit record.
- It does not block device-to-device LAN access, disconnect wireless clients, quarantine devices, schedule access, set QoS/bandwidth, reserve DHCP, configure VLANs, or create guest/isolated networks. App-side groups are labels, not network segmentation.

## Milestone 5 — application completion

**Status: partial.** The app retains the Dashboard, Devices, Router, Wi-Fi, and Settings destinations. Settings now persist scan controls; the requested traffic, ports/connections, diagnostics, and other settings pages are not implemented rather than shown as decorative placeholders. No exported diagnostic report exists.

## Validation boundaries

- Automated tests cover subnet limits, TCP connect probing, SSDP/Sky Q header parsing, settings persistence, identity merging/ambiguity, and the existing OpenWrt/Room behavior.
- No physical Android device, Sky Q box, home LAN, or supported router was available. Discovery completeness, multicast behavior, OpenWrt packet-level enforcement, and Android-version permission behavior are **not hardware-verified**.
- A successful unit test/build is not a pass of the user's real-device acceptance tests. See [TEST_REPORT.md](TEST_REPORT.md) for exact executed commands and environment limitations.
