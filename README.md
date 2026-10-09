# NoraProxy

Android-first **smart subscription companion** for VPN resellers and their customers.

> **MVP limitation:** NoraProxy v0.1.0 is a real TCP-based endpoint selector, **not** a standalone VPN. It does not yet contain the Xray core or establish a VPN tunnel. After choosing a node, customers can copy/share its configuration into v2rayNG or V2Box. A reachable TCP port does not prove that proxy authentication, TLS/Reality negotiation, or browsing through the tunnel will succeed.

## Features

- Native Persian Jetpack Compose UI with seller branding and one suggested node per location
- HTTPS-only subscription import; plain or Base64-encoded share links
- VLESS / VMess / Trojan / Shadowsocks endpoint parsing
- Real TCP reachability tests from the **customer's own device**, not fake latency values or distant datacenter metrics
- At most 250 nodes, 12 concurrent tests, and two extra confirmation samples for the eight leading candidates
- Conservative ranking by reachability, connection latency and stability; unavailable routes do not outrank reachable ones
- Selected configuration handoff via Android clipboard or the Android share sheet
- Per-device encrypted subscription storage using Android Keystore AES-GCM
- Private reseller invitation links with prefilled branding (no silent network requests)

## Seller onboarding

The APK can be shared by many resellers. Each customer inputs their *own reseller-issued subscription*; routes never mix across customer subscriptions.

Run:

    python3 tools/create_invite.py --seller "My Shop" --subscription "https://example.com/sub?token=PRIVATE"

Or open [the offline invite builder](web/reseller.html) in a browser.

**Security:** Invitation links contain the original subscription URL (a bearer-like secret). Share one link with **one intended customer privately**. Do not post invites publicly or reuse one shared credential for an entire reseller. Centralized signed one-time invite exchange and reseller RBAC are future milestones.

## Build

Requires JDK 17, Android SDK 35 and Gradle 8.11.1:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

The debug APK is in app/build/outputs/apk/debug/app-debug.apk. GitHub Actions will also upload noraproxy-debug-apk on a successful build. A debug build is not a signed commercial release.

## Project map

- app/src/main/java/app/noraproxy/MainActivity.kt: mobile UI and config export
- app/src/main/java/app/noraproxy/NoraViewModel.kt: import and ranking state
- app/src/main/java/app/noraproxy/core/: parsing, real TCP probing, scoring and secrets encryption
- app/src/test/: parser and selection unit tests
- web/reseller.html: zero-backend private invite generator
- tools/: invitation CLI and tests
- docs/ARCHITECTURE.md: planned native VPN, SaaS security and milestones

## Roadmap

1. Protocol-aware proxy RTT tests and a native Xray-based Android VPN client with VpnService
2. Device network-aware smart failover, hysteresis, and realtime tunnel health checks
3. Multi-tenant admin portal, reseller roles, expiring opaque invites, and server-side entitlements
4. Branding packs, APK release signing, staged updates, and production end-to-end tests

See [Architecture](docs/ARCHITECTURE.md). Any future derivative based on GPL-3.0 v2rayNG must respect upstream license terms and notices.
