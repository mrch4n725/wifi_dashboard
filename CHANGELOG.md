# Changelog

## Unreleased (v0.4.0 development)

- Added bounded, cancellable local IPv4 TCP-connect scanning, with private/link-local subnet enforcement, optional validated subnet selection, 1,024-host cap, 11 common ports, progress, and persisted scan settings.
- Added SSDP/UPnP M-SEARCH discovery for common media targets and Sky Q identification from response headers; cached Android neighbors are read before and after scanning.
- Added recent, ambiguity-aware identity reconciliation; gateway prefill and blank router username defaults.
- Documented discovery root causes, limitations, and pending physical-device acceptance validation.

## v0.3.0 — 2026-11-04

- Added capability-gated OpenWrt wireless settings inspection and SSID rename for supported AP sections, with explicit confirmation, UCI rollback/read-back, and local audit events.
- Added protocol tests for wireless settings discovery, successful SSID updates, rollback after a read-back mismatch, pending UCI edits, and AP-only restrictions.

## v0.2.0 — 2026-11-03

- Added capability-gated, previewed OpenWrt per-device internet block/restore via app-owned firewall rules and UCI timed apply/rollback.
- Parse interface-keyed OpenWrt DHCP leases and require an unambiguous client-interface-to-firewall-zone mapping with WAN forwarding before offering controls.
- Added Room-backed access-control audit events, UI confirmation, and explicit wording that router config read-back does not verify actual device connectivity.
- Initialized a Kotlin/Compose Android application with a live network dashboard and Wi-Fi detail view.
- Added permission-aware Android network inspection, honest unavailable-value handling, and clearly labeled device-wide traffic counters.
- Added unit tests and Android GitHub Actions build/artifact workflow.
- Added a Room-backed persistent device inventory with observation history and custom names/groups.
- Added bounded, user-triggered mDNS discovery and passive reads of Android's cached neighbor table.
- Added authenticated OpenWrt rpcd/ubus read-only inspection with endpoint validation, capability detection, router/interface details, DHCP leases, hostapd clients, and explicit per-method errors.
- Added opt-in Android Keystore AES-GCM credential storage, explicit acknowledgement for cleartext HTTP, and a router screen that stores router-reported client observations in the Room inventory.
- Added OpenWrt protocol tests for endpoint validation, authentication, capability discovery, returned router data, unsupported methods, and ubus errors.
