# NoraProxy

**NoraProxy** is an Android VPN with its **own in-app Xray engine and Android VpnService**. Customers no longer have to install v2rayNG or V2Box. Sellers can distribute the same NoraProxy APK with a private subscription/invitation, and customers connect through NoraProxy itself.

## Two separate generations of the codebase

- **Native VPN (target v0.2.0)**: source overlays in native-vpn/, deployment script in scripts/prepare_native_vpn.py, and reproducible APK CI in .github/workflows/native-vpn.yml. Official v2rayNG 2.3.5 at exact SHA 9fcb1a30f81345920f9609029ae923b4ea0a866c provides the Xray core, tun device, native tunnel and Android VpnService. Our overlay replaces its main screen and brands the application **NoraProxy** (package app.noraproxy).
- **Legacy v0.1.0 selector prototype**: code in app/, retained temporarily for parser/ranking test coverage. Its standalone APK is NOT the product: it only did TCP endpoint checks and external config export. The old workflow no longer publishes its APK in the native branch.

**No standalone debug APK should be presented as a production release before native CI passes and a device test confirms connectivity.**

## What the native app provides

- Native Persian Compose home with connect/disconnect button and native Android VPN permission.
- VLESS, VMess, Trojan, Shadowsocks, and other protocols already supported by the pinned upstream Xray runtime.
- Import a subscription URL or configuration *within NoraProxy*, directly or through a noraproxy://setup invitation.
- Inspect real proxy-test delays already recorded by the native runtime, rather than random or TCP-only fake ping numbers.
- Show just one choice per detected country and default to the best successfully measured server.
- A single APK usable by multiple resellers; invitation can show a private seller's name inside the app.
- No handoff to external VPN clients.

**MVP limits:** automated health-triggered failover and guaranteed minimum latency are NOT shipped yet. The chosen route is based on existing measurements and should be retested after switching Wi-Fi/mobile networks. Server health, authentication and network censorship conditions may change. One shared installation is not a replacement for proper multi-tenant backend isolation.

## Build the real VPN APK

Follow .github/workflows/native-vpn.yml; it checks out the official pinned upstream and its native submodules, compiles hev-tunnel using Android NDK, downloads the upstream-compatible libv2ray AAR, applies NoraProxy branding and UI, then runs:

    cd upstream/V2rayNG
    ./gradlew --no-daemon :app:assemblePlaystoreDebug

A successful workflow uploads **NoraProxy-Native-VPN-Debug** under GitHub Actions artifacts. This is a debug-signed build for functional validation; production updates require a permanent release signing key, end-to-end tests, and license compliance. Never commit signing keys, upstream account credentials or customer subscription links.

## Invite a reseller customer

Run (use a per-customer subscription, not the reseller master account):

    python3 tools/create_invite.py --seller "Example Shop" --subscription "https://example.com/user-secret-sub"

Or open the offline web/reseller.html form. Opening that private link on an Android device pre-fills/imports the subscription into the **NoraProxy** app and stores the seller display name.

**Invitation URLs contain a sensitive bearer-style subscription address. Share only privately.** The multi-tenant server-side one-time invite service, seller RBAC, reseller account management, and individualized logos are separate roadmap milestones.

## Architecture, source and licensing

- [Native VPN design and limitations](docs/NATIVE-VPN.md)
- [Future reseller SaaS architecture](docs/ARCHITECTURE.md)
- [NoraProxy UI overlay](native-vpn/)
- [Exact pinned upstream source](https://github.com/2dust/v2rayNG/tree/9fcb1a30f81345920f9609029ae923b4ea0a866c)

NoraProxy's native VPN is a modified build of v2rayNG under the **GNU GPL-3.0**. The upstream license, attribution, source availability and any dependency notices must be preserved when redistributing the APK. See LICENSE. NoraProxy does not claim authorship of Xray, libv2ray, or the upstream VPN service.
