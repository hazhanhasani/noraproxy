# NoraProxy 0.2.21 — BlueVPN UI Migration

## Source review
- **Reference:** `hazhanhasani/bluevpnapp`, `android-source/BlueVpnHomeActivity.kt`, `BlueVpnServersActivity.kt`, `BlueVpnTheme.kt`, `BlueVpnSubscriptionsActivity.kt`, `BlueVpnSettingsActivity.kt`.
- **Target:** `hazhanhasani/noraproxy`, `native-vpn/NoraMainScreen.kt` (Jetpack Compose).
- **Rationale:** BlueVPN uses imperative Android views; NoraProxy uses a pinned v2rayNG runtime with a Compose overlay. The UI is adapted, not copied wholesale, to preserve the native VPN engine, subscription semantics, signed OTA, import routing and monetization lifecycle.

## Applied design
- BlueVPN dark palette: background `#08090E`, surface `#121319`, primary `#4B7DFF`, secondary `#725BFF`, status green `#2ED3A1`.
- Clean top action bar, branded wordmark, connection-state headline and BlueVPN-style capsule power control.
- Responsive no-scroll home, real measured latency, explicitly labeled foreground-observed connection timer, real subscription traffic and expiry.
- Integrated active location card, smart and recent shortcuts; update badge leads to the working updater.
- Locations: segmented All / Favorites / Recent tabs; native test completion indicator, test-all command, real-latency filters, existing search and favorites.
- Subscription and Settings retain their original working logic with a shared black/blue Material color scheme and surfaces.
- Active bottom-nav indicator and consistent Persian labels across four tabs.
- Recent location history is capped at 12 codes, stored per subscription group in local preferences.

## Deliberately not copied
- BlueVPN-specific customer authentication, billing, subscriptions API, ads carousel, Warp logic, or background connection verification: these are separate product behavior and must not silently replace NoraProxy's VPN runtime or seller subscriptions.
- Fake IP, speed, ISP, or location telemetry. Unknown data stays unknown. The connection timer is **not** claimed as native tunnel uptime.
- A swipe-to-connect gesture: the capsule supports accessible tap anywhere, avoiding unintended connect/disconnect during scrolling or gesture navigation.
- BlueVPN light/system theme: this migration adopts its dark visual identity; future theme variants require contrast and device QA before enabling them.

## Validation
1. `python3 -m unittest discover -s tests -p 'test_nora_design_contract.py' -v`
2. `python3 scripts/prepare_native_vpn.py` against the pinned upstream commit.
3. `./gradlew --no-daemon :app:assemblePlaystoreDebug` and native unit tests in the GitHub Actions workflow.
4. Verify portrait 320/360/393dp widths, 1.3–2x font scaling, home with/without route, permission prompts, connection retry, connected state, recent/favorite changes and rapid taps.
5. Verify imports, group switching, background/foreground refresh, TapSell, and signed OTA on a physical Android device.

**Release policy:** publish a signed `0.2.21` APK only when CI and device acceptance checks pass.
