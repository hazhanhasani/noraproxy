# NoraProxy Promotional Banners

## Automatic registration

**You only need to upload an image to this folder.** The workflow
[`Sync NoraProxy Promotional Banners`](../../.github/workflows/sync-nora-ads.yml)
automatically updates `manifest.json` on the `main` branch after any
image upload, rename or deletion. Accepted extensions: `.png`, `.jpg`,
`.jpeg`, `.webp`; file name must use ASCII letters/digits, hyphens,
underscores and dots, without `..`. Each file must be under **2 MiB**.
The newest **four** valid images appear, and any custom title, enabled
state or HTTPS destination for existing images remains intact. No APK
rebuild is required.

The Android app checks cached promotions when Home opens and refreshes
the remote manifest after its 15-minute cache interval. Reopening the
app after that interval is sufficient; do not clear application data.


The Android home screen reads **this public GitHub folder**. It fetches `manifest.json` and the referenced images from the `main` branch after changes are merged.

## Upload an ad without a new APK
1. Open `assets/ads/` in GitHub, choose **Add file → Upload files**, and upload an image named **`banner-1.webp`**, **`banner-2.webp`**, or **`banner-3.webp`**. These three slots are already enabled in `manifest.json` — no JSON editing is needed for a simple banner upload or replacement.
2. Recommended size: **1200 × 320 px** (wide 3.75:1). Target **under 200 KB**, maximum download size **2 MiB** per image.
3. Commit the uploaded file. The app updates on its next manifest/image refresh (normally within 15 minutes after reopening Home) and caches banners for offline launches. Edit `manifest.json` only for custom filenames, titles, destinations, rotation intervals, or disabling a slot.
4. When none of the configured images exist, or all slots are disabled, the app shows a compact non-clickable placeholder.

Example:

```json
{
  "version": 1,
  "enabled": true,
  "rotationSeconds": 8,
  "banners": [
    {
      "id": "november-offer",
      "file": "november-offer.webp",
      "title": "پیشنهاد ویژه",
      "target": "https://example.com",
      "enabled": true
    }
  ]
}
```

**Rules:** up to 4 enabled banners. Files must be *simple filenames* (no paths), with ASCII letters/digits/hyphens/underscores/dots; no `..`. `target` is optional and must be HTTPS. Never upload API keys, private customer data, or copyrighted creative without rights. To disable ads, set `enabled: false` or clear the `banners` array.

**Deployment:** Because the APK fetches `https://raw.githubusercontent.com/hazhanhasani/noraproxy/main/assets/ads/...`, edits to an unmerged feature branch do not appear for stable clients.
