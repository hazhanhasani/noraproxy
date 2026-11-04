# NoraProxy GitHub Promotions and Compose Performance

## Architecture
First-party promotions are **public GitHub content**, not ads served by the VPN provider and not third-party tracking. Files:

- `assets/ads/manifest.json` — enable/disable banners, filename, optional HTTPS click-through and title.
- `assets/ads/*.webp|png|jpg|jpeg` — images uploaded through GitHub; suggest 1200 × 320 and under 200 KB.
- `native-vpn/NoraRemoteAds.kt` — validation, bounded HTTPS download and local decoded bitmap cache.
- `native-vpn/NoraMainScreen.kt` — Compose `NoraPromotionsStrip()` UI placed just below the connection capsule.

The app reads `raw.githubusercontent.com/hazhanhasani/noraproxy/main/assets/ads/`. **Changes in feature branches are invisible until merged into main.** The release APK and its signing are completely independent of banner updates.

### Failure handling
- Only `HTTPS` destinations are allowed for ad taps (no arbitrary intents/deep links).
- Only simple image filenames; no separators or `..` accepted. Content cannot redirect the image loader to other hosts.
- At most 4 active images, 2 MiB per image, 32 KiB manifest, bounds checking before decode, RGB_565 downsampling to max roughly 1200 × 400.
- No automatic click-through, analytics tracking or privacy identifiers.
- 3.5s connection timeout, 4.5s read timeout, no HTTP redirects, refresh at most once per 15 minutes. Cached banners appear offline.
- An empty or disabled manifest displays a small non-clickable placeholder.
- The banner panel does not affect native Xray, DNS, VPN permission prompts, node switching, or Tapsell. Avoid running network IO on Compose frame callbacks.

### Manual verification
1. Add a `1200x320` compressed WebP under `assets/ads/` on main and enable it in manifest.
2. Open NoraProxy, visit Home and confirm banner loads; switch tabs and confirm there is no UI freeze.
3. Disable network and relaunch: last valid banner should load from disk cache.
4. Try invalid paths, oversized image, malformed JSON and unsupported external targets. App must not crash.
5. Update only a banner in GitHub; ensure it changes after the next refresh without recompiling the APK.

## List performance
The original `remember(refreshTick, selectedGroupId) { NoraRouteSelector.load(...) }` decoded MMKV profiles on the composition/UI thread. This implementation computes the sorted route snapshot on `Dispatchers.IO` and publishes an immutable Pair of route list + per-country list. Search/filtering uses `remember(...)`, and LazyColumn rows use stable country and group GUID keys plus explicit `contentType`.

For further instrumented optimization, benchmark on a budget Android device with 200+ imported routes, rapid scroll and live ping; inspect JankStats/Perfetto for frames above 16.67ms at 60Hz. Avoid perf claims from static tests alone.

### Acceptance checks
- Python static UI and ad contracts: `python3 -m unittest discover -s tests -p 'test_nora_design_contract.py' -v`
- CI: pinned upstream Gradle debug APK compile and native unit tests.
- Hardware checks: touch gestures, navigation, group switching, airplane mode, ad rotation, low RAM and font scaling.
