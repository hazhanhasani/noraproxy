# NoraProxy

🌐 **Official website:** [NoraProxy](https://hazhanhasani.github.io/noraproxy/) · [Website source](site/) · [Pages setup](site/README.md)


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

## Subscription groups and quota (v0.2.7)

From the **اشتراک** tab, open **ساخت، ویرایش و حذف گروه اشتراک** to create any number of named groups.
Each group can contain an HTTPS subscription URL, manually pasted VLESS/VMess/Trojan/Shadowsocks URLs, or both.
Select a group before importing; country choices, latency tests and native connection routing use the selected group's real server GUIDs.
You can paste several raw share links at once, scan a QR code with the camera, or select a QR image directly from the Android gallery.

Traffic/expiry badges use the provider's optional HTTP `subscription-userinfo` header
(`upload`, `download`, `total`, `expire`) over validated HTTPS without redirects.
The application does **not** infer remote account quotas from locally observed VPN traffic:
if metadata is unavailable (especially for standalone raw configs), it explicitly shows **unknown**.
A refresh button requests fresh quota headers; account tokens and full subscription URLs are never shown on the public UI.

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

## Native subscription management and Persian formatting (v0.2.8)

Group creation, editing and deletion now use NoraProxy dark-themed Compose dialogs
inside the application's own Subscription tab. We no longer navigate to the
unbranded v2rayNG `SubSettingActivity` for customer group management.
Existing subscriptions persist in the original MMKV schema and are not migrated,
replaced or silently discarded. Deletion requires confirmation and is blocked
during an active VPN connection.

Quota cards and the compact Home usage line display Persian digits and
Persian storage units (بایت، کیلوبایت، مگابایت، گیگابایت، ترابایت),
rather than mixing Latin `TB`/`GB` with right-to-left sentences.
Expired and missing metadata remain distinct; unlimited is displayed only when
the provider explicitly reports `0` for the respective field.

For QA, see the native Android CI unit test `NoraSubscriptionUsageTest`.

## Simplified subscription UX (v0.2.9)

The group dialog only asks for a group name. Use the one **Add to group**
form for an HTTPS subscription, raw VLESS/VMess/Trojan/Shadowsocks URIs, or QR.
Pasting a subscription URL updates the currently selected group instead of
creating a duplicate `import sub` group. Keep raw configs in a separate
local-only group so remote automatic updates cannot overwrite them.
This change does not remove previously imported groups.

## Automatic import and ad diagnostics (v0.2.10)

No manual group selection is required to import. One HTTPS subscription URL
creates one dedicated group; identical URLs reuse existing groups. Several
HTTPS subscription URLs can be pasted at once. Raw VLESS, VMess, Trojan,
Shadowsocks and other supported share links automatically go into the Default
local-only group, even when a different subscription is selected. The app
preserves existing groups and user profiles.

Tapsell interstitials are prefetched and now use bounded retries, timeout
handling and an ad status indicator on Settings. Missing inventory or an SDK
error must never delay VPN connection or disconnection. Ad impressions cannot
be guaranteed on all carriers, devices, networks or countries.

## Foreground subscription refresh (v0.2.11)

Whenever the Android app enters the foreground, it immediately starts a non-blocking
refresh of **every enabled URL-backed subscription group** (including groups other
than the selected one). Disabled groups and the local-only Default/raw group are
never downloaded or replaced. Subscriptions with autoUpdate disabled are still
refreshed on foreground if the group itself is enabled; the periodic background
schedule is unchanged. Refreshes are serialized to protect native MMKV profile
writes and debounced for 30 seconds to avoid repeat requests after interstitials
or permission dialogs. The native view model and the usage/expiry cards are
reloaded when the cycle finishes. If the device is offline, existing cached
servers remain available.

## Subscription UX and connected ping (v0.2.12)

The subscription import form is now first. URLs automatically create dedicated
subscription groups, while raw configs use the local Default group. Empty manual
group creation has been removed; editing/deleting existing groups still works.
The home location card displays measured native ping even when selected. While
connected, native ping measurements are refreshed every 30 seconds when enabled
in the streamlined Settings page, which retains signed in-app updates.

## NoraProxy UI 3.0 — first delivery (v0.2.13)

The Home dashboard now combines connected status, a native connection control,
backend-selected location, measured latency (when available), provider-reported
remaining quota and validity, and a horizontal country quick picker. Quick picks
use the same native SelectServer action and stored country preference as the
Locations tab; selection never changes only the visual highlight. The layout
adapts power-button size and spacing to compact displays, with no Home scrolling.

The former 15-second unconditional MMKV profile polling was removed. Instead,
changes to native UI state, subscription updates and active ping measurements
invalidate the cached routes. A connected device refreshes the last native ping
result after triggering a measurement and then repeats on a 30-second cadence.
Unavailable ping/quota data remains unknown, rather than showing invented values.

## Favorite locations and compact subscriptions (v0.2.14)

Locations support localized Persian/English ISO country searches, common city
aliases and country codes. Favorites are saved in local Android SharedPreferences
by ISO code and promoted to the start of both the Locations list and Home quick
picker. A favorites-only filter is available. Favoriting is a UI preference and
never modifies native VPN credentials or server IDs; route selections continue to
use the original native GUID.

Subscription import stays at the top of its tab, but now opens/collapses on tap.
Existing group cards keep traffic and expiry visible while hiding edit/delete
buttons under an explicit Manage group control. The existing manual refresh
button is labeled to reflect its actual selected-group-only behavior.

## Smart Engine (v0.2.15)

The Smart Engine uses native Xray real delay samples, smoothed latency and
jitter to choose a stable route within the user's chosen country (or across
countries only in automatic-location mode). Unknown and failed measurements
cannot win ranking against positive measured latencies.

