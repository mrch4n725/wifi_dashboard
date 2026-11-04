# WiFi Dashboard

WiFi Dashboard is an Android network-administration control surface. Android reports what the handset can observe; supported routers are the enforcement point for network-wide administration. OpenWrt is the first router adapter.

## Current milestone

Version 0.4.0 adds bounded, cancellable TCP-connect discovery on the active private IPv4 subnet, cached Android neighbor reads, selected mDNS browsing, SSDP/UPnP discovery (including Sky Q identification from SSDP headers), and configurable scan limits. Router-assisted DHCP and wireless client observations remain available through authenticated OpenWrt rpcd/ubus. The existing router controls include capability-gated device Internet block/restore and SSID rename for supported OpenWrt access points. See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the audit, scope, and limitations.

Router-account permissions and advertised capabilities determine which actions are available; signing in does not grant Android root or universal router privileges. Device Internet blocking does not provide same-LAN isolation or Wi-Fi disconnection. No physical router or Android device was available for acceptance validation.

## Build

Open the repository in Android Studio or follow [BUILD_APK.md](BUILD_APK.md). The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Scope and permissions

The app does not extract saved Wi-Fi passwords, use raw packet capture, or claim complete network visibility. Active scanning is user-triggered, bound to the selected Android `Network`, and limited to a subnet containing the handset's current private/link-local IPv4 address, at most 1,024 hosts, and 11 common TCP ports. It uses ordinary TCP `connect` calls, not ICMP/raw ARP, UDP scanning, Nmap fingerprinting, or traffic capture. Subnet, scan concurrency, per-connection timeout, total active-scan limit, and cancellation are exposed in Settings. Android multicast/mDNS behavior and router filtering can limit results.

Router discovery does not imply router API availability. The router form defaults to Android's detected IPv4 gateway when available, but the only implemented provider is OpenWrt rpcd/ubus; no generic or undocumented web-login API is attempted. Username is not assumed to be `root`. Router capabilities are read after authentication. HTTPS uses standard platform TLS verification; cleartext HTTP requires explicit acknowledgement. Saved router credentials are encrypted with an Android Keystore AES-GCM key.

Device block/restore operations change only a named app-owned firewall rule. SSID rename is limited to a router-reported access-point section. Both flows require review and confirmation and use UCI timed rollback with configuration read-back; neither proves client connectivity after the change. Wireless passwords/encryption, radio/channel, DHCP/DNS, traffic statistics, and other administrative actions are not implemented.

Discovery results are observations, not proof of current connection state. The repository reconciles recent IP/hostname observations with one unambiguous identity, but MAC randomization, shared hostnames, reused IPs, and inaccessible router data can still create duplicate or unidentified entries. Router traffic analytics, NAT/connection-tracking views, private network segmentation, diagnostic exports, and authoritative online/offline state are not implemented. Android system-wide traffic totals are not per-device or router traffic. No cloud backend is required or planned.

## Development

- [Architecture](ARCHITECTURE.md)
- [Implementation status](IMPLEMENTATION_STATUS.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Test report](TEST_REPORT.md)

GitHub Actions runs unit tests, Android lint, a debug APK build, and APK checksum/artifact validation on pushes, pull requests, and manual dispatch.
