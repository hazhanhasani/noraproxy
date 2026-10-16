# NoraProxy 0.2.18 — Cross-platform UI refresh

## Design tokens

- Background `#090E1B`, primary surface `#121F32`, raised surface `#20364E`.
- Polar blue `#78B8FF` for actions and warm peach `#FFAF85` for highlights.
- Mint `#6CE5C2` communicates verified active connectivity.
- Shared NoraProxy brand icon, navigation labels and status hierarchy on Android and Windows.

## UX

- Android: responsive single-screen power dashboard, redesigned country cards, real route GUID selection, genuine ping values and quota metrics.
- Android: on very small displays, secondary country carousel yields space to the connection controls and quota.
- Windows 0.1.4: updated WPF card design and tab navigation; safe DPAPI storage and manual verified update remain available.
- Windows preview still lacks a validated Xray/TUN runtime; power control remains intentionally unavailable until the engine ships.

## Fixes

- Only successfully refreshed subscription groups become the automatically selected import group.
- Newly created subscriptions are fetched exactly once by the import coordinator.

## Validation

- Android native runtime compilation, unit tests and signing checks: `.github/workflows/native-vpn.yml`.
- Windows build, import and updater tests, install/uninstall smoke and UI parity checks: `.github/workflows/windows.yml`.
