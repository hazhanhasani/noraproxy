# NoraProxy VNext — Product Upgrade Specification

**Status:** design exploration / implementation branch, not production shipped.
**Targets:** Android (Jetpack Compose + pinned Xray/VpnService), Windows (WPF preview), GitHub Pages (public site).
**Design file:** https://www.figma.com/design/2a1BAOCfcPxjyNYJwCuK6T
**Interactive prototype:** MagicPath project 462528940108566528

## Why this upgrade

The previous build has a functioning Android Native VPN pipeline and a Windows-only visual preview. The new version prioritizes reliability and clarity over visual gimmicks. Windows must never imply real VPN connectivity while Xray/TUN support is incomplete.

## Design principles

1. Connection status is truthful. Derive the displayed status from the native VPN service, not from a tapped button.
2. One country represents multiple nodes; the engine chooses a healthy route. Manual selection always updates the actual selected server GUID, not just the selected card.
3. Connected, connecting, disconnecting, disconnected, subscription-empty, permission-denied, subscription-expired and network-offline need visibly distinguishable states.
4. Mobile-first. Main Android screen does not scroll; bottom navigation remains visible with system insets and gesture navigation.
5. Content is bilingual where necessary but action text, dates, and quotas are legible in Persian. Typography must not clip with Android font scaling.
6. Avoid 3D effects and decorative permanent animations. Honor reduced-motion and battery saver.
7. One shared semantic design system; Android and Windows layouts adapt to their form factor instead of blindly reproducing a mobile screen in WPF.
8. Ad inventory may fail. Failed or unavailable ads must never block VPN connection, disconnection, or accessibility navigation.

## Tokens

| Purpose | Hex |
|---|---|
| Canvas | `#081120` |
| Surface | `#12223A` |
| Raised surface | `#1D3552` |
| Border | `#365575` |
| Text | `#F4F8FF` |
| Muted | `#9BB0C9` |
| Action | `#8FC5FF` |
| Highlight | `#FFBC92` |
| Connected/verified | `#69E6BF` |

**Typography:** Persian-first readable UI. Use platform-provided typeface or bundled licensed font with verified glyph support. Minimum body 14sp, interaction touch-target 48dp, accessible contrast >= 4.5:1 for typical body text.

## Reliability and diagnostics

- Foreground resume: refresh eligible subscription groups, no overlapping MMKV writes, explicit last-refresh timestamp and partial-error feedback.
- Location search: resolve country aliases once per normalized query; preserve behavior for short/exact terms, favorites and mixed Persian/English names.
- Route telemetry: record only coarse outcome enums such as `vpn_connect_requested`, `vpn_connected`, `vpn_connect_failed`, `route_changed`, `subscription_refresh_result`, `update_result` and `ui_crash`.
- Never transmit subscription URLs, access tokens, raw node configurations, server addresses, remarks, IPs, user phone, account handles, device identifiers or sensitive error messages.
- PostHog: current connected project is an uninitialized generic project (no ingested events). Do not assume it belongs to NoraProxy. Do not embed keys or enable analytics in shipped code until the user confirms project ownership and privacy/consent settings.
- Error reports: only safe error categories, app version, OS major version, action source, and elapsed duration buckets.
- Feature flags: allow gradual rollout of optional UI features; native tunnel remains independent of network analytics and feature-flag availability.
- Always-on VPN, runtime permission, background service and revoke flow must follow Android's official VpnService lifecycle.

## Project baseline & references

- Official Android VPN lifecycle: https://developer.android.com/develop/connectivity/vpn
- PostHog Kotlin multiplatform SDK: https://posthog.com/docs/libraries/kmp
- Product visual target: Figma mockup and MagicPath working concept listed above.
- Canva account returned no available Brand Kit; a canonical NoraProxy kit should be built only after design review.
- Windows remains labeled as **Preview — no native VPN engine** until real end-to-end tunnel tests pass.

## Acceptance gates

- Zero hardcoded fake live ping or runtime connection state in production Android.
- Connection/disconnection and device permission revocation pass on Android 13-16; repeat reconnect 25x with no zombie service.
- 20 HTTPS subscriptions import without UI hang; raw configs route to Default; every subscription owns a group.
- All-country locale recognition and favorites/search are stable and performant at 1,000 nodes.
- Signed APK certificate SHA-256 matches fixed expected fingerprint; in-app update verifies official release checksum before install.
- UI screenshot regression checks for 360dp mobile, large font, tablet and desktop; RTL and touch-target audit.
- Privacy test proves crash/usage metadata cannot leak URLs, config strings, tokens, hostnames or IP addresses.
- Windows engine capability clearly disclosed, no misleading connect action.

## Delivery strategy

Use a feature branch and a reviewed pull request. Do not automatically merge or auto-release experimental UI changes. QA gates and signer validation are required before main.
