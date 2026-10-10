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

**Missing or invalid IDs:** the controller is disabled, connects/disconnects
work normally, and no test ads are served to users. The SDK has its automatic
initialization disabled in AndroidManifest and is initialized manually only
when configured.

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

Initial staging is intentionally **without a new version number** while waiting
for real Tapsell IDs and end-to-end testing. It therefore does not replace the
existing public release.
