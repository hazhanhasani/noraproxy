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


## Automatic verified OTA release workflow (v0.2.3+)

A successful `main` native build now publishes the fixed-signature Universal APK
and companion `.apk.sha256` to GitHub Releases automatically. The version and
asset names are extracted from the actual overlaid Gradle `versionName`, never
hand-maintained in the workflow. A build cannot publish unless its APK's signing
certificate matches the pinned production fingerprint and SHA-256 check succeeds.

- Publish only from `main`, after all build/signature verification steps succeed.
- If the same version already exists, leave its assets immutable and succeed without republishing.
- Reject an older version than a published stable version.
- For each intended update, increase both `versionName` and `versionCode` in
  `scripts/prepare_native_vpn.py` before pushing to main.
- Releases are distributed through `/releases/latest` and the APK stays inside
  Android app-private storage until explicit user-approved installation.
- The updater validates SHA-256, package ID, signing certificate and increasing
  version code. If GitHub omits `asset.digest`, the updater reads the matching
  published `.apk.sha256` asset; never skip checksum verification.

Build success does not replace actual Android-device VPN, navigation, updater,
and reseller subscription testing prior to wider production rollout.


## MIUI/Android native in-app installation fix (v0.2.5)

The updater still downloads APK bytes in NoraProxy's private storage and checks:
GitHub release SHA-256, APK package ID, versionCode monotonicity, and matching
installed signing certificate. Just before install, it repeats SHA-256 and APK
package/signature verification.

NoraProxy now uses Android's `PackageInstaller.Session` rather than sending a
`FileProvider` URI to Xiaomi's package installer with `ACTION_VIEW`.
The verified APK is streamed into a full-install session and fsynced. A
non-exported `NoraInstallResultActivity` receives status callbacks via an
explicit mutable activity PendingIntent; on `STATUS_PENDING_USER_ACTION`
it opens Android's official user-confirmation UI. Android's unknown-source
permission is still required and install consent is never skipped.

The activity shows a visible error message if the system installer reports a
failure or rejects confirmation. Device-specific installation behavior still
requires manual testing on MIUI/HyperOS hardware.

## Subscription groups, QR gallery and usage (v0.2.7)

The subscription tab exposes the pinned upstream Subscription Manager for group creation,
renaming, deleting and per-group URLs, including local-only groups with empty URLs.
NoraProxy's own main UI displays the groups, switches the selected group in the
upstream MainViewModel, and calls MainAction.SelectServer with an actual GUID from
that group. Location deduplication and real latency tests are scoped to the group.

Both multiline raw node links and HTTPS subscriptions use the upstream
AngConfigManager import path. QR scanning uses upstream CameraX; gallery images
use Android's content picker and are decoded at reduced resolution in a worker.
Decoding failures do not alter existing subscriptions.

Remaining traffic and expiry come only from the optional subscription-userinfo
response header. HTTP requests use HTTPS, disabled redirects, bounded timeouts,
no private IP literals, and no token logging. Missing data is shown as unknown;
raw configurations do not include account balances. Quota data is held in memory
and refreshed on subscription tab entry/explicit refresh, not downloaded constantly.

The pinned upstream VPN service, native Xray runtime, authorization flow and
Tapsell interstitial implementation are unchanged.

## NoraProxy-only subscription management (v0.2.8)

The `Subscriptions` screen remains within the native NoraProxy dark theme.
Editing uses branded Compose dialogs and persists directly to the upstream
MMKV subscription model, while retaining existing profiles. Removal requires
a confirmation and is disabled while connected. No v2rayNG light-themed
`SubSettingActivity` is opened from the native NoraProxy tab.

Human-facing volume and expiry labels are rendered in Persian digits and units
to prevent RTL/LTR reordering. Missing metadata is never treated as zero usage
or an unlimited account. The actual VPN backend and pinned upstream repository
are unchanged.
