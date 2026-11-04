# Architecture

## Application layers

- **Compose UI (`ui`)** renders Dashboard, Devices, Router, Wi-Fi, and Settings. Unavailable values are shown as unavailable rather than inferred.
- **Android network inspection (`network`)** reads the active `ConnectivityManager` network, `LinkProperties`, and Wi-Fi information when Android exposes it. The Android default IPv4 gateway is used to prefill the editable router address.
- **Local discovery (`discovery`)** combines cached Android neighbors, bounded active TCP connection attempts, selected mDNS browsing, and SSDP/UPnP on the active interface. Each reports an observation source and discovery progress.
- **Subnet selection (`IPv4Subnet`)** defaults to an address/prefix exposed by Android; a persisted optional subnet can be selected in Settings. Either way it requires the handset address to belong to the subnet, allows only RFC1918/link-local space, excludes network/broadcast addresses, and caps at 1,024 usable hosts.
- **Active TCP discovery (`ActiveTcpScanner`)** tries a fixed list of 11 common TCP ports with normal `Socket.connect`, sockets bound to the active Android `Network`, a bounded per-connect timeout, a configurable worker count and overall limit, and coroutine cancellation checks. A stale snapshot or changed active network skips active scanning rather than probing a previous subnet through a different network. Results indicate only an accepting TCP port at scan time. The scanner is not Nmap, port-state fingerprinting, raw-packet discovery, UDP scanning, or traffic capture.
- **SSDP (`SsdpDiscovery`)** sends bounded UPnP M-SEARCH queries through the active interface, accepts responses only from the selected subnet, and labels a device Sky Q only when response headers contain matching Sky Q identifiers. It does not fetch untrusted device-description URLs.
- **mDNS (`MdnsDiscovery`)** retains the Android NSD API with a fixed set of service types, a 12-second browse, a result cap, multicast lock lifecycle, and warnings for browse/resolve failures.
- **Persistent inventory (`storage`)** stores device identities, labels/groups, first/last observation times, and observation history in Room. A unique recent IP/hostname match may reconcile weak observations or merge a weak identity with one MAC identity within two minutes. Ambiguous matches are not merged; this heuristic cannot reliably resolve randomized MACs or address reuse.
- **OpenWrt adapter (`router.openwrt`)** performs authenticated JSON-RPC calls to rpcd/ubus, enumerates methods, and reads only advertised router data. It reads model/firmware, interfaces, DHCP leases, and advertised hostapd wireless clients. This is the only implemented router API provider; the Android default gateway is not assumed to be OpenWrt.
- **OpenWrt Internet access controller** changes only its named `wifi_dashboard_block_<mac>` UCI firewall rule. It checks pending edits and standard LAN/WAN zones, previews and checks state again, applies with automatic rollback, verifies configuration, then confirms or attempts recovery. It controls LAN-to-WAN forwarding only; no same-LAN isolation or Wi-Fi disconnection is implied.
- **Router credentials (`RouterCredentialStore`)** encrypt opt-in saved credentials with AES-GCM and Android Keystore keys scoped to the endpoint. Session tokens remain in memory. HTTP requires acknowledgement; HTTPS keeps platform TLS verification and redirects are not followed.
- **Discovery settings (`DiscoverySettingsStore`)** persist active scan concurrency, TCP timeout, and maximum active scan duration; the scanner consumes these values on the next scan.
- **Administration audit (`storage`)** records policy outcomes locally. The Room v1-to-v2 migration adds the audit table without replacing inventory.

## Platform and measurement boundaries

Android app networking provides ordinary TCP connects and multicast APIs, but this implementation does not assume root, raw sockets, Wi-Fi monitor mode, or unrestricted packet capture. Android may hide cached neighbor information and restrict Wi-Fi identifiers or multicast discovery. Active scans currently support IPv4 only, are limited to the active handset subnet, and may miss hosts that do not accept any scanned TCP port.

`TrafficStats` counters are handset-wide since-boot totals. They are not per-device, per-network, or router-wide measurements. Hostapd may report per-client counters on some OpenWrt versions, but those values are not currently converted to rates or persisted as traffic history. A TCP port scan is not a connection-tracking log.

The app does not set privileged Wi-Fi configuration. Router firewall changes are conditional and limited to the app-owned policy. UCI read-back confirms that the router saved the requested rule, not that packets from the device are blocked. Custom firewall zones, missing methods/ACLs, conflicting policy names, pre-existing staged firewall edits, and unsupported identity data fail closed before mutation. Without an identified router and documented local management API, other router administration is unsupported.

Observations are not automatically “online”. Only a router-reported current client list can provide a stronger connection-state source. A device last observed by mDNS, TCP, or the neighbor cache may since have disconnected.

## Validation and evolution

Unit tests use local sockets, representative SSDP responses, Room in-memory persistence, and mocked ubus responses. Real Android, LAN, Sky Q, and router testing remains necessary to confirm multicast behavior, client inventory completeness, and policy enforcement.

Future router providers must use documented APIs and explicit capability discovery. Router mutations must preserve unrelated configuration and use preview/confirm/apply/verify, rollback where practical, and an audit record. Unsupported capabilities must remain visible instead of simulated.
