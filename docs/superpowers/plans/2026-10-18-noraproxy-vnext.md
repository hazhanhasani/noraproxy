# NoraProxy VNext Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a responsive, privacy-first cross-platform redesign without regressing actual Android VPN connectivity.

**Architecture:** Keep the existing pinned Xray/VpnService adapter as the sole runtime owner. Compose uses observable connection state, view models and pure routing helpers; Windows WPF maintains an honest preview capability flag. Analytics is optional and strictly sanitized.

**Tech Stack:** Kotlin / Jetpack Compose, WPF / .NET, Python build scripts, GitHub Actions, Figma, MagicPath, optional PostHog.

**Spec:** `docs/NORAPROXY_VNEXT_SPEC.md`

## Global Constraints

- Release identity `app.noraproxy` and fixed signing key are unchanged.
- Never edit protected upstream native core service files.
- Never transmit VPN credentials, subscription URLs, user identifiers or raw stack traces.
- Windows VPN button stays disabled until a validated native VPN runtime exists.
- No release or production merge until end-to-end tests pass.

## Review Focus

- VPN permission revoked mid-connection: UI must reflect the actual service state and safe failure.
- No ad inventory: connect/disconnect must still complete; overlay never traps the user.
- Import of invalid/duplicate mixed URLs: clear, non-destructive validation result.
- Subscriptions with >1,000 nodes: country search remains interactive and grouped.
- Missing network on app launch: refresh reports offline but does not erase stored configs.

---

### Task 1: Route Search Optimization

**Files:**
- Modify: `native-vpn/NoraRouteSelector.kt`
- Test: `native-vpn/tests/NoraRouteSelectorTest.kt`

**Interfaces:** Preserve `NoraRouteSelector.searchLocations(locations, query, favorites, favoritesOnly)`.

- [x] Write regression coverage for partial English aliases and short exact country codes.
- [ ] Run tests against unfixed code; verify failed performance/behavior as appropriate.
- [x] Compute alias matches once per query and reuse the country-code set during filtering.
- [ ] Verify Kotlin Android test runner is green in GitHub Actions.
- [ ] Commit and review.

### Task 2: Service-State Connection UI

**Files:**
- Modify: `native-vpn/NoraMainScreen.kt`
- Create: `native-vpn/NoraConnectionUiState.kt`
- Test: `native-vpn/tests/NoraConnectionUiStateTest.kt`
- Update: `scripts/prepare_native_vpn.py`

**Interface:** `NoraConnectionUiState.from(running, transitioning, permission, lastErrorCode)`.

- [ ] Write failing state transition tests for connecting, connected, disconnected and revoked permission.
- [ ] Run Android unit tests and observe failure.
- [ ] Add pure state mapping; render status from actual service callbacks rather than click events.
- [ ] Verify connect/disconnect cycles on a real Android device and check OEM handling.
- [ ] Commit separately from UI cosmetics.

### Task 3: Cross-Platform Design System

**Files:**
- Modify: `native-vpn/NoraMainScreen.kt`
- Modify: `desktop/windows/NoraProxy.Windows/App.xaml`
- Modify: `desktop/windows/NoraProxy.Windows/MainWindow.xaml`
- Test: corresponding Android & Windows UI parity workflows

- [ ] Approve Figma prototype as a visual target; preserve signed Android functionality.
- [ ] Define typography and spacing tokens with accessible focus and 48dp hit targets.
- [ ] Refactor Android Home, route picker, subscriptions and settings one screen at a time.
- [ ] Translate same hierarchy to adaptive WPF; no false indication of active tunnel.
- [ ] Verify responsive layouts, RTL, large font, reduced motion and snapshot comparisons.

### Task 4: Instrumentation (Opt-in)

**Files:**
- Create: `native-vpn/NoraAnalytics.kt` and `native-vpn/tests/NoraAnalyticsTest.kt`
- Update: Gradle overlay script and privacy settings after confirmed PostHog project

- [ ] Confirm the intended PostHog project, retention policy and end-user opt-in.
- [ ] Write tests rejecting tokens, URLs, server names, IP addresses and raw error text.
- [ ] Implement an allowlisted event/property mapper; telemetry disabled by default.
- [ ] Record only connection outcomes, latency buckets, refresh outcomes and crash categories.
- [ ] Review PostHog event schema and rollouts without enabling sensitive session replay.

### Task 5: Release Pipeline and Validation

**Files:** `.github/workflows/native-vpn.yml`, `.github/workflows/windows.yml`, release notes and documentation.

- [ ] Require Android unit tests, WPF tests, signing and checksum gates.
- [ ] Run emulator / physical-device smoke tests on UI, VPN lifecycle, update installation and Android permissions.
- [ ] Verify signed APK identity, version bump, download and Telegram formatting.
- [ ] Open reviewed PR and merge only after test and visual acceptance gates.
