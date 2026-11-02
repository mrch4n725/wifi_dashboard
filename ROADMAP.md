# Roadmap

This roadmap tracks the project direction. A milestone is complete only after implementation, tests, and validation against the relevant Android device or supported router.

1. **Android foundation and Wi-Fi inspection** — implemented in source and build-verified; physical-device validation remains pending.
2. **LAN discovery and inventory** — implemented in source; cached Android neighbors, bounded user-triggered mDNS, Room identities, labels/groups and observation history. Real LAN/device validation remains pending.
3. **OpenWrt authentication and read-only discovery** — implemented in source and build-verified: authenticated rpcd/ubus sessions, capability detection, router/interface information, DHCP leases, and hostapd clients; real-router validation remains pending.
4. **Transactional device controls** — initial OpenWrt block/restore slice implemented and unit/build-verified: MAC-scoped application-owned rule, LAN/WAN capability checks, preview/confirm, UCI timed rollback, config read-back, and audit history. Real-router/device validation remains pending.
5. **Wireless/router configuration** — capability-aware settings and safe application/verification for supported OpenWrt configurations.
6. **Traffic, diagnostics, and topology** — distinguish authoritative router measurements from estimates and render only observed relationships.
7. **Scheduling and advanced controls** — scheduled access, profiles, quarantine, and other supported router-side enforcement.

Cross-cutting requirements: no mandatory cloud service; protect credentials with Android Keystore-backed encryption; validate TLS; do not overwrite unrelated router policy; report unsupported capabilities honestly.
