# NoraProxy release signing setup

NoraProxy Android release APKs use one permanent PKCS#12 keystore.
**Never upload, commit, paste or share the private keystore or its passwords.**

The release workflow in `.github/workflows/native-vpn.yml` requires:

- `NORA_KEYSTORE_BASE64`
- `NORA_SIGNING_STORE_PASSWORD`
- `NORA_SIGNING_KEY_ALIAS`
- `NORA_SIGNING_KEY_PASSWORD`

If you have the original private `NoraProxy-signing-backup.zip`:

1. Install [GitHub CLI](https://cli.github.com/) and Python 3 locally.
2. Authenticate as the maintainer: `gh auth login` (ensure GitHub Actions Secrets write permission).
3. From the repository checkout run:
   `python3 scripts/register_signing_secrets.py /path/to/NoraProxy-signing-backup.zip`
4. Verify the four names, without displaying their values:
   `gh secret list --repo hazhanhasani/noraproxy`
5. Keep the private backup offline (preferably in two separately protected locations).

The helper sends secret values through stdin and validates the published SHA-256 certificate fingerprint: `248e68eda1329080c3d05252392c585996b014280c4a380411b0cb3e88ee9a8a`.
Only the certificate fingerprint is public; never place keystore bytes in repository files.

After the secrets exist and the native debug build and real-device VPN tests succeed,
run **Build NoraProxy Native VPN** with `workflow_dispatch` from the main branch.
The release workflow refuses to sign unless all four values are supplied and
the signed APK matches the pinned certificate. A release with a different keystore
cannot update an already installed version. Debug-signed builds cannot update
release-signed installations.

**Safety:** Publishing a release and enabling OTA updates are separate from merely
registering secrets. Confirm production readiness before dispatching release.
