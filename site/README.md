# NoraProxy Official Website

A modern, Persian-first, RTL-responsive landing page for the
[NoraProxy Android VPN](https://github.com/hazhanhasani/noraproxy).
Built with plain HTML, CSS and JavaScript. No npm, CDN, cookies or analytics.

## Live address

Once GitHub Pages is enabled and its deployment succeeds:

**https://hazhanhasani.github.io/noraproxy/**

## One-time setup

The repository administrator needs to enable Pages once:

1. Open repository **Settings → Pages**.
2. Under **Build and deployment**, choose **Source: GitHub Actions**.
3. Open **Actions → Deploy NoraProxy Website**, then select **Run workflow**
   on **main** if it has not already deployed.

Subsequent edits under `site/` on `main` redeploy the site automatically via
`.github/workflows/pages.yml`. The website workflow is separate from the
Android APK/signing workflow and does not publish APK releases.

## Latest APK

`app.js` fetches the latest non-prerelease GitHub Release, showing its
version, date and size. Official APK URLs are accepted only from the
repository's GitHub Releases prefix. If the API is unavailable,
download links continue pointing to the official latest-release page.

The logo is copied from `native-vpn/assets/nora_icon.jpg` at deploy time.

## Local checks

    python3 -m unittest discover -s tests -p 'test_site.py' -v

## Scope

Website content is informational. It does not host VPN traffic,
subscriptions, signing keys or APK mirrors. See [`LICENSE`](../LICENSE)
for open-source license and upstream attribution.
