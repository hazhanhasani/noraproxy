# NoraProxy Android — Advanced UX Phase 2

**Candidate:** 0.2.27  
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
- Use the Nora-native editor for creating, editing, deleting and ordering rules.
  Keep the upstream Import/Export presets and complex external tools accessible
  until these are replaced with fully branded workflows.

### Native routing editor (0.2.26)

- Create a rule with a stable generated ID and an explicit match condition.
- Edit domain/geosite, IP/CIDR/geoip, process, protocol, ports, network and
  outbound tag, while preserving enabled/locked flags and existing IDs.
- Delete only unlocked rules after confirmation; guarded against stale indices.
- Reorder adjacent unlocked rules, preserving MMKV order and preventing movement
  through protected rules. Reordering is disabled while the filtered list is shown.
- Validate names, scoped conditions, CSV limits, port ranges and outbound tags
  before any write. Do not create an accidental catch-all block rule.
- Keep upstream native Import/Export as a fallback pending full branding.

### DNS servers editor (0.2.26)

- Edit Remote/Direct DNS using literal IP, HTTPS DoH, TCP or QUIC endpoints.
- Edit VPN DNS using IP literals only, matching the upstream native resolver.
- Reject malformed URLs, credentials, invalid ports and oversized lists.
- Blank fields reset to upstream defaults instead of leaving an invalid resolver.
- Save through the existing MMKV settings and request an Xray restart, without
  constructing a second tunnel or changing active server credentials.
- Preserve unsaved edits until explicit confirmation; Cancel is non-destructive.

### Network and DNS

- Manage local DNS, Fake DNS, IPv6, IPv6 preference, sniffing and route-only.
- Persist changes using the original AppConfig keys and
  `SettingsChangeManager.notifySettingChanged` to preserve runtime behavior.
- Edit Remote, Direct and VPN DNS resolvers in the Nora-native dialog.
  Preserve the upstream advanced settings page for MTU and remaining low-level options.
- Warn that active sessions may need reconnection for changes to apply.

### Responsive theme selection (0.2.27)

- NoraProxy now offers **System / Light / Dark** from its Persian Settings tab.
- Uses the existing upstream `ThemeManager` and `PREF_UI_MODE_NIGHT`
  MMKV key; no shadow preferences or independent account settings.
- Semantic colors cover home, tabs, location lists, subscriptions, advanced
  routing, per-app controls, DNS and rule dialogs.
- The system mode responds to Android's current dark/light preference.
- The Nora identity stays blue, with themed surfaces, readable text and a
  consistent system-bar mode through the existing upstream activity theme.
- Some less-common upstream screens and third-party promotion assets retain
  their original styling until screenshot and device accessibility QA.

### Diagnostics and protected backup navigation (0.2.27)

- Nora-branded diagnostic page reads a **limited subset** of logcat on IO,
  supports search/refresh, and explicitly warns about private information.
- Before rendering or copying, known URL, configuration URI, bearer-token,
  and credential patterns are redacted. **This is best-effort sanitization**,
  not a security guarantee. Raw logs remain in the original advanced viewer.
- No logs are uploaded by Nora's diagnostic page.
- The Nora backup hub explains that archives may contain credentials and
  launches the pinned original backup/restore flow, rather than introducing
  an unverified archive writer or recovery routine.
- Automated tests check palette mode selection and representative redaction
  patterns; physical-device logcat and recovery QA remain required.

## UX and safety follow-ups

- Unify the remaining upstream Import/Export routing actions, raw log
  viewer, WebDAV configuration and MTU/advanced transport into Nora Compose
  routes. Keep mature backup/restore operations delegated to upstream for safety.
- Validate light/system/dark screen contrast, including on Android 12+,
  and test accessibility and screenshot regressions before release.
- Add TalkBack checks, 200% font scale, landscape, compact-screen QA, and
  bidirectional text tests for package names and raw addresses.
- Run unit tests in `NoraAdvancedValidationTest` for ID preservation, scoped
  rules, port ranges and DNS endpoint validation.
- Run device tests with Wi-Fi/cellular handovers, two or more different
  subscription formats and all supported Android target versions.
- Do not copy Hiddify source, trademark, or visual assets.

## Release gate

Build and all unit tests must pass on the stacked PR before merging, followed
by real Android VPN/TUN/DNS/leak and app-list behavior checks. Version number,
signing certificate and update digest must be verified against the installed
production app. Until then this is a **development candidate**, not a
production-ready parity release.
