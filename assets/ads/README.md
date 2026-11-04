# NoraProxy Promotional Banners

The Android home screen reads **this public GitHub folder**. It fetches `manifest.json` and the referenced images from the `main` branch after changes are merged.

## Upload an ad without a new APK
1. Open `assets/ads/` in GitHub, choose **Add file → Upload files**, and upload a `.webp`, `.png` or `.jpg` image.
2. Recommended size: **1200 × 320 px** (wide 3.75:1). Target **under 200 KB**, maximum download size **2 MiB** per image.
3. Edit `manifest.json`, enable a banner, and commit. The app updates on the next refresh (normally within 15 minutes) and caches images for offline launches.
4. When no banners are active, the app shows a compact non-clickable placeholder.

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
