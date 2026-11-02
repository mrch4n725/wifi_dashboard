# WiFi Dashboard

WiFi Dashboard is an Android network-administration control surface. Android reports what the handset can observe; supported routers are the enforcement point for network-wide administration. OpenWrt is the first router adapter.

## Current milestone

The app provides Android network inspection, a Room-backed local device inventory, and authenticated OpenWrt rpcd/ubus discovery. It also includes capability-gated device internet access controls for routers exposing the required UCI methods and a firewall zone mapping for the selected client's router-reported network interface with forwarding to WAN. See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for implementation and hardware-validation status.

## Build

Open the repository in Android Studio or follow [BUILD_APK.md](BUILD_APK.md). The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Scope and permissions

The app does not extract saved Wi-Fi passwords, sweep LAN hosts, or ping devices. Android discovery is user-triggered, bounded mDNS service browsing plus the cached neighbor table; router discovery uses only methods advertised by an authenticated OpenWrt rpcd session. Results remain partial and router capabilities vary. HTTPS is used by default and platform TLS certificate validation is retained. Cleartext HTTP requires an explicit in-app acknowledgement and is not encrypted in transit. Saved router credentials are encrypted with an Android Keystore AES-GCM key.

Device block/restore operations change only a named app-owned firewall rule. They require a preview and confirmation, apply through UCI with a timed rollback window, and verify the saved UCI rule before confirming. A saved rule is not proof that the target device lost internet access. Other router configuration, traffic statistics, and administrative actions are not implemented. No cloud backend is required or planned. Android system-wide traffic totals are not per-device or router traffic.

## Development

- [Architecture](ARCHITECTURE.md)
- [Implementation status](IMPLEMENTATION_STATUS.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Test report](TEST_REPORT.md)

GitHub Actions runs unit tests, Android lint, a debug APK build, and APK checksum/artifact validation on pushes, pull requests, and manual dispatch.
