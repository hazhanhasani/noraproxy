# NoraProxy 0.2.20 — Teal Connect Dashboard

## Android native dashboard

- New deep-navy/teal connection screen inspired by the supplied reference.
- Layered circular hero and enlarged power control, with native VPN state as the only authority.
- Compact Selected Location tile linked to the actual selected server GUID.
- Smart Location and Fastest Location shortcuts use the existing smart engine and measured nodes.
- Secure Network panel is **decorative**: it never invents a map position, public IP, provider or country.
- Live ping is shown only when available from native measurements.
- Remaining traffic and expiry are shown only when returned by the subscription provider.
- The displayed connection timer measures **foreground observation time**, not Xray service uptime.
- Flat bottom navigation retains all four working tabs; no dummy add-ons or speed-test screens.
- Responsive no-scroll home prioritizes power, selection and quota on smaller devices.
- Existing import, subscription refresh, TapSell, selection recovery and OTA flows are preserved.

## Build and verification

- Version: Android native `0.2.20` (code `220`).
- Regression contracts: `tests/test_nora_design_contract.py`.
- Full integration build: `.github/workflows/native-vpn.yml`, using pinned official v2rayNG.
- Review Android native build results in the pull request before merging.
