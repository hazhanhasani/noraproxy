# Hiddify-inspired capability parity: NoraProxy

Updated: 2026-11-05. Target Android candidate: 0.2.24.

## Scope and license boundaries

NoraProxy is an Android-first Xray VPN client using a pinned, GPL-3.0 v2rayNG
runtime with a custom Kotlin/Compose interface. Hiddify is a separate
Flutter/Hiddify-Core/Sing-box multi-platform product. This is a **feature study**,
not a code port. Do **not** copy Hiddify source or redistribute Hiddify UI
assets: its repository LICENSE.md includes extra commercial, branding,
attribution, fork and release restrictions that require independent legal review.
NoraProxy's existing upstream GPL obligations continue to apply.

## Current feature matrix

| Capability | NoraProxy Android | Notes |
| --- | --- | --- |
| VLESS / VMess / Trojan / Shadowsocks / Reality | Native Xray | Subject to actual server/device QA |
| TUN and Android VPN permission | Native runtime | Uses Android VpnService |
| Subscription URL and raw share links | Implemented | Raw imports have separate local group |
| Subscription groups and per-country selection | Implemented | Persist in native MMKV |
| Measured delay and smart location selection | Implemented | No synthetic latency |
| Optional automated recovery | Implemented | Conservative; may restart connection |
| Quota / expiry | Implemented | Provider HTTP headers; missing is unknown |
| Provider portal / support links | Added in 0.2.24 | Only validated HTTPS URLs shown |
| Provider update interval | Displayed in 0.2.24 | Advisory header only; **not** a scheduler |
| Per-app proxy/bypass | Exposed in 0.2.24 | Existing native v2rayNG activity |
| Routing profiles and rules | Exposed in 0.2.24 | Existing native v2rayNG activity |
| DNS/network controls | Exposed in 0.2.24 | Existing native v2rayNG settings activity |
| Logs and backup/restore | Exposed in 0.2.24 | Existing native v2rayNG activities |
| QR scanner and gallery | Implemented | Camera and gallery imports |
| Persian custom home, location and profile UI | Implemented | Remaining advanced activities still upstream UI |
| Signed in-app Android updates | Implemented | Verify signature, package, digest and version |
| Hysteria2, TUIC, WireGuard, SSH parity | Not guaranteed | Needs compatible runtime and interoperability tests |
| Clash/Clash.Meta and Sing-box JSON/YAML profiles | Not implemented as general import | Must not silently claim to import |
| Light theme, dynamic colors | Not implemented in Nora custom shell | Current theme is dark |
| Full Android advanced-screen visual unification | In progress | Advanced activities still use upstream UI |
| Windows VPN connection | Not implemented | Current WPF executable is preview only |
| iOS, macOS, Linux | Not implemented | Would require dedicated clients |

## Progressive delivery sequence

### Phase A — Safe discoverability (0.2.24)

- Expose the existing native per-app proxy, routing rules, DNS/network options,
  logcat and backup/restore via a coherent Advanced Connection card in Settings.
- Preserve the original backend activities and their tested preference paths.
- Parse optional `profile-web-page-url`, `support-url`, and
  `profile-update-interval` headers alongside `subscription-userinfo`.
- Reject external actions with malformed or non-HTTPS provider URLs.
- Keep missing quota/expiry values unknown; avoid bogus unlimited or zero labels.
- Do not alter tunnel behavior, MMKV schemas, Android permissions, or app signature.

### Phase B — Full Nora-branded advanced interface

- Replace the upstream activity styling with dedicated NoraProxy Compose screens,
  retaining the same underlying SettingsManager and MMKV state.
- Make all advanced controls searchable, accessible, and responsive with Persian RTL.
- Add contextual explanation, persisted filters and safe confirmation for disruptive
  settings, including state restoration on process death.
- Add light/dark/automatic theme modes without overriding Android system preferences.

### Phase C — Protocol and platform capability

- Evaluate a separate **independently integrated** Sing-box runtime adapter
  alongside Xray; compare supported features, Android VpnService integration and
  license compliance. Never run two competing Android VPN services simultaneously.
- Implement parser interoperability for Clash, Clash.Meta, and Sing-box with
  secure offline test fixtures; reject unsupported inputs with actionable errors.
- Add Hysteria2/TUIC/WireGuard/SSH only after end-to-end runtime verification.
- Implement desktop tunneling, DNS safety, signed update distribution and
  cross-platform parity in separate tracked tasks.

## Required test gate

1. `NoraSubscriptionUsageTest` including extra metadata and hostile URL cases.
2. All native Android unit tests and reproducible pinned upstream build.
3. Real Android: VPN connect/disconnect + route selection + cellular/Wi-Fi handover.
4. Enter advanced per-app, routing, DNS/network, logs and backup screens; verify
   back navigation and preference persistence without crashes.
5. Import two subscriptions with distinct quota headers; verify no token leaks.
6. Verify provider links appear only for valid HTTPS URLs and open on explicit tap.
7. Test compact screens, font scaling, Persian RTL, system bars and dark-theme contrast.
8. Check release signing, APK integrity and verified in-app update on physical device.

**Release policy:** Do not mark the candidate production-ready or merge into
`main` until the CI build and on-device VPN validation are completed.
