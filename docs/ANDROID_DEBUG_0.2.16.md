# NoraProxy Android 0.2.16 — Debug and regression audit

## Scope
The supported shipping Android client is the `native-vpn/` overlay of the pinned
v2rayNG engine, built by `.github/workflows/native-vpn.yml`. The separate
`app/` project is not the native VPN APK built by that workflow.

## Confirmed code issues and fixes

1. **P0 offline server selection triggered service restarts.**
   Pinned upstream `MainActivity.setSelectServer()` unconditionally calls
   `LauncherManager.restartService(this)`, even when disconnected.
   Home connect, selecting a country and automatic selection could call the
   action while offline. The new `selectActualServer` dispatches the upstream
   action only while connected; offline selection persists the GUID via
   `MainViewModel.updateSelectedGuid()`. Initial auto-selection is deferred
   while connected or subscription sync is running.
2. **P1 background current-server probing.**
   A 30-second UI coroutine depended on VPN state and GUID only, so it continued
   when the Activity entered background. This coroutine now observes
   foreground STARTED state, checks for other running tests and subscription
   writes, and cancels when the app stops. Background VPN connection itself
   remains untouched.
3. **P1 spurious handoffs from simultaneous physical network callbacks.**
   Android `registerNetworkCallback` reports all matching networks; a cell
   callback after a Wi-Fi callback does not imply that the phone switched
   away from Wi-Fi. Track callback networks, compute the effective preferred
   transport (ethernet > Wi-Fi > cellular), and handle `onLost`.
   A generation guard rejects queued callbacks after unregistration.
   An effective change merely schedules native remeasurement; it does not
   blindly restart Xray.
4. **P2 failed subscription update aborting later groups.**
   An unexpected exception from one remote group no longer prevents
   processing the others. Coroutine cancellation still propagates.

## Evidence
- Pinned upstream: `2dust/v2rayNG` commit
  `9fcb1a30f81345920f9609029ae923b4ea0a866c`, MainActivity
  `setSelectServer()`: selected GUID update then unconditional
  `LauncherManager.restartService(this)`.
- Android official docs: https://developer.android.com/develop/connectivity/network-ops/reading-network-state
  explains simultaneous networks for `registerNetworkCallback`, callback
  unregistration, and default-network semantics.

## Regression
`NoraSelectionPolicyTest` validates no service restart while disconnected,
restart while connected, and no-op on duplicate/blank GUID. Additional
`NoraSmartEngineTest` covers concurrent Wi-Fi and cellular reporting.
CI executes these alongside prior tests and builds the fixed-signature APK.

## Open device-only checks
- Test on Android 13, 14, 15+ with Wi-Fi and mobile both enabled.
- Verify connect/disconnect with first-run VPN permission prompt.
- Verify subscription refresh, Favorites, QR gallery and failed endpoints.
- Confirm interstitial advertising behavior with and without inventory.
- Check 320dp and landscape layouts and TalkBack contrast/focus navigation.
- Profile startup, MMKV I/O and scrolling with Android Studio profiler.
- Verify upgrade over prior signed APK with retained preferences.
Never treat passing unit tests as a substitute for real-device VPN/tunnel testing.
