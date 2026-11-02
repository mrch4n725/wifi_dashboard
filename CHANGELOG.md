# Changelog

## Unreleased

- Initialized a Kotlin/Compose Android application with a live network dashboard and Wi-Fi detail view.
- Added permission-aware Android network inspection, honest unavailable-value handling, and clearly labeled device-wide traffic counters.
- Added unit tests and Android GitHub Actions build/artifact workflow.
- Added a Room-backed persistent device inventory with observation history and custom names/groups.
- Added bounded, user-triggered mDNS discovery and passive reads of Android's cached neighbor table; no subnet sweep or host probes.