While the app is foregrounded, it observes non-VPN underlying network
transitions between Wi-Fi, cellular and Ethernet and schedules native
all-server remeasurement after network settlement. It also checks health at
most once every six minutes while connected and visible. No periodic scans
continue after backgrounding or disconnecting.

Automatic recovery is conservative: it requires two completed real native
test batches reporting a negative delay on the active GUID, a currently
positive-delay alternative, and five minutes since manual or automatic
selection. A manually preferred country is never overridden; switching
uses the upstream SelectServer action so Android restarts the actual VPN
service. No synthetic ping, no independent network leak probes, no unknown
country guessing. Users can toggle Smart Mode and Automatic Recovery in
Settings; both preferences persist locally. The policy does not claim a
fully transparent zero-downtime handoff.

## Windows desktop preparation

The native Windows preview lives in [desktop/windows/](desktop/windows/) and
uses a [Windows-only GitHub Actions build](.github/workflows/windows.yml).
It offers a Persian WPF .NET 10 shell plus offline imports of HTTPS subscriptions
and raw proxy links encrypted with Windows user-scoped DPAPI.

**This first Windows build does not yet connect to a VPN.** The Connect button
is deliberately disabled until real Windows Xray/TUN integration, leak
protection and physical device testing. Windows CI produces an **unsigned
preview ZIP**, not an official signed VPN release, and does not send it to the
Android Telegram channel. See [Windows roadmap](docs/WINDOWS.md).

## Windows EXE installer (desktop preview)

The [Windows desktop workflow](.github/workflows/windows.yml) builds
`NoraProxy-Setup-0.1.1-win-x64.exe` using Inno Setup for Windows 10/11.
It installs per user, adds a Start Menu shortcut and standard uninstall
entry, and is smoke-tested by the Windows runner.

**Not a connected VPN yet:** Windows Xray/TUN integration is still pending.
The unsigned EXE is a desktop UI and encrypted subscription manager. Android
builds/releases and Telegram APK publishing remain unchanged.
