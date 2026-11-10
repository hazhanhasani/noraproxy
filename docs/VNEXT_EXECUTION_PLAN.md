# NoraProxy vNext — Engineering Execution Plan

**Baseline reviewed:** 2026-11-10  
**Production branch:** `main` (Android overlay v0.2.24, official v2rayNG 2.3.15 / pinned commit `57907cf354d9769200ed4717b4430d605abe0952`)  
**Current official upstream:** v2rayNG 2.3.15 (2026-11-01).  
**Policy:** no automatic merge or release before CI and physical-device acceptance.

## Current architecture

- `native-vpn/` is the production Android/Jetpack Compose overlay.
- `scripts/prepare_native_vpn.py` applies the overlay to a verified upstream Xray/VpnService revision at build time.
- `app/` is a separate legacy Kotlin companion MVP; do not mistake it for the distributable native VPN.
- `desktop/windows/` is a WPF preview and must not claim functional tunnel connectivity.
- `.github/workflows/native-vpn.yml` owns Android builds, signing and guarded publication.

## Already-open work (not yet integrated into main)

| PR | Topic | Base |
|---|---|---|
| #14 | Hiddify-inspired advanced controls | main |
| #18 | Routing and DNS editors | #14 branch |
| #19 | Themes, secure diagnostics and backup | #18 branch |
| #20 | Routing clipboard transfer and MTU | #19 branch |

These PRs form a stacked chain. Rebase and validate in order; do not independently merge a child PR into `main` before its parent.

## Phase 0 — Subscription integrity (this PR)

- One coroutine mutex serializes foreground refresh, raw/remote import and group rename/delete writes to MMKV.
- Refresh debounce starts after the previous sync **finishes**, not after it begins.
- Refresh recognizes failed/empty upstream responses, continues remaining groups and surfaces aggregate success/failure counts.
- Duplicate group IDs are de-duplicated before refresh.
- Public UI report never reveals subscription URLs, hostnames, server IPs or credentials.
- Cancellation propagates and cannot silently continue a canceled batch.

### Phase 0 acceptance

- Run `NoraForegroundRefreshTest` as part of the pinned upstream unit suite.
- Verify concurrent manual import and foreground resume do not corrupt group membership or profile lists.
- Test failed HTTPS subscriptions, empty group, raw-only group, disabled group and partial success.
- Verify 20 subscriptions with repeated foreground/background transitions remain responsive.

## Phase 1 — Route engine and subscription domain

- Add authenticated proxy diagnostics rather than equating TCP ping with actual working VPN.
- Add measured route history partitioned by physical network, bounded size and expiry.
- Preserve manual country overrides and a failover cooldown; no blind cross-country auto switch.
- Add cancelable, resumable subscription refresh with user-visible last success and per-group error state.
- Make backups/restores transactional and avoid plain-text credential exports by default.

## Phase 2 — Product UI

- Compose design system: Persian-first, accessible RTL and large-font sizes.
- Clean Home, Locations, Subscriptions and Settings navigation; preserve bottom insets.
- Add clear native states for permission required, connecting, connected, disconnecting, offline, subscription expired and service error.
- Do not claim connected status from a power gesture alone.
- Server lists grouped by subscription/location; raw configs stay in Default; users can switch manually.
- Banner assets fit their container; remotely updated banners use cache invalidation, without forcing an APK update.

## Phase 3 — Engine parity and update lifecycle

- Evaluate Hiddify v5 features one by one as compatible **product requirements**. Its sing-box-based core cannot simply be overlaid on the current Xray/VpnService stack.
- Preserve current v2rayNG 2.3.15 pinned security fixes until a newer trusted stable release exists and passes build/QA.
- Keep identical `applicationId`, versionCode monotonicity and a matching production signing certificate for trusted debug-to-release upgrades.
- Android forbids upgrading legacy debug-key APKs in place to a different release signing key; do not advertise this as solvable by changing the version number.
- Require official APK digest, package ID and signing identity verification before user-confirmed installation.
- Tapsell may be required as a configured monetization integration, but unavailable ad inventory must never prevent VPN disconnect or accessibility actions.

## Phase 4 — Release gates

- CI: pinned Android overlay compile, unit/contract tests, basic static security checks, signed APK checksum and certificate validation.
- Device QA: Android 13/14/15/16; Wi-Fi/cellular handoff; VPN permission revoke; 25 connect/disconnect cycles; background/foreground; 1,000-node list.
- Compatibility: existing subscriptions, raw profiles, groups, preferences, usage/quota metadata and signed in-place upgrade.
- UI: 360dp phones, larger font scale, gesture nav, TalkBack, Persian RTL and screenshot checks.
- Staged rollout: internal test artifact -> signed release candidate -> reviewed stable publication. Keep rollback information.

## Non-goals

- Rewriting the core or promising every Hiddify transport in a UI-only change.
- Uploading private customer config, subscription URLs or access tokens for analytics.
- Merging stacked experimental branches without independent acceptance checks.
- Claiming the Windows preview is a working VPN.
