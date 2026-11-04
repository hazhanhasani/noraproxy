# NoraProxy Promotional Banners

NoraProxy 0.2.21 loads first-party banner images from this public folder on the
`main` branch. **You only need to upload the image.** No APK rebuild, preset
filename, JSON editing or server login is required.

## Upload an ad

1. Open `assets/ads/` on GitHub, choose **Add file → Upload files**, select a
   `.webp`, `.png`, `.jpg`, or `.jpeg` image, and commit it on `main`.
2. The [Sync NoraProxy Promotional Banners workflow](../../.github/workflows/sync-nora-ads.yml)
   scans uploaded files and updates `manifest.json` automatically.
3. Reopen NoraProxy after its 15-minute promotions cache interval. The image
   then appears on the Home screen. Do not clear app data or reinstall the APK.

**Recommended image:** 1200 × 320 px (wide 3.75:1), compressed WebP, ideally
under 200 KiB. The maximum app download size is 2 MiB per image, and the
Android decoder accepts images up to 4096 × 2048 px. Use filenames containing
ASCII letters/digits, dots, underscores or hyphens only; no `..` sequences.
Only the **four most recently committed** valid images will display.

## Configure behavior

The Android app reads `manifest.json` from
`https://raw.githubusercontent.com/hazhanhasani/noraproxy/main/assets/ads/manifest.json`.

The auto-updater preserves the manually set `title`, `enabled`, and optional
HTTPS `target` for images already present in the manifest. You may change
`rotationSeconds` to 3–30 and set the top-level `enabled: false` to hide all
banners. Uploaded files automatically create enabled entries by default.

Example entry generated for a new file:

```json
{
  "id": "github-sale-2026",
  "file": "sale-2026.webp",
  "title": "تبلیغات NoraProxy",
  "enabled": true
}
```

The manifest is refreshed on Home entry after its cache interval; offline
launches use cached images. With no valid images, the app shows a non-clickable
placeholder. The manifest is not a place for API keys, personal information or
secrets. Only upload artwork you are authorized to use.

The upload automation only runs for changes committed to `main`. Uploads to
feature branches do not become visible in the published application.
