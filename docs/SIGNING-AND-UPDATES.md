# NoraProxy: permanent signing and in-app updates

Production identity certificate SHA-256:

24:8E:68:ED:A1:32:90:80:C3:D0:52:52:39:2C:58:59:96:B0:14:28:0C:4A:38:04:11:B0:CB:3E:88:EE:9A:8A

This identity was created offline and is not committed in the public repo.
Keep at least two private backups and store its password separately.

## GitHub Actions Secrets (one time)

Go to Settings > Secrets and variables > Actions, and create:

| Name | Value |
|------|-------|
| NORA_KEYSTORE_BASE64 | Base64-encoded bytes of noraproxy-release.jks |
| NORA_SIGNING_STORE_PASSWORD | Store password from the private recovery document |
| NORA_SIGNING_KEY_ALIAS | Alias from the private recovery document |
| NORA_SIGNING_KEY_PASSWORD | Key password from the private recovery document |

Encode locally with base64 -w0 noraproxy-release.jks on Linux.
Never commit or paste any private signing material into README, Issues,
Pull Requests or application source.

## Producing signed APKs

- PR/push builds produce an ephemeral **debug test** APK only.
- Once Secrets have been configured, Actions > Build NoraProxy Native VPN >
  Run workflow creates a permanently signed APK artifact.
- Missing Secrets deliberately fail the release build; the workflow does not
  silently fall back to the debug key.
- Validate with apksigner and test real traffic before distribution.
- All future upgrades must use the same applicationId, certificate and a
  strictly increasing versionCode.

Users with debug-signed test builds may need one uninstall/reinstall when
moving to the first production signed release: different Android signatures
cannot update each other in place.

## Publishing and fetching new versions

Publish a non-prerelease GitHub Release tagged v0.2.0 or higher in
hazhanhasani/noraproxy. Attach the correctly signed APK named
NoraProxy-0.2.0.apk; GitHub Releases provides a sha256 digest for uploaded assets.

The native app reads the latest Release from GitHub's HTTPS API, downloads
the release APK directly to its app-private storage (not browser/Google Play),
shows percentage, verifies asset SHA256, Android package name, increasing
versionCode and same signing certificate, and invokes the normal Android
installer using FileProvider.

Android always asks for user approval for installing updates in a normal
consumer application. Silent installations are not advertised or attempted.
An unavailable digest or mismatched signature blocks installation.

There is no production APK before native CI and real Android testing succeed.
The self-updater only discovers a new version after a properly signed public
Release is published and does not invent availability.

## Automated publication (approved main branch only)

Once the native VPN Pull Request has passed testing and is merged into main,
manually dispatch Build NoraProxy Native VPN on the **main** branch.
With all four signing secrets provided, the workflow validates the APK and
certificate fingerprint, creates GitHub Release v0.2.0 and attaches the
production APK and checksum file. The app's update checker reads the latest
public GitHub Release automatically. Existing tags are never overwritten.

Do NOT publish from a feature branch as a production release; native builds
there are for testing and review. Manual dispatch on a feature branch produces
a signed artifact but deliberately does not publish it to consumers.
