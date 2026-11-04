# NoraProxy Android — Advanced UX Phase 2

**Candidate:** 0.2.25  
**Base:** PR #14 (the 0.2.24 capability-discovery layer)  
**Source:** `native-vpn/NoraAdvancedScreen.kt`

## Product design contract

The app's original NoraProxy brand, Persian RTL navigation, graphite surfaces,
blue accent, and native Xray connection state remain authoritative. The
advanced pages are embedded inside the existing `Settings` tab. Android Back
returns to Settings. Bottom navigation returns to its chosen tab. These
screens do not start or stop a second VPN service.

### Per-app Proxy

- Display installed applications with search by label/package.
- Filter by all/selected.
- Persist per-app proxy enablement, bypass mode and selected package names
  using the pinned upstream MMKV keys.
- Reject enabling an empty selection and reject clearing the final selected
  package when it would leave an enabled allowlist empty.
- Changes flag a native service restart; **do not** claim traffic switches
  immediately while an already-running tunnel is active.

### Routing

- Read the exact native routing ruleset and domain strategy.
- Display current rules and allow enabling/disabling non-locked rules.
- Preserve rule order, identifiers, and existing match conditions.
- Open the original native advanced routing editor for new rules, complex
  editing and manual reordering. This legacy screen is a deliberate
  intermediate step, not branded UI completion.

### Network and DNS

- Manage local DNS, Fake DNS, IPv6, IPv6 preference, sniffing and route-only.
- Persist changes using the original AppConfig keys and
  `SettingsChangeManager.notifySettingChanged` to preserve runtime behavior.
- Offer the native advanced settings page for raw resolver lists and MTU.
- Warn that active sessions may need reconnection for changes to apply.

## UX and safety follow-ups

- Migrate the remaining upstream complex routing editor, custom DNS form,
  logs and backup flows to dedicated Nora Compose routes.
- Add optional light/system themes; keep current dark design stable until
  screenshot and accessibility tests pass.
- Add TalkBack checks, 200% font scale, landscape, compact-screen QA, and
  bidirectional text tests for package names and raw addresses.
- Run device tests with Wi-Fi/cellular handovers, two or more different
  subscription formats and all supported Android target versions.
- Do not copy Hiddify source, trademark, or visual assets.

## Release gate

Build and all unit tests must pass on the stacked PR before merging, followed
by real Android VPN/TUN/DNS/leak and app-list behavior checks. Version number,
signing certificate and update digest must be verified against the installed
production app. Until then this is a **development candidate**, not a
production-ready parity release.
