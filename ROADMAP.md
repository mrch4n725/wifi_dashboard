# Roadmap

This roadmap tracks the project direction. A milestone is complete only after implementation, tests, and validation against the relevant Android device or supported router.

1. **Android foundation and Wi-Fi inspection** — current: Compose foundation, dashboard, Android network data, tests, and CI.
2. **LAN discovery and inventory** — conservative read-only discovery, Room identities and observation history; test discovery behavior on a controlled LAN.
3. **OpenWrt authentication and read-only discovery** — authenticated rpcd/ubus sessions, capability detection, router/interface information, DHCP leases, and hostapd clients.
4. **Transactional device controls** — scoped application-owned firewall policies, preview/confirm/apply/verify, snapshots, rollback, and audit history.
5. **Wireless/router configuration** — capability-aware settings and safe application/verification for supported OpenWrt configurations.
6. **Traffic, diagnostics, and topology** — distinguish authoritative router measurements from estimates and render only observed relationships.
7. **Scheduling and advanced controls** — scheduled access, profiles, quarantine, and other supported router-side enforcement.

Cross-cutting requirements: no mandatory cloud service; protect credentials with Android Keystore-backed encryption; validate TLS; do not overwrite unrelated router policy; report unsupported capabilities honestly.
