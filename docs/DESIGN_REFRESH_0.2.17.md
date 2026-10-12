# NoraProxy Android 0.2.17 — UI polish

Intent: visually refine the existing native Android UI without changing
underlying proxy configuration, real server selection, tunnel state or
advertisement integration.

## UI changes
- Graphite/teal vertical-depth Home background, rounded connected-state pill,
  same brand cyan and restrained violet accents.
- Power ring with soft radial halo, a thin inner keyline and TalkBack
  content descriptions for connected, disconnected, and disabled states.
- Route card with restrained gradient, border and a compact real-measured
  latency pill. **No fabricated ping**. Quota labels are a bit larger.
- Country quick cards use native selectable semantics and an obvious active
  state; each has at least a 56dp vertical tap target.
- Bottom tabs provide 59dp height, selected semantics and non-duplicated
  accessibility labels. Favorite-star target is 48x48dp.

## Behavior explicitly unchanged
- Home remains fixed-height/no-scroll, with responsive tiny-device sizing.
- Preferred country and actual selected server GUID remain synchronized.
- VPN engine, in-app signed APK updater, and ad flow are untouched.
- Smart Engine diagnostics and background/foreground safety are unaffected.

## Verification
Run `tests/test_nora_design_contract.py`, pinned upstream overlay,
Android Debug+Unit tests, and fixed-signature Release build via GitHub Actions.
Screenshot/manual checks remain required for 320dp phones, TalkBack focus,
compact landscape, and varying Android font scaling.
