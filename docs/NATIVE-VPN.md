# Native VPN integration (NoraProxy v0.2.0)

## Product promise

The user's phone connects through **NoraProxy itself**, using Android VPN permission and the Xray core. V2Box and v2rayNG are not required, opened, or invoked at runtime. This replaces the earlier selector-only v0.1.0 architecture.

## Runtime pipeline

    seller subscription / private noraproxy://setup invitation
          |
    original v2rayNG import and subscription updater
          |
    native MMKV profile repository
          |
    NoraRouteSelector (best known delay; one profile per country)
          |
    NoraProxy home Connect button -> original MainAction.SelectServer
          |
    original MainActivity -> VpnService.prepare -> LauncherManager
          |
    CoreVpnService + Xray native libv2ray + hev tunnel
          |
    Android TUN routing / encrypted tunnel / traffic

The original upstream main screen is **overwritten at build time**, but the original runtime lifecycle, VPN service, import parser, config manager and native tunnel code remain unchanged. The version is reproducibly pinned at Git SHA 9fcb1a30f81345920f9609029ae923b4ea0a866c.

The Gradle app namespace remains com.v2ray.ang for source compatibility, but package/applicationId is **app.noraproxy**, branded launcher label and icon **NoraProxy**. The built app is self-contained.

## Server selection

NoraRouteSelector reads profile GUIDs and real delay values from upstream MMKV. Positive delay is measured, zero means unknown (not 0 ms), negative means test failure. Measured nodes rank first, then unknown nodes, then failed nodes. Per-country deduplication takes the best node in that country; all other same-country configs stay internally available. Pressing Connect activates the desired route, and the underlying MainActivity uses the original Android VPN permission flow.

Current tests are initiated by the native runtime's RealPing test button; stale history has no TTL yet and is not a promise of fastest possible internet. Continuous automated failover is intentionally left for a tested next version because uncontrolled service restarts would harm customers.

## Reseller safety

One shared APK works with private customer-issued subscriptions. A private seller deep link contains the seller name and a HTTPS URL. Seller branding is a local display preference. This is **not** server-side tenant isolation, white-label distribution, or an account system. A customer should only be handed their own token, never the reseller's master URL.

Do not log credentials; minimize HTTP redirects and secret propagation. Remote subscription URLs can be bearer tokens, and deep links should only be sent privately. Production requires opaque one-time invitation exchange and tenant ownership enforcement.

## Build prerequisites

- Official v2rayNG and its git submodules checked out to upstream/
- Android SDK 37 + NDK 29.0.14206865
- JDK 17 and upstream Gradle 9.5.1 wrapper
- Upstream-matched libv2ray.aar from AndroidLibXrayLite tag

Run scripts/prepare_native_vpn.py, then the upstream Gradle playstore debug build. CI executes these tasks and uploads the native APK after success.

## Acceptance before commercial distribution

1. Native CI green with a real Xray AAR and hev tunnel bundled
2. Fresh Android installation: import HTTPS subscription and grant VPN permission
3. Confirm traffic exits through chosen server, verify DNS and IPv6 behavior
4. Validate VLESS Reality/XTLS, VMess, Trojan, Shadowsocks and subscription refresh
5. Test real latency signals on multiple cellular/Wi-Fi providers
6. Verify disconnect, background, screen lock and network handover on real phones
7. Confirm signing identity, version upgrades, GPL attribution and supported dependency license notices

No production readiness claim until all of these succeed.
