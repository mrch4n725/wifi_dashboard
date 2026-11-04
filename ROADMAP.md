# Roadmap

Milestones are not complete until implementation, automated tests, and relevant physical-device/router validation are all done.

1. **Android foundation and Wi-Fi inspection** — implemented in source and unit tested; Android hardware permission/value validation remains pending.
2. **Discovery foundation (v0.4.0)** — bounded local IPv4 TCP-connect scanning, selected mDNS, SSDP/UPnP Sky Q identification, cached Android neighbors, OpenWrt DHCP/wireless clients, progress/cancellation, persistent scan settings and optional subnet selection, and recent identity reconciliation implemented. Real home-network comparison remains pending; IPv6 active scanning, UDP probing, full Nmap, and authoritative online state are not implemented.
3. **OpenWrt integration and controls** — authenticated rpcd/ubus sessions, capability detection, router/interface information, DHCP leases, hostapd clients, transactional Internet block/restore, and transactional SSID rename are implemented. Real-router validation remains pending. Other router APIs are unsupported; gateway detection/prefill is not API detection.
4. **Network visibility** — remaining: per-device rates/totals and histories; router-wide traffic; physical ports; TCP/UDP connection tracking; NAT/port-forwarding rules; DNS and DHCP configuration diagnostics. Existing hostapd byte counters are not yet processed as network analytics.
5. **Administration and segmentation** — partial OpenWrt Internet forwarding block/restore exists. Still needed where documented router capabilities allow: Wi-Fi disconnect, scheduled access, QoS, DHCP reservations, quarantine, VLAN/guest network and actual client isolation. App-side groups are labels, not segmentation.
6. **Diagnostics and completion** — remaining router/network diagnostic suite and redacted export; eight-section administrator navigation; router profiles; device/traffic history retention, notifications, theme, local data management, and the rest of persistent settings.

Cross-cutting requirements: no mandatory cloud backend; credentials remain Keystore-encrypted; preserve TLS validation; do not overwrite unrelated router policy; detect capabilities, clearly identify data source/time/reliability, and do not claim physical validation based on compilation or mocks.

## Real-device validation still required

1. On Android over a home Wi-Fi network, verify the displayed gateway, local subnet, permission behavior, mDNS/SSDP multicast, cancellation, and scan duration.
2. Compare cached neighbors, active TCP results, mDNS, SSDP, and router client results independently against the router's client table.
3. Check Sky Q discovery and devices that advertise no mDNS service but respond to TCP.
4. Confirm detected TCP services against the devices themselves. The scanner can miss devices that reject all probed ports.
5. On a supported OpenWrt router, verify advertised DHCP/wireless methods and test block/restore on a lab device, including recovery and packet-level effect.
6. Record unsupported router capabilities; do not infer physical ports, traffic history, connection tracking, or VLAN isolation when the API does not expose them.
