# NoraProxy × Tapsell Mediation — Android Interstitial Ads

This optional integration displays Tapsell Mediation **interstitial** ads after a user-triggered VPN connection or disconnection actually changes state. Native Xray VPN actions are never blocked by ad loading, watching, network failures or inventory.

## Configure real production ads

1. Register Android app `app.noraproxy` in https://app.tapsell.ir/.
2. Create an **Interstitial** placement/zone for this app.
3. Set these **GitHub Actions Secrets** at
   https://github.com/hazhanhasani/noraproxy/settings/secrets/actions :
   - `NORA_TAPSELL_APP_ID` — Tapsell Mediation App ID.
   - `NORA_TAPSELL_INTERSTITIAL_ZONE_ID` — dedicated interstitial Zone ID.
4. Use real production IDs, not Tapsell's test App ID / Zone ID.
5. Bump `versionCode` and `versionName` in `scripts/prepare_native_vpn.py` and run the
   `Build NoraProxy Native VPN` workflow on main. Official releases are immutable;
   registering secrets alone cannot modify a published APK.

**Missing or invalid IDs:** main-branch production builds fail BEFORE APK
compilation or release, rather than silently releasing an ad-disabled build.
Pull-request and development builds can still compile with ads disabled.
When a valid production APK is built, the controller preloads interstitials and
connects/disconnects without depending on ad availability. The SDK has its
automatic initialization disabled in AndroidManifest and is initialized
manually only when configured.

**Important build detail:** the secrets must be injected during the
`Apply NoraProxy standalone VPN overlay` step: that is when `BuildConfig` and
the Tapsell manifest placeholder are generated. Passing secrets only to the
later Gradle build step does **not** enable ads. A CI assertion now verifies
that `NORA_TAPSELL_ENABLED=true` is compiled into main releases. No values
are printed in build logs.

## User experience and safety

- Ads are preloaded on the main screen. A fresh interstitial is displayed **after**
  the native VPN state reaches the state requested by the user. This avoids
  drawing an ad over Android's first-time VPN permission prompt.
- If no ad is ready, loading fails, or the SDK reports an error, there is
  **no waiting screen**. The user gets access to the app immediately.
- When the SDK shows a full-screen ad, interaction with the underlying NoraProxy
  power control is disabled; the SDK's official close/Back controls remain available.
- When the ad closes (including an early skip) or fails to show, controls are
  restored and the next ad is prefetched.
- The app **cannot and must not** disable Android's system Home/Back navigation
  or force a user to finish an ad beyond the ad network's official behavior.
- VPN disconnect always happens before an ad display. The user is never
  required to view an ad to disconnect.
- Ad inventory and network consent are external concerns; there is no
  guarantee that every toggle receives an ad impression.

## Privacy and monetization considerations

For publishing in regions with GDPR/other consent requirements, add a consent
flow and provide a privacy notice explaining the advertising SDK and any device
data it collects. Tapsell documentation describes `setUserConsent(...)`.
Do not activate personalized ads without applicable consent. Excessive
interstitial frequency can cause user churn or conflict with app-store/ad-network
quality guidelines. Consider frequency caps or a paid no-ads tier.

## Official sources

- https://developer.tapsell.ir/docs/sdk/platforms/android/quick-start/
- https://developer.tapsell.ir/docs/sdk/platforms/android/ad-formats/interstitial/
- https://developer.tapsell.ir/docs/sdk/platforms/android/test/
- https://developer.tapsell.ir/docs/sdk/changelog/

## Implementation

- `native-vpn/NoraTapsellAds.kt`: SDK load/show lifecycle and callback handling.
- `native-vpn/NoraMainScreen.kt`: triggers only after actual VPN transition.
- `scripts/prepare_native_vpn.py`: Gradle/Maven dependencies, runtime IDs,
  disabled automatic initialization.
- `.github/workflows/native-vpn.yml`: injects production values only from
  GitHub Secrets, without committing identifiers or credentials.

Version **0.2.6** enables this integration in signed public APKs when the
Tapsell IDs are present and valid. A successful Android CI build does not prove
that a live ad will be returned by the Tapsell network. Confirm real display,
dismissal, lack-of-inventory fallback and VPN behavior on a physical device.

## Device-level reliability improvements (v0.2.10)

Tapsell ad fill is not guaranteed across phones: regional availability,
device eligibility, connectivity, DNS/ad blockers, network policy, user consent,
SDK response latency and invalid inventory can all prevent delivery.
Even correctly configured production IDs cannot force an impression on every
connection or disconnection.

The app now retries failed ad requests with capped exponential delays
(15/30/60/120 seconds), applies a request timeout, discards stale loaded ads,
and uses a display watchdog to avoid indefinitely locking the app controls.
The **Settings → Ad status** field shows local load/no-fill/display state
without exposing advertisement IDs, subscription URLs or SDK error payloads.
The VPN operation always takes place first and must not depend on ad inventory.

Manual testing on affected devices:
1. Use the official *release-signed* APK (debug and PR builds can disable ads).
2. Open Settings → Ad status and wait for the SDK to load a creative.
3. Switch between cellular and Wi-Fi; check restricted/ad-blocking DNS settings.
4. Test connect and disconnect with enough time for preload; do not assume
   every toggle gets an impression.
5. If missing inventory persists on a particular region/carrier, inspect
   Tapsell dashboard fill rate, placement health, mediation adapters, consent
   and country targeting. Never force-close OS navigation to compensate.
