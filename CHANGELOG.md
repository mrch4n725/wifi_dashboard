# Changelog

## v0.2.0 — 2026-11-03

- Added capability-gated, previewed OpenWrt per-device internet block/restore via app-owned firewall rules and UCI timed apply/rollback.
- Parse interface-keyed OpenWrt DHCP leases and require an unambiguous client-interface-to-firewall-zone mapping with WAN forwarding before offering controls.
- Added Room-backed access-control audit events, UI confirmation, and explicit wording that router config read-back does not verify actual device connectivity.
- Initialized a Kotlin/Compose Android application with a live network dashboard and Wi-Fi detail view.
- Added permission-aware Android network inspection, honest unavailable-value handling, and clearly labeled device-wide traffic counters.
- Added unit tests and Android GitHub Actions build/artifact workflow.
- Added a Room-backed persistent device inventory with observation history and custom names/groups.
- Added bounded, user-triggered mDNS discovery and passive reads of Android's cached neighbor table; no subnet sweep or host probes.
- Added authenticated OpenWrt rpcd/ubus read-only inspection with endpoint validation, capability detection, router/interface details, DHCP leases, hostapd clients, and explicit per-method errors.
- Added opt-in Android Keystore AES-GCM credential storage, explicit acknowledgement for cleartext HTTP, and a router screen that stores router-reported client observations in the Room inventory.
- Added OpenWrt protocol tests for endpoint validation, authentication, capability discovery, returned router data, unsupported methods, and ubus errors.
